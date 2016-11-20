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

import akka.stream.Materializer
import com.flipkart.connekt.busybees.models.{SmsRequestTracker, SmsResponse}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels.MessageStatus.{InternalStatus, SmsResponseStatus}
import com.flipkart.connekt.commons.iomodels.SmsCallbackEvent
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class SmsResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends SmsProviderResponseHandler[(Try[SmsResponse], SmsRequestTracker)](96) with Instrumented {
  override val map: ((Try[SmsResponse], SmsRequestTracker)) => Future[List[SmsCallbackEvent]] = responseTrackerPair => Future({

    val tryResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2
    val receivers = requestTracker.receivers

    val events = ListBuffer[SmsCallbackEvent]()

    tryResponse match {
      case Success(smsResponse) =>
        ConnektLogger(LogFile.PROCESSORS).info(s"SmsResponseHandler received http response for: ${requestTracker.messageId} with provider messageId : ${smsResponse.providerMessageId} / ${smsResponse.responseCode}")
        smsResponse.responseCode match {
          case s if 2 == (s / 100) =>
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.Received)
            events ++= receivers.map(SmsCallbackEvent(requestTracker.messageId, SmsResponseStatus.Received, _,
              requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId))
          case f if 4 == (f / 100) =>
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.AuthError)
            events ++= receivers.map(SmsCallbackEvent(requestTracker.messageId, SmsResponseStatus.AuthError, _,
              requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId, s"ProviderReponse : MessageId : ${smsResponse.providerMessageId}, Message: ${smsResponse.message}"))
            ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler http response - auth error for: ${requestTracker.messageId} code: ${smsResponse.responseCode} response: ${smsResponse.message}")
          case e if 5 == (e / 100) =>
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.InternalError)
            events ++= receivers.map(SmsCallbackEvent(requestTracker.messageId, SmsResponseStatus.InternalError, _,
              requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId, s"ProviderReponse : MessageId : ${smsResponse.providerMessageId}, Message: ${smsResponse.message}"))
            ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler http response - the server encountered an error while trying to process the request for: ${requestTracker.messageId} code: ${smsResponse.responseCode} response: ${smsResponse.message}")
          case w =>
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.Error)
            events ++= receivers.map(SmsCallbackEvent(requestTracker.messageId, SmsResponseStatus.Error, _,
              requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId, s"ProviderReponse : MessageId : ${smsResponse.providerMessageId}, Message: ${smsResponse.message}"))
            ConnektLogger(LogFile.PROCESSORS).error(s"OpenWebResponseHandler http response - response unhandled for: ${requestTracker.messageId} code: ${smsResponse.responseCode} response: ${smsResponse.message}")
        }

      case Failure(e) =>
        ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler failed to send sms for: ${requestTracker.messageId} due to: ${e.getClass.getSimpleName}, ${e.getMessage}", e)
        ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = InternalStatus.ProviderSendError)
        events ++= receivers.map(SmsCallbackEvent(requestTracker.messageId, InternalStatus.ProviderSendError, _,
          requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId, s"SmsResponseHandler-${e.getClass.getSimpleName}-${e.getMessage}"))
    }
    events.persist
    events.toList
  })(m.executionContext)
}
