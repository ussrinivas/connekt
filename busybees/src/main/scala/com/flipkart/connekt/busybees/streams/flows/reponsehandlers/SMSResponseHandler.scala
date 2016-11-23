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

class SmsResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends SmsProviderResponseHandler[(Try[SmsResponse], SmsRequestTracker), SmsRequestTracker](96) with Instrumented {
  override val map: ((Try[SmsResponse], SmsRequestTracker)) => Future[List[Either[SmsRequestTracker, SmsCallbackEvent]]] = responseTrackerPair => Future({

    handleSmsResponse(responseTrackerPair._1, responseTrackerPair._2) match {
      case Some(events) =>
        events.persist
        events.map(Right(_))
      case None =>
        List(Left(responseTrackerPair._2))
    }
  })(m.executionContext)

  private def handleSmsResponse(tryResponse: Try[SmsResponse], requestTracker: SmsRequestTracker): Option[List[SmsCallbackEvent]] = {

    val receivers = requestTracker.receivers

    val maybeSmsCallbackEvent: Option[List[SmsCallbackEvent]] = tryResponse match {
      case Success(smsResponse) =>
        ConnektLogger(LogFile.PROCESSORS).info(s"SmsResponseHandler received http response for: messageId : ${requestTracker.messageId} code: ${smsResponse.responseCode}")
        smsResponse.responseCode match {
          case s if 2 == (s / 100) =>
            val rP = smsResponse.responsePerReceivers.map(r => {
              ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = r.receiverStatus)
              SmsCallbackEvent(requestTracker.messageId, r.providerMessageId, r.receiverStatus, r.receiver,
                requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId, r.errorMessage)
            })
            Some(rP)
          case f if 4 == (f / 100) =>
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.AuthError)
            ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler http response - auth error for: ${requestTracker.messageId} code: ${smsResponse.responseCode} response: ${smsResponse.message}")
            Some(smsResponse.responsePerReceivers.map(r => {
              SmsCallbackEvent(requestTracker.messageId, r.providerMessageId, SmsResponseStatus.AuthError, r.receiver,
                requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId, s"ProviderReponse : MessageId : ${requestTracker.messageId}, Message: ${smsResponse.message}")
            }))
          case e if 5 == (e / 100) =>
            // Retrying in this case
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.InternalError)
            ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler http response - the server encountered an error while trying to process the request for: ${requestTracker.messageId} code: ${smsResponse.responseCode} response: ${smsResponse.message}")
            smsResponse.responsePerReceivers.map(r => {
              SmsCallbackEvent(requestTracker.messageId, r.providerMessageId, SmsResponseStatus.InternalError, r.receiver,
                requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId, s"ProviderReponse : MessageId : ${requestTracker.messageId}, Message: ${smsResponse.message}")
            }).persist
            None
          case w =>
            // Retrying in this case
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.Error)
            ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler http response - response unhandled for: ${requestTracker.messageId} code: ${smsResponse.responseCode} response: ${smsResponse.message}")
            smsResponse.responsePerReceivers.map(r => {
              SmsCallbackEvent(requestTracker.messageId, r.providerMessageId, SmsResponseStatus.Error, r.receiver,
                requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId, s"ProviderReponse : MessageId : ${requestTracker.messageId}, Message: ${smsResponse.message}")
            }).persist
            None
        }

      case Failure(e) =>
        // Retrying in this case
        ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler failed to send sms for: ${requestTracker.messageId} due to: ${e.getClass.getSimpleName}, ${e.getMessage}", e)
        ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = InternalStatus.ProviderSendError)
        receivers.map(SmsCallbackEvent(requestTracker.messageId, "", InternalStatus.ProviderSendError, _,
          requestTracker.clientId, requestTracker.provider, requestTracker.appName, Channel.SMS, requestTracker.contextId, s"SmsResponseHandler-${e.getClass.getSimpleName}-${e.getMessage}")).toList.persist
        None
    }
    maybeSmsCallbackEvent
  }
}
