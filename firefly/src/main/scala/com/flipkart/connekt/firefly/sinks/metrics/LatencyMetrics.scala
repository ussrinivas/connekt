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
package com.flipkart.connekt.firefly.sinks.metrics

import akka.Done
import akka.stream.scaladsl.Sink
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.Future


class LatencyMetrics extends Instrumented {

  private def publishSMSLatency: Boolean = ConnektConfig.getBoolean("publish.sms.latency").getOrElse(true)

  def sink: Sink[CallbackEvent, Future[Done]] = Sink.foreach[CallbackEvent] {
    case event@(sce: SmsCallbackEvent) =>
      val providerName = sce.cargo.getObj[Map[String, String]].getOrElse("provider", "na")
      meter(s"provider.$providerName.event.${sce.eventType}").mark()

      if (sce.eventType.equalsIgnoreCase("sms_delivered") && publishSMSLatency) {
        val smsEventDetails = ServiceFactory.getCallbackService.fetchCallbackEventByMId(event.messageId.toString, Channel.SMS)
        val eventDetails = smsEventDetails.get(sce.asInstanceOf[SmsCallbackEvent].receiver)
        Option(eventDetails).foreach(eventDetail => {
          if (eventDetail.exists(_.eventType.equalsIgnoreCase("sms_received"))) {
            val receivedEvent = eventDetail.filter(_.eventType.equalsIgnoreCase("sms_received")).head
            val recTS = receivedEvent.asInstanceOf[SmsCallbackEvent].timestamp
            val lagTS = sce.timestamp - recTS
            registry.histogram(getMetricName(s"SMS.latency.$providerName")).update(lagTS)
          }
        })
      }
    case _ =>
  }
}
