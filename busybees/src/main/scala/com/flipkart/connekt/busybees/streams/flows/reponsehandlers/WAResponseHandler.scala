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
package com.flipkart.connekt.busybees.streams.flows.reponsehandlers

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import com.flipkart.connekt.busybees.models.{RequestTracker, WARequestTracker}
import com.flipkart.connekt.commons.entities.{Channel, WAMessageIdMappingEntity}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels.MessageStatus.WAResponseStatus
import com.flipkart.connekt.commons.iomodels.{WACallbackEvent, WAErrorResponse, WASuccessResponse}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.WAMessageIdMappingService
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WAResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends WAProviderResponseHandler[(Try[HttpResponse], RequestTracker)](90) with Instrumented {
  override val map: ((Try[HttpResponse], RequestTracker)) => Future[List[WACallbackEvent]] = responseTrackerPair => Future(profile("map") {

    val httpResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2.asInstanceOf[WARequestTracker]

    val messageId = requestTracker.messageId
    val appName = requestTracker.appName

    val events = ListBuffer[WACallbackEvent]()
    val eventTS = System.currentTimeMillis()

    try {
      httpResponse match {
        case Success(r) =>
          val stringResponse = r.entity.getString
          val isSuccess = Try(stringResponse.getObj[WASuccessResponse]).isSuccess
          ConnektLogger(LogFile.PROCESSORS).info(s"WaResponseHandler received http response for: $messageId")
          ConnektLogger(LogFile.PROCESSORS).trace(s"WaResponseHandler received http response for: $messageId http response body: $stringResponse")
          r.status.intValue() match {
            case 200 if isSuccess =>
              val responseBody = stringResponse.getObj[WASuccessResponse]
              WAMessageIdMappingService.add(WAMessageIdMappingEntity(
                responseBody.payload.message_id.getOrElse(""),
                requestTracker.messageId,
                requestTracker.clientId,
                requestTracker.appName,
                requestTracker.contextId
              ))
              ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.Received)
              events += WACallbackEvent(messageId, Some(responseBody.payload.message_id.getOrElse("")), requestTracker.destination, WAResponseStatus.Received, requestTracker.clientId, appName, requestTracker.contextId, responseBody.getJson, eventTS)
              meter(s"send.${WAResponseStatus.Received}").mark()
              ConnektLogger(LogFile.PROCESSORS).info(s"WaResponseHandler received for: $messageId and waMessageId : ${responseBody.payload.message_id}")
            case 200 if !isSuccess =>
              val responseBody = stringResponse.getObj[WAErrorResponse]
              ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.Error)
              events += WACallbackEvent(messageId, None, requestTracker.destination, responseBody.error.errortext, requestTracker.clientId, appName, requestTracker.contextId, responseBody.error.getJson, eventTS)
              meter(s"send.failed.${WAResponseStatus.Error}").mark()
              ConnektLogger(LogFile.PROCESSORS).error(s"WaResponseHandler received Whatsapp http error for: $messageId with error: ${responseBody.error}")
            case _ =>
              ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.Error)
              events += WACallbackEvent(messageId, None, requestTracker.destination, WAResponseStatus.Error, requestTracker.clientId, appName, requestTracker.contextId, stringResponse, eventTS)
              ConnektLogger(LogFile.PROCESSORS).error(s"WaResponseHandler received Whatsapp non 200 http failure for: $messageId with error: $stringResponse")
              meter(s"send.failed.${WAResponseStatus.Error}").mark()
          }
        case Failure(e2) =>
          ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.Error)
          events += WACallbackEvent(messageId, None, requestTracker.destination, WAResponseStatus.Error, requestTracker.clientId, appName, requestTracker.contextId, e2.getMessage, eventTS)
          ConnektLogger(LogFile.PROCESSORS).error(s"WaResponseHandler received http failure for: $messageId", e2)
          meter(s"send.failed.${WAResponseStatus.Error}").mark()
      }
    } catch {
      case e: Exception =>
        ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.Error)
        events += WACallbackEvent(messageId, None, requestTracker.destination, WAResponseStatus.Error, requestTracker.clientId, appName, requestTracker.contextId, e.getMessage, eventTS)
        ConnektLogger(LogFile.PROCESSORS).error(s"WaResponseHandler: failed due to an exception: $messageId", e)
        meter(s"send.failed.${WAResponseStatus.SendSystemError}").mark()
    }
    events.enqueue
    events.toList
  })(m.executionContext)
}
