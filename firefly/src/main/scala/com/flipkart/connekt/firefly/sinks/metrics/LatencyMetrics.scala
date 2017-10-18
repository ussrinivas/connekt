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

import akka.stream.scaladsl.Sink
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._


class LatencyMetrics extends Instrumented {

  def smsHbaseLookup = ConnektConfig.getBoolean("sms.callback.hbase.lookup").getOrElse(false)

  def sink = Sink.foreach[CallbackEvent](event => {

    event match {
      case sce:SmsCallbackEvent =>

        val providerName = sce.cargo.getObj[Map[String, String]].getOrElse("provider", "na")
        meter(s"provider.$providerName.event.${sce.eventType}").mark()

        if(sce.eventType.equalsIgnoreCase("sms_delivered") && smsHbaseLookup) {

          try {
            val smsEventDetails = ServiceFactory.getCallbackService.fetchCallbackEventByMId(event.messageId.toString, Channel.SMS)
            val eventDetails = smsEventDetails.get(sce.asInstanceOf[SmsCallbackEvent].receiver)

            if(eventDetails.exists(_.eventType.equalsIgnoreCase("sms_received"))) {
              val receivedEvent = eventDetails.filter(_.eventType.equalsIgnoreCase("sms_received")).head
              val recTS = receivedEvent.asInstanceOf[SmsCallbackEvent].timestamp
              val lagTS = sce.timestamp - recTS
              registry.histogram(getMetricName(s"SMS.latency.$providerName")).update(lagTS)
            }
          } catch {
            case e: NullPointerException =>
          }
        }
      case _ =>
    }
  })
}
