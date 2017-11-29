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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WAContactResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends WAProviderResponseHandler[(Try[HttpResponse], WAContactTracker)](1) with Instrumented {

  private val contactService = ServiceFactory.getContactService

  override implicit val map: ((Try[HttpResponse], WAContactTracker)) => Future[List[Nothing]] = responseTrackerPair => Future(profile("map") {

    val httpResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2

    // TODO : check where re-queue is required.
    httpResponse match {
      case Success(r) =>
        try {
          val response = r.entity.getString.getObj[WAResponse]
          val results = response.payload.results
          ConnektLogger(LogFile.PROCESSORS).debug(s"WAResponseHandler received http response for: $results")
          r.status.intValue() match {
            case 200 if response.error.equalsIgnoreCase("false") =>
              results.map(result => {
                val waContactEntity = WAContactEntity(result.input_number, result.wa_username, requestTracker.appName, result.wa_exists, None)
                WAContactService().add(waContactEntity)
                BigfootService.ingestEntity(result.wa_username, waContactEntity.toPublishFormat, waContactEntity.namespace).get
              })
              ConnektLogger(LogFile.PROCESSORS).trace(s"WAResponseHandler contacts updated in hbase : $results")
              meter(s"check.contact.${WAResponseStatus.ContactHTTP}").mark()
            case w =>
              ConnektLogger(LogFile.PROCESSORS).error(s"WAResponseHandler received http response : ${response.getJson} , with status code $w and tracker ${responseTrackerPair.getJson}")
              meter(s"check.contact.failed.${WAResponseStatus.ContactError}").mark()
              requestTracker.contactPayload.foreach(contactService.enqueueContactEvents)
          }
        } catch {
          case e: Exception =>
            ConnektLogger(LogFile.PROCESSORS).error(s"WAResponseHandler failed processing http response body for: $r", e)
            meter(s"check.contact.failed.${WAResponseStatus.ContactSystemError}").mark()
        }
      case Failure(e2) =>
        ConnektLogger(LogFile.PROCESSORS).error(s"WAResponseHandler send failure for: $requestTracker", e2)
        meter(s"check.contact.failed.${WAResponseStatus.ContactHTTP}").mark()
    }
    List.empty[Nothing]
  })(m.executionContext)
}
