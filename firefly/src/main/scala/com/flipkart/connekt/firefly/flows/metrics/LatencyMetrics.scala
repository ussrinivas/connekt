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
  private val publishWALatency: Boolean = ConnektConfig.getBoolean("publish.wa.latency.enabled").getOrElse(false)

  private val latencyScanLL: Long = ConnektConfig.getInt("latency.hbase.scan.lower.limit").getOrElse(86400000).toLong
  private val latencyScanUL: Long = ConnektConfig.getInt("latency.hbase.scan.upper.limit").getOrElse(1800000).toLong

  private val _timer = scala.collection.concurrent.TrieMap[String, Timer]()

  lazy val appLevelConfigService = ServiceFactory.getUserProjectConfigService

  private def slidingTimer(name: String): Timer = _timer.getOrElseUpdate(name, {
    val slidingTimer = new Timer(new SlidingTimeWindowReservoir(2, TimeUnit.MINUTES))
    registry.register(name, slidingTimer)
    slidingTimer
  })

  private def latencyMetricsCalc(messageId: String, receiver: String, channel: Channel.Value, deliveredTS: Long, receivedEventType: String, provider: String = null) = {
    val minTimestamp: Long = deliveredTS - latencyScanLL
    val maxTimestamp: Long = deliveredTS + latencyScanUL
    val channelName = channel.toString.toLowerCase
    ServiceFactory.getCallbackService.fetchCallbackEventByMId(messageId, channel, Some(Tuple2(minTimestamp, maxTimestamp))) match {
      case Success(details) if details.nonEmpty =>
        val eventDetails = details.get(receiver)
        eventDetails.foreach(eventDetail => {
          val receivedEvent = eventDetail.filter(_.eventType.equalsIgnoreCase(receivedEventType))
          if (receivedEvent.nonEmpty) {
            val receivedTs: Long = if (channelName.equalsIgnoreCase(Channel.SMS.toString.toLowerCase)) receivedEvent.head.asInstanceOf[SmsCallbackEvent].timestamp else receivedEvent.head.asInstanceOf[WACallbackEvent].timestamp
            val msgAppName: String = receivedEvent.head.appName
            val diff = deliveredTS - receivedTs
            slidingTimer(getMetricName(s"$channelName.latency.$msgAppName.$provider")).update(diff, TimeUnit.MILLISECONDS)
            ConnektLogger(LogFile.SERVICE).debug(s"Metrics.LatencyMetrics for $messageId is ingested into cosmos")
            val appThresholdConfig = appLevelConfigService.getProjectConfiguration(msgAppName.toLowerCase, s"latency-threshold-$channelName").get
            if (appThresholdConfig.nonEmpty && diff > appThresholdConfig.get.value.toLong) {
              ConnektLogger(LogFile.SERVICE).info(s"${channelName.toUpperCase} msg $messageId got delayed by $diff ms by provider: $provider")
            }
          }
        })
      case Success(details) =>
        ConnektLogger(LogFile.SERVICE).debug(s"Events not available: fetchCallbackEventByMId for messageId : $messageId")
      case Failure(f) =>
        ConnektLogger(LogFile.SERVICE).error(s"Events fetch failed fetchCallbackEventByMId for messageId : $messageId with error : ", f)
    }
  }


  override implicit val map: CallbackEvent => List[FlowResponseStatus] = {

    case wce: WACallbackEvent =>
      val messageId = wce.messageId
      def excludedEvents: List[String] = ConnektConfig.getList[String]("wa.metrics.publish.excluded.eventsList").map(_.toLowerCase)

      ConnektLogger(LogFile.SERVICE).trace(s"Ingesting WA Metrics.LatencyMetrics for $messageId with cargo : ${wce.cargo}.")
      if (!excludedEvents.contains(wce.eventType.toLowerCase)) {
        val tryCargoMap = Try(wce.cargo.getObj[Map[String, Any]])
        tryCargoMap match {
          case Success(cargoMap) =>
            meter(s"${wce.appName}.${wce.eventType}").mark()
            if (wce.eventType.equalsIgnoreCase(WAResponseStatus.Delivered) && publishWALatency && cargoMap.nonEmpty) {
              val deliveredTS: Long = try {
                cargoMap("payload").asInstanceOf[Map[String, String]]("timestamp").toLong*1000
              } catch {
                case ex: Exception =>
                  meter(s"${wce.appName}.errored").mark()
                  ConnektLogger(LogFile.SERVICE).error(s"Erroneous DeliveredTS value for messageId:$messageId ${ex.getMessage}")
                  -1L
              }
              if (deliveredTS >= 0L)
                latencyMetricsCalc(messageId, wce.destination, Channel.WA, deliveredTS, WAResponseStatus.Received)
            }
          case Failure(f) =>
            ConnektLogger(LogFile.SERVICE).error(s"Erroneous WA cargo value for messageId : $messageId with error : ", f)
        }
      }
      else {
        ConnektLogger(LogFile.SERVICE).debug(s"WA Event: ${wce.eventType} is in the exclusion list for metrics publish, messageID: $messageId")
      }
      List(FlowResponseStatus(Status.Success))

    case sce: SmsCallbackEvent =>
      val messageId = sce.messageId
      def excludedEvents: List[String] = ConnektConfig.getList[String]("sms.metrics.publish.excluded.eventsList").map(_.toLowerCase)

      ConnektLogger(LogFile.SERVICE).trace(s"Ingesting SMS Metrics.LatencyMetrics for $messageId with cargo : ${sce.cargo}.")
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
              if (deliveredTS >= 0L)
                latencyMetricsCalc(messageId, sce.receiver, Channel.SMS, deliveredTS, SmsResponseStatus.Received, providerName)
            }
          case Success(cargoMap) =>
            ConnektLogger(LogFile.SERVICE).debug(s"Events fetch null providerName for cargo: ${sce.cargo} messageId : $messageId")
          case Failure(f) =>
            ConnektLogger(LogFile.SERVICE).error(s"Erroneous SMS cargo value for messageId : $messageId with error : ", f)
        }
      }
      else {
        ConnektLogger(LogFile.SERVICE).debug(s"Event: ${sce.eventType} is in the exclusion list for metrics publish, messageID: $messageId")
      }
      List(FlowResponseStatus(Status.Success))
    case _ =>
      ConnektLogger(LogFile.SERVICE).info(s"SMS LatencyMetrics for channel callback event not implemented yet.")
      List(FlowResponseStatus(Status.Failed))
  }
}
