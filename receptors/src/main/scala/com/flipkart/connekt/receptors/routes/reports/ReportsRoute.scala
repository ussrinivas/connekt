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
package com.flipkart.connekt.receptors.routes.reports

import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.entities.Channel._
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.DateTimeUtils
import com.flipkart.connekt.receptors.directives.ChannelSegment
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.wire.ResponseUtils._


class ReportsRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  val route =
    authenticate {
      user =>
        pathPrefix("v1" / "reports") {
          authorize(user, "REPORTS") {
            path(ChannelSegment / Segment / "messages" / Segment / Segment / "events") {
              (channel: Channel, appName: String, deviceId: String, messageId: String) =>
                get {
                  meteredResource(s"reports${channel}MessageEvents.$appName") {
                    val events = ServiceFactory.getCallbackService.fetchCallbackEvent(messageId, s"${appName.toLowerCase}$deviceId", channel).get
                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Events fetched for messageId: $messageId contactId: $deviceId fetched.", Map(deviceId -> events.map(_._1)))))
                  }
                }
            } ~ path(ChannelSegment / Segment / "messages" / Segment) {
              (channel: Channel, appName: String, contactId: String) =>
                get {
                  meteredResource(s"reports${channel}ContactEvents.$appName") {
                    parameters("startTs" ? 0L) { (startTs) =>
                      val events = ServiceFactory.getCallbackService.fetchCallbackEventByContactId(s"${appName.toLowerCase}$contactId", channel, startTs, System.currentTimeMillis()).getOrElse(Nil)
                      val messageIds  = events.map(_._1.asInstanceOf[CallbackEvent]).map(_.messageId).distinct
                      val messages = messageIds.flatMap(ServiceFactory.getMessageService(channel).getRequestInfo(_).getOrElse(None))
                      val finalTs = events.map(_._2).reduceLeftOption(_ max _).getOrElse(System.currentTimeMillis)

                      complete(GenericResponse(StatusCodes.OK.intValue, Map("contactId" -> contactId, "appName" -> appName, "startTs" -> startTs), Response(s"messages fetched for $appName / $contactId", Map("messages" -> messages, "endTs" -> finalTs, "count" -> messages.size))))
                    }
                  }
                }
            } ~ path(ChannelSegment / "messages" / Segment / Segment / "events") {
              (channel: Channel, contactId: String, messageId: String) =>
                get {
                  meteredResource("reportsContactEvents") {
                    val events = ServiceFactory.getCallbackService.fetchCallbackEvent(messageId, s"$contactId", channel).get
                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Events fetched for messageId: $messageId contactId: $contactId fetched.", Map(contactId -> events))))
                  }
                }
            } ~ path(ChannelSegment / "messages" / Segment / "events") {
              (channel: Channel, messageId: String) =>
                get {
                  meteredResource("reportsMessageEvents") {
                    val events = ServiceFactory.getCallbackService.fetchCallbackEventByMId(messageId, channel).get
                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Events for $messageId fetched.", events)))
                  }
                }
            } ~ path(ChannelSegment / "messages" / Segment) {
              (channel: Channel, messageId: String) =>
                get {
                  meteredResource("reportsMessage") {
                    val data = ServiceFactory.getMessageService(channel).getRequestInfo(messageId).get
                    data match {
                      case None =>
                        complete(GenericResponse(StatusCodes.NotFound.intValue, Map("messageId" -> messageId), Response(s"No Message found for messageId $messageId.", null)))
                      case Some(x) =>
                        complete(GenericResponse(StatusCodes.OK.intValue, Map("messageId" -> messageId), Response(s"Message info fetched for messageId: $messageId.", data)))
                    }
                  }
                }
            }
          } ~ path("date" / Segment) {
            (date: String) =>
              get {
                parameters('clientId, 'contextId ?, 'appName ?, 'stencilId ?) { (clientId, contextId, appName, stencilId) =>
                  authorize(user, s"STATS_$clientId", "STATS") {
                    require(DateTimeUtils.parseCalendarDate(date).isSuccess, "Only 'yyyyMMdd' (Calendar Date) format allowed in `date`")

                    val result = ServiceFactory.getReportingService.getAllDetails(date = date, clientId = clientId, contextId = contextId, stencilId = stencilId, appName = appName, platform = None, channel = None)
                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response("Statistics", result)))
                  }
                }

              }

          }
        }
    }
}
