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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Either.MergeableEither
import scala.util.{Failure, Success, Try}

class SmsResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends SmsProviderResponseHandler[(Try[SmsResponse], SmsRequestTracker), SmsRequestTracker](96) with Instrumented {
  override val map: ((Try[SmsResponse], SmsRequestTracker)) => Future[List[Either[SmsRequestTracker, SmsCallbackEvent]]] = responseTrackerPair => Future({

    handleSmsResponse(responseTrackerPair._1, responseTrackerPair._2) match {
      case Right(events) =>
        events.map(Right(_))
      case Left(_) =>
        List(Left(responseTrackerPair._2))
    }
  })(m.executionContext)

  private def handleSmsResponse(tryResponse: Try[SmsResponse], requestTracker: SmsRequestTracker): Either[List[SmsCallbackEvent], List[SmsCallbackEvent]] = {

    val receivers = requestTracker.receivers

    val maybeSmsCallbackEvent = tryResponse match {
      case Success(smsResponse) =>
        ConnektLogger(LogFile.PROCESSORS).info(s"SmsResponseHandler received http response for: messageId : ${requestTracker.messageId} code: ${smsResponse.responseCode}")
        smsResponse.responseCode match {
          case s if 2 == (s / 100) =>
            Right(smsResponse.responsePerReceivers.map(r => {
              ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = r.receiverStatus)
              SmsCallbackEvent(requestTracker.messageId, r.providerMessageId, requestTracker.request.smsPart, requestTracker.request.encoding, requestTracker.request.smsLength, r.receiverStatus, r.receiver,
                requestTracker.clientId, requestTracker.provider, requestTracker.appName, requestTracker.contextId, r.cargo)
            }))
          case f if 4 == (f / 100) =>
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.AuthError)
            ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler http response - auth error for: ${requestTracker.messageId} code: ${smsResponse.responseCode} response: ${smsResponse.message}")
            Right(smsResponse.responsePerReceivers.map(r => {
              SmsCallbackEvent(requestTracker.messageId, r.providerMessageId, requestTracker.request.smsPart, requestTracker.request.encoding, requestTracker.request.smsLength, SmsResponseStatus.AuthError, r.receiver,
                requestTracker.clientId, requestTracker.provider, requestTracker.appName, requestTracker.contextId, s"ProviderReponse : MessageId : ${requestTracker.messageId}, Message: ${smsResponse.message}")
            }))
          case e if 5 == (e / 100) =>
            // Retrying in this case
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.InternalError)
            ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler http response - the server encountered an error while trying to process the request for: ${requestTracker.messageId} code: ${smsResponse.responseCode} response: ${smsResponse.message}")
            Left(smsResponse.responsePerReceivers.map(r => {
              SmsCallbackEvent(requestTracker.messageId, r.providerMessageId, requestTracker.request.smsPart, requestTracker.request.encoding, requestTracker.request.smsLength, SmsResponseStatus.InternalError, r.receiver,
                requestTracker.clientId, requestTracker.provider, requestTracker.appName, requestTracker.contextId, s"ProviderReponse : MessageId : ${requestTracker.messageId}, Message: ${smsResponse.message}")
            }))
          case w =>
            // Retrying in this case
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = SmsResponseStatus.Error)
            ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler http response - response unhandled for: ${requestTracker.messageId} code: ${smsResponse.responseCode} response: ${smsResponse.message}")
            Left(smsResponse.responsePerReceivers.map(r => {
              SmsCallbackEvent(requestTracker.messageId, r.providerMessageId, requestTracker.request.smsPart, requestTracker.request.encoding, requestTracker.request.smsLength, SmsResponseStatus.Error, r.receiver,
                requestTracker.clientId, requestTracker.provider, requestTracker.appName, requestTracker.contextId, s"ProviderReponse : MessageId : ${requestTracker.messageId}, Message: ${smsResponse.message}")
            }))
        }

      case Failure(e) =>
        // Retrying in this case
        ConnektLogger(LogFile.PROCESSORS).error(s"SmsResponseHandler failed to send sms for: ${requestTracker.messageId} due to: ${e.getClass.getSimpleName}, ${e.getMessage}", e)
        ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.SMS), appName = requestTracker.appName, event = InternalStatus.ProviderSendError)
        Left(receivers.map(SmsCallbackEvent(requestTracker.messageId, "", requestTracker.request.smsPart, requestTracker.request.encoding, requestTracker.request.smsLength, InternalStatus.ProviderSendError, _,
          requestTracker.clientId, requestTracker.provider, requestTracker.appName, requestTracker.contextId, s"SmsResponseHandler-${e.getClass.getSimpleName}-${e.getMessage}")).toList)
    }
    maybeSmsCallbackEvent.merge.persist
    maybeSmsCallbackEvent
  }
}
