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
import com.flipkart.connekt.busybees.models.WaRequestTracker
import com.flipkart.connekt.commons.entities.{Channel, WAMessageIdMappingEntity}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels.MessageStatus.WaResponseStatus
import com.flipkart.connekt.commons.iomodels.WACallbackEvent
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.WAMessageIdMappingService
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

sealed case class SMSCargoContainer(providerMessageId: String, provider: String, cargo: String, meta:Map[String,Any])

class WaResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends  WaProviderResponseHandler[(Try[HttpResponse], WaRequestTracker)](90) with Instrumented {
  override val map: ((Try[HttpResponse], WaRequestTracker)) => Future[List[WACallbackEvent]] = responseTrackerPair => Future(profile("map") {

    val httpResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2

    val messageId = requestTracker.messageId
    val appName = requestTracker.appName
    val contactNo = requestTracker.destination

    val events = ListBuffer[WACallbackEvent]()
    val eventTS = System.currentTimeMillis()

    httpResponse match {
      case Success(r) =>
        try {
          val stringResponse = r.entity.getString(m)
          ConnektLogger(LogFile.PROCESSORS).debug(s"WaResponseHandler received http response for: $messageId")
          ConnektLogger(LogFile.PROCESSORS).trace(s"WaResponseHandler received http response for: $messageId http response body: $stringResponse")
          r.status.intValue() match {
            case 200 =>
              val responseBody = stringResponse.getObj[ObjectNode]
              responseBody match {
                case _ if responseBody.findValue("payload") == null =>
                  val error = responseBody.get("error")
                  val errorCode = error.findValue("errorcode").asInt()
                  val errorText = error.findValue("errortext").asText()
                  ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WaResponseStatus.Error)
                  events += WACallbackEvent(messageId, errorText, requestTracker.destination, requestTracker.clientId, appName, requestTracker.contextId, error.asText(), eventTS)
                case _ if responseBody.findValue("error").asBoolean() == false =>
                  val payload = responseBody.get("payload")
                  val waMessageId = payload.get("message_id").asText()
                  WAMessageIdMappingService.add(WAMessageIdMappingEntity(waMessageId, requestTracker.messageId))
                  ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WaResponseStatus.Received_HTTP)
                  events += WACallbackEvent(messageId, WaResponseStatus.Received_HTTP, requestTracker.destination, requestTracker.clientId, appName, requestTracker.contextId, payload.asText(), eventTS)
              }
            case _ =>
              ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WaResponseStatus.Error)
              events += WACallbackEvent(messageId, WaResponseStatus.Error, requestTracker.destination, requestTracker.clientId, appName, requestTracker.contextId, stringResponse, eventTS)
              ConnektLogger(LogFile.PROCESSORS).error(s"WaResponseHandler received http failure for: $messageId with error: $stringResponse")
          }
        } catch {
          case e: Exception =>
            ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WaResponseStatus.Error)
            events += WACallbackEvent(messageId, WaResponseStatus.Error, requestTracker.destination, requestTracker.clientId, appName, requestTracker.contextId, e.getMessage, eventTS)
            ConnektLogger(LogFile.PROCESSORS).error(s"WaResponseHandler received http failure for: $messageId", e)
        }
      case Failure(e2) =>
        ServiceFactory.getReportingService.recordChannelStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Channel.WA, requestTracker.appName, WaResponseStatus.Error)
        events += WACallbackEvent(messageId, WaResponseStatus.Error, requestTracker.destination, requestTracker.clientId, appName, requestTracker.contextId, e2.getMessage, eventTS)
        ConnektLogger(LogFile.PROCESSORS).error(s"WaResponseHandler received http failure for: $messageId", e2)
    }
    events.enqueue
    events.toList
  })(m.executionContext)
}
