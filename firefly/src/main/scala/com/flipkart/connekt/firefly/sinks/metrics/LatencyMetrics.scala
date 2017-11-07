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

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import akka.Done
import akka.stream.scaladsl.Sink
import com.codahale.metrics.{SlidingTimeWindowReservoir, Timer}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.JavaConverters._
import scala.concurrent.Future

class LatencyMetrics extends Instrumented {

  private def publishSMSLatency: Boolean = ConnektConfig.getBoolean("publish.sms.latency").getOrElse(true)

  private val _timer = new ConcurrentHashMap[String,Timer]().asScala
  private def slidingTimer(name:String):Timer =  _timer.getOrElseUpdate(name, {
    val slidingTimer = new Timer(new SlidingTimeWindowReservoir(5, TimeUnit.MINUTES))
    registry.register(name, slidingTimer)
    slidingTimer
  })

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
            slidingTimer(getMetricName(s"SMS.latency.$providerName")).update(lagTS, TimeUnit.MILLISECONDS)
          }
        })
      }
    case _ =>
  }
}
