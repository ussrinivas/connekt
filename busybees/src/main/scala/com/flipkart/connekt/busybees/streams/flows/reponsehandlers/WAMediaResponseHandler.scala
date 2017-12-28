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
import com.flipkart.connekt.busybees.models.{RequestTracker, WAMediaRequestTracker}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.WAResponseStatus
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, WACallbackEvent, WAErrorResponse, WASuccessResponse}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WAMediaResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends WAMediaProviderResponseHandler[(Try[HttpResponse], RequestTracker)](90) with Instrumented {
  override val map: ((Try[HttpResponse], RequestTracker)) => Future[List[ConnektRequest]] = responseTrackerPair => Future(profile("map") {

    val httpResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2.asInstanceOf[WAMediaRequestTracker]
    val response = ListBuffer[ConnektRequest]()

    val messageId = requestTracker.request.id
    val appName = requestTracker.request.appName
    val clientId = requestTracker.request.clientId
    val contextId = requestTracker.request.contextId
    val destination = requestTracker.request.destinations.head
    val stencilId = requestTracker.request.meta.get("stencilId").map(_.toString)

    val events = ListBuffer[WACallbackEvent]()
    val eventTS = System.currentTimeMillis()
    try {
      httpResponse match {
        case Success(r) =>
          val stringResponse = r.entity.getString
          val isSuccess = Try(stringResponse.getObj[WASuccessResponse]).isSuccess
          ConnektLogger(LogFile.PROCESSORS).info(s"WAMediaResponseHandler received http response for: $messageId")
          ConnektLogger(LogFile.PROCESSORS).trace(s"WAMediaResponseHandler received http response for: $messageId http response body: $stringResponse")
          r.status.intValue() match {
            case 200 if isSuccess =>
              response += requestTracker.request
              ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploaded)
              events += WACallbackEvent(messageId, None, destination, WAResponseStatus.MediaUploaded, clientId, appName, contextId.getOrElse(""), stringResponse, eventTS)
              counter(s"whatsapp.media.upload.${WAResponseStatus.MediaUploaded}")
            case 200 if !isSuccess =>
              val responseBody = stringResponse.getObj[WAErrorResponse]
              ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploadError)
              events += WACallbackEvent(messageId, None, destination, responseBody.error.errortext, clientId, appName, contextId.getOrElse(""), stringResponse, eventTS)
              ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received error response for: $messageId, error: $stringResponse")
              counter(s"whatsapp.media.upload.${WAResponseStatus.MediaUploadError}")
            case _ =>
              ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploadError)
              events += WACallbackEvent(messageId, None, destination, WAResponseStatus.MediaUploadError, clientId, appName, contextId.getOrElse(""), stringResponse, eventTS)
              ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received http non 200 failure for: $messageId with error: $stringResponse")
              counter(s"whatsapp.media.upload.${WAResponseStatus.MediaUploadError}")
          }
        case Failure(e2) =>
          ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploadError)
          events += WACallbackEvent(messageId, None, destination, WAResponseStatus.MediaUploadError, clientId, appName, contextId.getOrElse(""), e2.getMessage, eventTS)
          ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received http failure for: $messageId", e2)
          counter(s"whatsapp.media.upload.${WAResponseStatus.MediaUploadError}")
      }
    } catch {
      case e: Exception =>
        ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploadError)
        events += WACallbackEvent(messageId, None, destination, WAResponseStatus.MediaUploadError, clientId, appName, contextId.getOrElse(""), e.getMessage, eventTS)
        ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler Internal Error when handling http response for: $messageId", e)
        counter(s"whatsapp.media.upload.${WAResponseStatus.MediaUploadSystemError}")
    }
    events.enqueue
    response.toList
  })(m.executionContext)
}


