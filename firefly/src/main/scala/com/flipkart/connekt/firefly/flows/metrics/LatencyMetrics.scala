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
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.flows.MapFlowStage
import com.flipkart.connekt.firefly.models.FlowResponseStatus
import com.flipkart.connekt.firefly.models.Status

import scala.util.{Failure, Success, Try}

class LatencyMetrics extends MapFlowStage[CallbackEvent, FlowResponseStatus] with Instrumented {

  private val _timer = scala.collection.concurrent.TrieMap[String, Timer]()

  lazy val appLevelConfigService = ServiceFactory.getUserProjectConfigService

  private def slidingTimer(name: String): Timer = _timer.getOrElseUpdate(name, {
    val slidingTimer = new Timer(new SlidingTimeWindowReservoir(2, TimeUnit.MINUTES))
    registry.register(name, slidingTimer)
    slidingTimer
  })

  override implicit val map: CallbackEvent => List[FlowResponseStatus] = ce => {
      val messageId = ce.messageId
      val channel: Channel.Value = ce match {
        case sce: SmsCallbackEvent => Channel.SMS
        case wce: WACallbackEvent => Channel.WA
        case ece: EmailCallbackEvent => Channel.EMAIL
        case pce: PNCallbackEvent => Channel.PUSH
        case _ => throw new Exception("No such channel support is there.")
      }
      val channelName : String = channel.toString.toLowerCase

      val appLevelLatencyConfig = appLevelConfigService.getProjectConfiguration(ce.appName.toLowerCase, s"latency-topology-params-$channelName").get
      if (appLevelLatencyConfig.nonEmpty) {
        val jsonObj = appLevelLatencyConfig.get.value.getObj[Map[String, Any]]
        val excludedEvents: List[String] = jsonObj("excludedEventsList").asInstanceOf[List[String]]
        val kafkaEvent: String = ce.eventType
        if (!excludedEvents.contains(kafkaEvent.toLowerCase)) {
          val tryCargoMap = ce match {
            case sce: SmsCallbackEvent => Try(sce.cargo.getObj[Map[String, Any]])
            case wce: WACallbackEvent => Try(wce.cargo.getObj[Map[String, Any]])
            case ece: EmailCallbackEvent => Try(ece.cargo.getObj[Map[String, Any]])
            case pce: PNCallbackEvent => Try(pce.cargo.getObj[Map[String, Any]])
            case _ => Failure(new IllegalArgumentException("Undefined callback event type"))
          }
          tryCargoMap match {
            case Success(cargoMap) if Try(cargoMap("provider")).isSuccess =>
              ConnektLogger(LogFile.SERVICE).trace(s"Ingesting $channelName Metrics.LatencyMetrics for $messageId with cargo : $tryCargoMap.")
              val providerName = cargoMap("provider").toString
              meter(s"$channelName.${ce.appName}.$providerName.$kafkaEvent").mark()

              val eventThreshold = jsonObj("event-threshold").asInstanceOf[Map[String,Int]]
              if (eventThreshold.nonEmpty && eventThreshold.contains(kafkaEvent) && jsonObj("publishLatency").asInstanceOf[String].toBoolean && cargoMap.nonEmpty) {
                val deliveredTS = try {
                  cargoMap("deliveredTS").toString.toLong
                } catch {
                  case ex: Exception =>
                    meter(s"$channelName.${ce.appName}.$providerName.errored").mark()
                    ConnektLogger(LogFile.SERVICE).error(s"Erroneous DeliveredTS value being sent in channel $channelName by provider: $providerName for messageId:$messageId ${ex.getMessage}")
                    -1L
                }
                if (deliveredTS >= 0L) {
                  val minTimestamp: Long = deliveredTS - jsonObj("hbase-scan-lowerLimit").asInstanceOf[Int].toLong
                  val maxTimestamp: Long = deliveredTS + jsonObj("hbase-scan-upperLimit").asInstanceOf[Int].toLong
                  ServiceFactory.getCallbackService.fetchCallbackEventByMId(messageId, channel, Some(Tuple2(minTimestamp, maxTimestamp))) match {
                    case Success(msgDetail) if msgDetail.nonEmpty =>
                      val eventDetails = msgDetail.get(ce.destination)
                      eventDetails.foreach(eventDetail => {
                          eventDetail.filter(_.eventType.equalsIgnoreCase(jsonObj("receivedEvent").toString)).foreach(receivedEvent => {
                          val receivedTs: Long = receivedEvent.timestamp
                          val diff: Long = deliveredTS - receivedTs
                          slidingTimer(getMetricName(s"$channelName.$kafkaEvent.latency.${receivedEvent.appName}.$providerName")).update(diff, TimeUnit.MILLISECONDS)
                          ConnektLogger(LogFile.SERVICE).debug(s"${channelName.toUpperCase} $kafkaEvent.LatencyMetrics for $messageId is ingested into cosmos")
                          val thresholdVal = eventThreshold(kafkaEvent).toLong
                          if (diff > thresholdVal) {
                            ConnektLogger(LogFile.SERVICE).info(s"${channelName.toUpperCase} msg $messageId got delayed by $diff ms by provider: $providerName")
                          }
                        })
                      })
                    case Success(msgDetail) =>
                      ConnektLogger(LogFile.SERVICE).debug(s"Events not available: fetchCallbackEventByMId for messageId : $messageId")
                    case Failure(f) =>
                      ConnektLogger(LogFile.SERVICE).error(s"Events fetch failed fetchCallbackEventByMId for messageId : $messageId with error : ", f)
                  }
                }
              }
            case Success(cargoMap) =>
              ConnektLogger(LogFile.SERVICE).debug(s"Events fetch null providerName for messageId : $messageId")
            case Failure(f) =>
              ConnektLogger(LogFile.SERVICE).error(s"Erroneous $channelName cargo value for messageId : $messageId with error : ", f)
          }
        }
        else {
          ConnektLogger(LogFile.SERVICE).debug(s"$channelName Event: $kafkaEvent is in the exclusion list for metrics publish, messageID: $messageId")
        }
      }
      else {
        ConnektLogger(LogFile.SERVICE).info(s"The app config for the channel: $channelName is not defined.")
      }
      List(FlowResponseStatus(Status.Success))
  }
}
