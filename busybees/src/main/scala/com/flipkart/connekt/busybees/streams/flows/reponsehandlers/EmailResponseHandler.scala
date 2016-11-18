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
import com.flipkart.connekt.busybees.models.{EmailRequestTracker, EmailResponse}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels.EmailCallbackEvent
import com.flipkart.connekt.commons.iomodels.MessageStatus.{EmailResponseStatus, InternalStatus}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class EmailResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends EmailProviderResponseHandler[(Try[EmailResponse], EmailRequestTracker)](96) with Instrumented {
  override val map: ((Try[EmailResponse], EmailRequestTracker)) => Future[List[EmailCallbackEvent]] = responseTrackerPair => Future({

    val tryResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2

    val events = ListBuffer[EmailCallbackEvent]()

    tryResponse match {
      case Success(emailResponse) =>
        ConnektLogger(LogFile.PROCESSORS).info(s"EmailResponseHandler received http response for: ${requestTracker.messageId} with provider messageId : ${emailResponse.messageId} / ${emailResponse.responseCode}")
        emailResponse.responseCode match {
          case s if 2 == (s / 100) =>
            //Good!
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.EMAIL), appName = requestTracker.appName, event = EmailResponseStatus.Received)
            events ++= (requestTracker.to ++ requestTracker.cc).map(t => EmailCallbackEvent(requestTracker.messageId, requestTracker.clientId, t, EmailResponseStatus.Received, requestTracker.appName, requestTracker.contextId, emailResponse.messageId))

          case f if 4 == (f / 100) =>
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.EMAIL), appName = requestTracker.appName, event = EmailResponseStatus.AuthError)
            events ++= (requestTracker.to ++ requestTracker.cc).map(t => EmailCallbackEvent(requestTracker.messageId, requestTracker.clientId, t, EmailResponseStatus.AuthError, requestTracker.appName, requestTracker.contextId, s"ProviderReponse : MessageId : ${emailResponse.messageId}, Message: ${emailResponse.message}"))
            ConnektLogger(LogFile.PROCESSORS).error(s"EmailResponseHandler http response - auth error for: ${requestTracker.messageId} code: ${emailResponse.responseCode} response: ${emailResponse.message}")

          case e if 5 == (e / 100) =>
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.EMAIL), appName = requestTracker.appName, event = EmailResponseStatus.InternalError)
            events ++= (requestTracker.to ++ requestTracker.cc).map(t => EmailCallbackEvent(requestTracker.messageId, requestTracker.clientId, t, EmailResponseStatus.InternalError, requestTracker.appName, requestTracker.contextId, emailResponse.message))
            ConnektLogger(LogFile.PROCESSORS).error(s"EmailResponseHandler http response - the server encountered an error while trying to process the request for: ${requestTracker.messageId} code: ${emailResponse.responseCode} response: ${emailResponse.message}")
          case w =>
            ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.EMAIL), appName = requestTracker.appName, event = EmailResponseStatus.Error)
            events ++= (requestTracker.to ++ requestTracker.cc).map(t => EmailCallbackEvent(requestTracker.messageId, requestTracker.clientId, t, EmailResponseStatus.Error, requestTracker.appName, requestTracker.contextId, emailResponse.message))
            ConnektLogger(LogFile.PROCESSORS).error(s"OpenWebResponseHandler http response - response unhandled for: ${requestTracker.messageId} code: ${emailResponse.responseCode} response: ${emailResponse.message}")
        }

      case Failure(e) =>
        ConnektLogger(LogFile.PROCESSORS).error(s"EmailResponseHandler failed to send email for: ${requestTracker.messageId} due to: ${e.getClass.getSimpleName}, ${e.getMessage}", e)
        ServiceFactory.getReportingService.recordPushStatsDelta(clientId = requestTracker.clientId, contextId = Option(requestTracker.contextId), stencilId = requestTracker.meta.get("stencilId").map(_.toString), platform = Option(Channel.EMAIL), appName = requestTracker.appName, event = InternalStatus.ProviderSendError)
        events ++= (requestTracker.to ++ requestTracker.cc).map(t => EmailCallbackEvent(requestTracker.messageId, requestTracker.clientId, t, InternalStatus.ProviderSendError, requestTracker.appName, requestTracker.contextId, s"EmailResponseHandler-${e.getClass.getSimpleName}-${e.getMessage}"))
    }
    events.persist

    events.toList
  })(m.executionContext)
}
