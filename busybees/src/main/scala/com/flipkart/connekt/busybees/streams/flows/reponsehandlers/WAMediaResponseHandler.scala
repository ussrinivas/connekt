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
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.models.{RequestTracker, WAMediaRequestTracker}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels.MessageStatus.WAResponseStatus
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, WACallbackEvent}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WAMediaResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends  WAMediaProviderResponseHandler[(Try[HttpResponse], RequestTracker)](90) with Instrumented {
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
                  val errorText = error.findValue("errortext").asText()
                  ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploadError)
                  events += WACallbackEvent(messageId, None, destination, errorText, clientId, appName, contextId.getOrElse(""), stringResponse, eventTS)
                  ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received error response for: $messageId, error: ${stringResponse}")
                  counter(s"whatsapp.mediaupload.${WAResponseStatus.MediaUploadError}")
                case _ if responseBody.findValue("error").asText() == "false" =>
                  response += requestTracker.request
                  ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploaded)
                  events += WACallbackEvent(messageId, None, destination, WAResponseStatus.MediaUploaded, clientId, appName, contextId.getOrElse(""), stringResponse, eventTS)
                  counter(s"whatsapp.mediaupload.${WAResponseStatus.MediaUploaded}")
              }
            case _ =>
              ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploadError)
              events += WACallbackEvent(messageId, None, destination, WAResponseStatus.MediaUploadError, clientId, appName, contextId.getOrElse(""), stringResponse, eventTS)
              ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received http failure for: $messageId with error: $stringResponse")
              counter(s"whatsapp.mediaupload.${WAResponseStatus.MediaUploadError}")
          }
        } catch {
          case e: Exception =>
            ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploadError)
            events += WACallbackEvent(messageId, None, destination, WAResponseStatus.MediaUploadError, clientId, appName, contextId.getOrElse(""), e.getMessage, eventTS)
            ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received http failure for: $messageId", e)
            counter(s"whatsapp.mediaupload.${WAResponseStatus.MediaUploadSystemError}")
        }
      case Failure(e2) =>
        ServiceFactory.getReportingService.recordChannelStatsDelta(clientId, contextId, stencilId, Channel.WA, appName, WAResponseStatus.MediaUploadError)
        events += WACallbackEvent(messageId, None, destination, WAResponseStatus.MediaUploadError, clientId, appName, contextId.getOrElse(""), e2.getMessage, eventTS)
        ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler received http failure for: $messageId", e2)
        counter(s"whatsapp.mediaupload.${WAResponseStatus.MediaUploadError}")
    }
    events.enqueue
    response.toList
  })(m.executionContext)
}


