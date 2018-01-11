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
package com.flipkart.connekt.firefly.flows.responsehandlers

import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.flipkart.connekt.busybees.models.WAContactTracker
import com.flipkart.connekt.commons.entities.WAContactEntity
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.MessageStatus.WAResponseStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.{BigfootService, WAContactService}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.models.FlowResponseStatus
import com.flipkart.connekt.firefly.models.Status

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WAContactResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends WAProviderResponseHandler[(Try[HttpResponse], WAContactTracker)](96) with Instrumented {

  private val contactService = ServiceFactory.getContactService

  override implicit val map: ((Try[HttpResponse], WAContactTracker)) => Future[List[FlowResponseStatus]] = responseTrackerPair => Future(profile("map") {

    val httpResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2

    httpResponse match {
      case Success(r) =>
        try {
          val strResponse = r.entity.getString
          val isSuccess = Try(strResponse.getObj[WASuccessResponse]).isSuccess
          r.status.intValue() match {
            case 200 if isSuccess =>
              val response = strResponse.getObj[WASuccessResponse]
              val results = response.payload.results.getOrElse(List.empty)
              ConnektLogger(LogFile.PROCESSORS).info(s"WAContactResponseHandler received http response for messageId : ${requestTracker.messageId}")
              ConnektLogger(LogFile.PROCESSORS).trace(s"WAContactResponseHandler received http response for: $results")
              results.map(result => {
                val waContactEntity = WAContactEntity(result.input_number, result.wa_username, requestTracker.appName, result.wa_exists, None)
                WAContactService().add(waContactEntity)
                BigfootService.ingestEntity(result.wa_username, waContactEntity.toPublishFormat, waContactEntity.namespace).get
              })
              ConnektLogger(LogFile.PROCESSORS).info(s"WAContactResponseHandler contacts updated in hbase for messageId : ${requestTracker.messageId}")
              ConnektLogger(LogFile.PROCESSORS).trace(s"WAContactResponseHandler contacts updated in hbase : $results")
              meter(s"check.contact.${WAResponseStatus.ContactReceived}").mark()
              List(FlowResponseStatus(Status.Success))
            case w =>
              val response = strResponse.getObj[WAErrorResponse]
              ConnektLogger(LogFile.PROCESSORS).error(s"WAContactResponseHandler received http response : ${response.getJson} , with status code $w.")
              meter(s"check.contact.failed.${WAResponseStatus.ContactError}").mark()
              requestTracker.contactPayloads.contacts.foreach(contactService.enqueueContactEvents)
              List(FlowResponseStatus(Status.Failed))
          }
        } catch {
          case e: Exception =>
            ConnektLogger(LogFile.PROCESSORS).error(s"WAContactResponseHandler failed processing http response body for: $r due to internal error ", e)
            meter(s"check.contact.failed.${WAResponseStatus.ContactSystemError}").mark()
            requestTracker.contactPayloads.contacts.foreach(contactService.enqueueContactEvents)
            List(FlowResponseStatus(Status.Failed))
        }
      case Failure(e2) =>
        ConnektLogger(LogFile.PROCESSORS).debug(s"WAContactResponseHandler send failure for: $requestTracker", e2)
        ConnektLogger(LogFile.PROCESSORS).error(s"WAContactResponseHandler send failure for messageId : ${requestTracker.messageId} with error : ", e2)
        meter(s"check.contact.failed.${WAResponseStatus.ContactError}").mark()
        requestTracker.contactPayloads.contacts.foreach(contactService.enqueueContactEvents)
        List(FlowResponseStatus(Status.Failed))
    }
  })(m.executionContext)
}
