/*
 *         -╥⌐⌐⌐⌐            -⌐⌐⌐⌐-
 *      ≡╢░░░░⌐\░░░φ     ╓╝░░░░⌐░░░░╪╕
 *     ╣╬░░`    `░░░╢┘ φ▒╣╬╝╜     ░░╢╣Q
 *    ║╣╬░⌐        ` ╤▒▒▒Å`        ║╢╬╣
 *    ╚╣╬░⌐        ╔▒▒▒▒`«╕        ╢╢╣▒
 *     ╫╬░░╖    .░ ╙╨╨  ╣╣╬░φ    ╓φ░╢╢Å
 *      ╙╢░░░░⌐"░░░╜     ╙Å░░░░⌐░░░░╝`
 *        ``˚¬ ⌐              ˚˚⌐´
 *
 *      Copyright © 2016 Flipkart.com
 */
package com.flipkart.connekt.receptors.routes.helper

import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.entities.{Channel, WAContactEntity}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.MessageStatus.WAResponseStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.{BigfootService, ConnektConfig, WAContactService}
import com.flipkart.connekt.commons.utils.StringUtils.{HttpEntity2String, JSONMarshallFunctions, JSONUnMarshallFunctions}
import com.flipkart.connekt.receptors.service.WebClient

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object WAContactCheckHelper extends Instrumented {

  private val baseUrl = ConnektConfig.getString("wa.base.uri").get
  private val timeout = ConnektConfig.getInt("wa.contact.api.timeout.sec").get.seconds
  private val checkContactInterval = ConnektConfig.getInt("wa.hbase.check.contact.interval.days").get
  private val checkContactEnabled = ConnektConfig.getBoolean("wa.hbase.check.contact.enable").get

  //returns tuple of valid numbers list and invalid numbers list
  def checkContact(appName: String, destinations: Set[String])(implicit am: ActorMaterializer): (List[WAContactEntity], List[WAContactEntity]) = {
    val withinTTLWAList = WAContactService.instance.gets(appName, destinations) match {
      case Success(waList) =>
        if (checkContactEnabled)
          waList.filter(System.currentTimeMillis() - _.lastCheckContactTS < checkContactInterval.days.toMillis)
        else
          waList
      case _ => Nil
    }
    (checkContactViaWAApi(destinations.toList.diff(withinTTLWAList.map(_.destination)), appName) ++ withinTTLWAList).partition(_.exists.toLowerCase.contains("true"))
  }

  def checkContactViaWAApi(destinations: List[String], appName: String)(implicit am: ActorMaterializer): List[WAContactEntity] = {
    if (destinations.nonEmpty) {
      val requestEntity = HttpEntity(ContentTypes.`application/json`, WAContactRequest(Payload(users = destinations.toSet)).getJson)
      val httpRequest = HttpRequest(HttpMethods.POST, s"$baseUrl${Constants.WAConstants.WHATSAPP_CHECK_CONTACT_URI}", Nil, requestEntity)

      val contactWAStatus = ListBuffer.empty[WAContactEntity]
      Try(Await.result[HttpResponse](WebClient.instance.callHttpService(httpRequest,
        Channel.WA, appName), timeout)) match {
        case Success(r) =>
          val strResponse = r.entity.getString
          val isSuccess = Try(strResponse.getObj[WASuccessResponse]).isSuccess
          r.status.intValue() match {
            case 200 if isSuccess =>
              val response = strResponse.getObj[WASuccessResponse]
              val results = response.payload.results.getOrElse(List.empty)
              ConnektLogger(LogFile.PROCESSORS).debug(s"WAContactCheckHelper received http response for destination : ${destinations.mkString(",")}")
              results.map(result => {
                val waContactEntity = WAContactEntity(result.input_number, result.wa_username, appName, result.wa_exists, None)
                WAContactService().add(waContactEntity)
                BigfootService.ingestEntity(result.wa_username, waContactEntity.toPublishFormat, waContactEntity.namespace).get
                contactWAStatus += waContactEntity
              })
              ConnektLogger(LogFile.PROCESSORS).debug(s"WAContactCheckHelper contacts updated in hbase for destination : ${destinations.mkString(",")}")
              meter(s"check.contact.${WAResponseStatus.ContactReceived}").mark()
            case w =>
              val response = strResponse.getObj[WAErrorResponse]
              ConnektLogger(LogFile.PROCESSORS).error(s"WAContactCheckHelper received http response : ${response.getJson} , with status code $w.")
              meter(s"check.contact.failed.${WAResponseStatus.ContactError}").mark()
          }
        case Failure(f) =>
          ConnektLogger(LogFile.PROCESSORS).error(s"WAContactCheckHelper failed processing http response body for destination : ${destinations.mkString(",")} due to internal error ", f)
      }
      contactWAStatus.toList
    } else {
      ConnektLogger(LogFile.PROCESSORS).error(s"WAContactCheckHelper no destination to check")
      List.empty
    }
  }
}
