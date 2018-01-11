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
package com.flipkart.connekt.firefly.flows.metrics

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{SlidingTimeWindowReservoir, Timer}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.MessageStatus._
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.flows.MapFlowStage
import com.flipkart.connekt.firefly.models.FlowResponseStatus
import com.flipkart.connekt.firefly.models.Status

import scala.util.{Failure, Success, Try}

class LatencyMetrics extends MapFlowStage[CallbackEvent, FlowResponseStatus] with Instrumented {

  private val publishSMSLatency: Boolean = ConnektConfig.getBoolean("publish.sms.latency.enabled").getOrElse(false)

  private val _timer = scala.collection.concurrent.TrieMap[String, Timer]()

  lazy val appLevelConfigService = ServiceFactory.getUserProjectConfigService

  private def slidingTimer(name: String): Timer = _timer.getOrElseUpdate(name, {
    val slidingTimer = new Timer(new SlidingTimeWindowReservoir(2, TimeUnit.MINUTES))
    registry.register(name, slidingTimer)
    slidingTimer
  })

  override implicit val map: CallbackEvent => List[FlowResponseStatus] = {
    case sce: SmsCallbackEvent =>
      val messageId = sce.messageId

      def excludedEvents: List[String] = ConnektConfig.getList[String]("sms.metrics.publish.excluded.eventsList").map(_.toLowerCase)

      ConnektLogger(LogFile.SERVICE).trace(s"Ingesting Metrics.LatencyMetrics for $messageId with cargo : ${sce.cargo}.")
      if (!excludedEvents.contains(sce.eventType.toLowerCase)) {
        val tryCargoMap = Try(sce.cargo.getObj[Map[String, String]])
        tryCargoMap match {
          case Success(cargoMap) if Try(cargoMap("provider")).isSuccess =>
            val providerName = cargoMap("provider")
            meter(s"${sce.appName}.$providerName.${sce.eventType}").mark()
            if (sce.eventType.equalsIgnoreCase(SmsResponseStatus.Delivered) && publishSMSLatency && cargoMap.nonEmpty) {
              val deliveredTS: Long = try {
                cargoMap("deliveredTS").toLong
              } catch {
                case ex: Exception =>
                  meter(s"${sce.appName}.$providerName.errored").mark()
                  ConnektLogger(LogFile.SERVICE).error(s"Erroneous DeliveredTS value being sent by provider: $providerName for messageId:$messageId ${ex.getMessage}")
                  -1L
              }
              if (deliveredTS >= 0L) {
                val minTimestamp: Long = sce.timestamp - 86400000L
                val maxTimestamp: Long = sce.timestamp + 1800000L

                ServiceFactory.getCallbackService.fetchCallbackEventByMId(messageId, Channel.SMS, Some(Tuple2(minTimestamp, maxTimestamp))) match {
                  case Success(details) if details.nonEmpty =>
                    val eventDetails = details.get(sce.receiver)
                    eventDetails.foreach(eventDetail => {
                      val receivedEvent = eventDetail.filter(_.eventType.equalsIgnoreCase(SmsResponseStatus.Received))
                      if (receivedEvent.nonEmpty) {
                        val receivedTs = receivedEvent.head.asInstanceOf[SmsCallbackEvent].timestamp
                        val diff = deliveredTS - receivedTs
                        slidingTimer(getMetricName(s"sms.latency.${receivedEvent.head.appName}.$providerName")).update(diff, TimeUnit.MILLISECONDS)
                        ConnektLogger(LogFile.SERVICE).debug(s"Metrics.LatencyMetrics for $messageId is ingested into cosmos")
                        val msgAppName: String = receivedEvent.head.appName
                        val appThresholdConfig = appLevelConfigService.getProjectConfiguration(msgAppName.toLowerCase, s"latency-threshold-sms").get
                        if (appThresholdConfig.nonEmpty && diff > appThresholdConfig.get.value.toLong) {
                            ConnektLogger(LogFile.SERVICE).info(s"$providerName took $diff ms to deliver an OTP SMS $messageId.")
                        }
                      }
                    })
                  case Success(details) =>
                    ConnektLogger(LogFile.SERVICE).debug(s"Events not available: fetchCallbackEventByMId for messageId : $messageId")
                  case Failure(f) =>
                    ConnektLogger(LogFile.SERVICE).error(s"Events fetch failed fetchCallbackEventByMId for messageId : $messageId with error : ", f)
                }
              }
            }
          case Success(cargoMap) =>
            ConnektLogger(LogFile.SERVICE).debug(s"Events fetch null providerName for cargo: ${sce.cargo} messageId : $messageId")
          case Failure(f) =>
            ConnektLogger(LogFile.SERVICE).error(s"Erroneous cargo value for messageId : $messageId with error : ", f)
        }
      }
      else {
        ConnektLogger(LogFile.SERVICE).debug(s"Event: ${sce.eventType} is in the exclusion list for metrics publish, messageID: $messageId")
      }
      List(FlowResponseStatus(Status.Success))
    case _ =>
      ConnektLogger(LogFile.SERVICE).info(s"LatencyMetrics for channel callback event not implemented yet.")
      List(FlowResponseStatus(Status.Failed))
  }
}
