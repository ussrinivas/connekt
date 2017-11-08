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
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.collection.JavaConverters._
import scala.concurrent.Future

class LatencyMetrics extends Instrumented {

  private def publishSMSLatency: Boolean = ConnektConfig.getBoolean("publish.sms.latency").getOrElse(false)

  private val _timer = new ConcurrentHashMap[String,Timer]().asScala
  private def slidingTimer(name:String):Timer =  _timer.getOrElseUpdate(name, {
    val slidingTimer = new Timer(new SlidingTimeWindowReservoir(2, TimeUnit.MINUTES))
    registry.register(name, slidingTimer)
    slidingTimer
  })

  def sink: Sink[CallbackEvent, Future[Done]] = Sink.foreach[CallbackEvent] {
    case event@(sce: SmsCallbackEvent) =>
      val providerName :String = Option(sce.cargo).filter(_.nonEmpty).map(_.getObj[Map[String, String]].getOrElse("provider","na")).getOrElse("na")
      meter(s"provider.$providerName.event.${sce.eventType}").mark()

      if (sce.eventType.equalsIgnoreCase("sms_delivered") && publishSMSLatency) {
        val smsEventDetails = ServiceFactory.getCallbackService.fetchCallbackEventByMId(event.messageId.toString, Channel.SMS)
        val eventDetails = smsEventDetails.get.get(sce.asInstanceOf[SmsCallbackEvent].receiver)
        val deliveredTS = sce.timestamp
        eventDetails.foreach(eventDetail => {
          if (eventDetail.exists(_.eventType.equalsIgnoreCase("sms_received"))) {
            val receivedEvent = eventDetail.filter(_.eventType.equalsIgnoreCase("sms_received")).head

            val receivedProviderName = if (providerName.equalsIgnoreCase("na")) receivedEvent.asInstanceOf[SmsCallbackEvent].cargo.getObj[Map[String, String]].getOrElse("provider", "na") else providerName

            val recTS = receivedEvent.asInstanceOf[SmsCallbackEvent].timestamp
            val lagTS = deliveredTS - recTS
            slidingTimer(getMetricName(s"SMS.latency.$receivedProviderName")).update(lagTS, TimeUnit.MILLISECONDS)
            ConnektLogger(LogFile.SERVICE).trace(s"Metrics.LatencyMetrics for ${event.messageId.toString} is ingested into cosmos")
          }
        })
      }
    case _ =>
  }
}
