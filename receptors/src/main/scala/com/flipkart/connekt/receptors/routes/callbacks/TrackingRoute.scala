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
package com.flipkart.connekt.receptors.routes.callbacks

import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels.{CallbackEvent, EmailCallbackEvent, SmsCallbackEvent}
import com.flipkart.connekt.commons.services.URLMessageTracker
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.receptors.routes.BaseHandler
import org.apache.commons.net.util.Base64

class TrackingRoute(implicit am: ActorMaterializer) extends BaseHandler {

  private val tranparentPNG = Base64.decodeBase64("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=").toList.toArray

  val route = pathPrefix("t") {
    path("open" / Segment) {
      (encodedData: String) =>
        get {
          val entity = Base64.decodeBase64(encodedData).getObj[URLMessageTracker]

          val channel = Channel.withName(entity.channel)
          val event: CallbackEvent = channel match {
            case Channel.EMAIL =>
              EmailCallbackEvent(messageId = entity.messageId,
                clientId = entity.clientId,
                address = entity.destination,
                eventType = "OPEN",
                appName = entity.appName,
                contextId = entity.contextId.orEmpty,
                cargo = null)
          }

          event.persist
          ServiceFactory.getReportingService.recordChannelStatsDelta(entity.clientId, entity.contextId, None, channel, entity.appName, event.eventType)
          ConnektLogger(LogFile.SERVICE).debug(s"Received callback event ${event.toString}")

          complete {
            HttpResponse(
              status = StatusCodes.OK,
              headers = Nil,
              entity = HttpEntity(MediaTypes.`image/png`, tranparentPNG)
            )
          }

        }
    } ~ path("click" / Segment) {
      (encodedData: String) =>
        get {
          val entity = Base64.decodeBase64(encodedData).getObj[URLMessageTracker]

          val channel = Channel.withName(entity.channel)
          val event: CallbackEvent = channel match {
            case Channel.EMAIL =>
              EmailCallbackEvent(messageId = entity.messageId,
                clientId = entity.clientId,
                address = entity.destination,
                eventType = "CLICK",
                appName = entity.appName,
                contextId = entity.contextId.orEmpty,
                cargo = Map("name" -> entity.linkName, "url" -> entity.url).getJson)

            case Channel.SMS =>
              SmsCallbackEvent(messageId = entity.messageId,
                clientId = entity.clientId,
                receiver = entity.destination,
                eventType = "CLICK",
                appName = entity.appName,
                contextId = entity.contextId.orEmpty,
                cargo = Map("name" -> entity.linkName, "url" -> entity.url).getJson)
          }

          event.persist
          ServiceFactory.getReportingService.recordChannelStatsDelta(entity.clientId, entity.contextId, None, channel, entity.appName, event.eventType)
          ConnektLogger(LogFile.SERVICE).debug(s"Received callback event ${event.toString}")

          redirect(entity.url, StatusCodes.Found)
        }
    }
  }
}
