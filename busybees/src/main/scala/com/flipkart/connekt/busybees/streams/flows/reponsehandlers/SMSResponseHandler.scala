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
import akka.stream._
import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.models.SmsRequestTracker
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels.MessageStatus.GCMResponseStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class SMSResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends SMSProviderResponseHandler[(Try[HttpResponse], SmsRequestTracker)](96) with Instrumented {

  override val map: ((Try[HttpResponse], SmsRequestTracker)) => Future[List[SMSCallbackEvent]] = responseTrackerPair => Future(profile("map") {

    val httpResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2

    val messageId = requestTracker.messageId
    val appName = requestTracker.appName
    val receiver = requestTracker.receivers

    val events = ListBuffer[SMSCallbackEvent]()
    val eventTS = System.currentTimeMillis()

    httpResponse match {
      case Success(r) =>
        try {
          val stringResponse = r.entity.getString(m)
          ConnektLogger(LogFile.PROCESSORS).info(s"SMSResponseHandler received http response for: $messageId")
          ConnektLogger(LogFile.PROCESSORS).trace(s"SMSResponseHandler received http response for: $messageId http response body: $stringResponse")
          r.status.intValue() match {
            case 200 =>
              val responseBody = stringResponse.getObj[ObjectNode]

              ServiceFactory.getReportingService.recordPushStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Option(Channel.SMS.toString), requestTracker.appName, GCMResponseStatus.InvalidDevice)
//              events += SMSCallbackEvent(messageId, "", "DELIEVERED", requestTracker.receiver, "GUPSHUP")
            case w =>
              ServiceFactory.getReportingService.recordPushStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Option(Channel.SMS.toString), requestTracker.appName, GCMResponseStatus.InvalidDevice)
//              events += SMSCallbackEvent(messageId, "", "FAILED", requestTracker.receiver, "GUPSHUP")
              ConnektLogger(LogFile.PROCESSORS).error(s"SMSResponseHandler http response - gcm response unhandled for: $messageId code: $w response: $stringResponse")
          }
        } catch {
          case e: Exception =>
            ServiceFactory.getReportingService.recordPushStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Option(Channel.SMS.toString), requestTracker.appName, GCMResponseStatus.InvalidDevice)
//            events += SMSCallbackEvent(messageId, "", "FAILED", receiver, "GUPSHUP")
            ConnektLogger(LogFile.PROCESSORS).error(s"GCMResponseHandler failed processing http response body for: $messageId", e)
        }
      case Failure(e2) =>
        ServiceFactory.getReportingService.recordPushStatsDelta(requestTracker.clientId, Option(requestTracker.contextId), requestTracker.meta.get("stencilId").map(_.toString), Option(Channel.SMS.toString), requestTracker.appName, GCMResponseStatus.InvalidDevice)
//        events += SMSCallbackEvent(messageId, "", "FAILED", receiver, "GUPSHUP")
        ConnektLogger(LogFile.PROCESSORS).error(s"GCMResponseHandler gcm send failure for: $messageId", e2)
    }

    events.persist
    events.toList
  })(m.executionContext)
}
