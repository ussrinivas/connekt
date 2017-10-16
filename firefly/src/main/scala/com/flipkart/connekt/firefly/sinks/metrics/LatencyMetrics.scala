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
import com.flipkart.connekt.commons.entities.{Channel, SubscriptionEvent}
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._

/**
  * Created by grishma.s on 11/10/17.
  */
class LatencyMetrics extends Instrumented {

//  TODO: Temp hack, clean this shit.
  def sink = Sink.foreach[CallbackEvent](event => {

    def smsHbaseLookup = ConnektConfig.getBoolean("sms.callback.hbase.lookup").getOrElse(false)

    event match {
      case sce:SmsCallbackEvent =>

        val providerName = sce.cargo.getObj[Map[String, String]].getOrElse("provider", "")
        meter(s"provider.$providerName.event.${sce.eventType}").mark()

        if(sce.eventType.equalsIgnoreCase("sms_delivered") && smsHbaseLookup) {

          val smsEventDetails = ServiceFactory.getCallbackService.fetchCallbackEventByMId(event.messageId.toString, Channel.SMS)
          val eventDetails = smsEventDetails.get(sce.asInstanceOf[SmsCallbackEvent].receiver)

          if(eventDetails.exists(_.eventType.equalsIgnoreCase("sms_received"))) {
            val receivedEvent = eventDetails.filter(_.eventType.equalsIgnoreCase("sms_received")).head
            val recTS = receivedEvent.asInstanceOf[SmsCallbackEvent].timestamp
            val delivTS = sce.timestamp
            val lagTS = delivTS - recTS
            registry.histogram(getMetricName(s"SMS.latency.$providerName")).update(lagTS)
          }
        }
      case _ =>
    }
  })
}
