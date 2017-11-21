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
import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.models.WARequestTracker
import com.flipkart.connekt.commons.entities.{Channel, WAMessageIdMappingEntity}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels.MessageStatus.WAResponseStatus
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, WACallbackEvent}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.WAMessageIdMappingService
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WAMediaResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends  WAMediaProviderResponseHandler[(Try[HttpResponse], WARequestTracker)](90) with Instrumented {
  override val map: ((Try[HttpResponse], WARequestTracker)) => Future[List[ConnektRequest]] = responseTrackerPair => Future(profile("map") {

    val httpResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2
    var mediaUploaded = false

    val messageId = requestTracker.messageId
    val appName = requestTracker.appName

    val events = ListBuffer[WACallbackEvent]()
    val eventTS = System.currentTimeMillis()

    httpResponse match {
      case Success(r) =>
        try {
          val stringResponse = r.entity.getString(m)
          ConnektLogger(LogFile.PROCESSORS).debug(s"WAMediaResponseHandler received http response for: $messageId")
          ConnektLogger(LogFile.PROCESSORS).trace(s"WAMediaResponseHandler received http response for: $messageId http response body: $stringResponse")
          r.status.intValue() match {
            case 200 =>
              val responseBody = stringResponse.getObj[ObjectNode]
              responseBody match {
                case _ if responseBody.findValue("payload").asText() == "null" =>
                  val error = responseBody.get("error")
                  val errorCode = error.findValue("errorcode").asInt()
                  val errorText = error.findValue("errortext").asText()
                  ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.MediaUploadError)
                  events += WACallbackEvent(messageId, None, requestTracker.destination, errorText, requestTracker.clientId, appName, requestTracker.contextId, error.asText(), eventTS)
                case _ if responseBody.findValue("error").asBoolean() == false =>
                  val payload = responseBody.get("payload")
//                  val providerMessageId = payload.get("filename").asText()
                  mediaUploaded = true
                  ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.MediaUploaded)
                  events += WACallbackEvent(messageId, None, requestTracker.destination, WAResponseStatus.MediaUploaded, requestTracker.clientId, appName, requestTracker.contextId, payload.asText(), eventTS)
              }
            case _ =>
              ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.MediaUploadError)
              events += WACallbackEvent(messageId, None, requestTracker.destination, WAResponseStatus.MediaUploadError, requestTracker.clientId, appName, requestTracker.contextId, stringResponse, eventTS)
              ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received http failure for: $messageId with error: $stringResponse")
          }
        } catch {
          case e: Exception =>
            ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.MediaUploadError)
            events += WACallbackEvent(messageId, None, requestTracker.destination, WAResponseStatus.MediaUploadError, requestTracker.clientId, appName, requestTracker.contextId, e.getMessage, eventTS)
            ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received http failure for: $messageId", e)
        }
      case Failure(e2) =>
        ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WAResponseStatus.MediaUploadError)
        events += WACallbackEvent(messageId, None, requestTracker.destination, WAResponseStatus.MediaUploadError, requestTracker.clientId, appName, requestTracker.contextId, e2.getMessage, eventTS)
        ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received http failure for: $messageId", e2)
    }
    events.enqueue
    if(mediaUploaded) List(requestTracker.request) else List()
  })(m.executionContext)
}


