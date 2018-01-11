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
import com.flipkart.connekt.commons.iomodels.{CallbackEvent, SmsCallbackEvent, WACallbackEvent}
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

  override implicit val map: CallbackEvent => List[FlowResponseStatus] = {
    case ce: CallbackEvent =>
      val messageId = ce.messageId
      val channel: Channel.Value = ce match {
        case sce: SmsCallbackEvent => Channel.SMS
        case wce: WACallbackEvent => Channel.WA
      }
      val channelName : String = channel.toString.toLowerCase

      val appLevelLatencyConfig = appLevelConfigService.getProjectConfiguration(ce.appName.toLowerCase, s"latency-topology-params-$channelName").get
      if (appLevelLatencyConfig.nonEmpty) {
        val jsonObj = appLevelLatencyConfig.get.value.getObj[Map[String, Any]]
        val excludedEvents: List[String] = jsonObj("excludedEventsList").asInstanceOf[List[String]]
        if (!excludedEvents.contains(ce.eventType.toLowerCase)) {
          val tryCargoMap = ce match {
            case sce: SmsCallbackEvent => Try(sce.cargo.getObj[Map[String, String]])
            case wce: WACallbackEvent => Try(wce.cargo.getObj[Map[String, Any]])
          }
          ConnektLogger(LogFile.SERVICE).trace(s"Ingesting $channelName Metrics.LatencyMetrics for $messageId with cargo : $tryCargoMap.")
          tryCargoMap match {
            case Success(cargoMap) if Try(cargoMap("provider")).isSuccess =>
              val providerName = cargoMap("provider").toString
              meter(s"${ce.appName}.$providerName.${ce.eventType}").mark()

              val pattern = "([A-Za-z]+)([0-9,+]+)".r
              val pattern(appName, phoneNumber) = ce.contactId

              if (jsonObj("event-threshold").asInstanceOf[Map[String,String]].nonEmpty && jsonObj("event-threshold").asInstanceOf[Map[String,String]].contains(ce.eventType) && jsonObj("publishLatency").asInstanceOf[String].toBoolean && cargoMap.nonEmpty) {
                val deliveredTS = try {
                  cargoMap("deliveredTS").toString.toLong
                } catch {
                  case ex: Exception =>
                    meter(s"${ce.appName}.$providerName.errored").mark()
                    ConnektLogger(LogFile.SERVICE).error(s"Erroneous DeliveredTS value being sent by provider: $providerName for messageId:$messageId ${ex.getMessage}")
                    -1L
                }
                if (deliveredTS >= 0L) {
                  val minTimestamp: Long = deliveredTS - jsonObj("hbase-scan-LL").asInstanceOf[String].toLong
                  val maxTimestamp: Long = deliveredTS + jsonObj("hbase-scan-UL").asInstanceOf[String].toLong
                  ServiceFactory.getCallbackService.fetchCallbackEventByMId(messageId, channel, Some(Tuple2(minTimestamp, maxTimestamp))) match {
                    case Success(details) if details.nonEmpty =>
                      val eventDetails = details.get(phoneNumber)
                      eventDetails.foreach(eventDetail => {
                        val receivedEvent = eventDetail.filter(_.eventType.equalsIgnoreCase(jsonObj("receivedEvent").toString))
                        if (receivedEvent.nonEmpty) {
                          val receivedTs: Long = channel match {
                            case Channel.SMS => receivedEvent.head.asInstanceOf[SmsCallbackEvent].timestamp
                            case _ => receivedEvent.head.asInstanceOf[WACallbackEvent].timestamp
                          }
                          val diff = deliveredTS - receivedTs
                          val metricType: String = if (ce.eventType.equalsIgnoreCase("read")) "read" else "delivered"
                          slidingTimer(getMetricName(s"$channelName.$metricType.latency.${receivedEvent.head.appName}.$providerName")).update(diff, TimeUnit.MILLISECONDS)
                          ConnektLogger(LogFile.SERVICE).debug(s"$metricType.LatencyMetrics for $messageId is ingested into cosmos")
                          if (diff > jsonObj("event-threshold").asInstanceOf[Map[String,String]](ce.eventType).toLong) {
                            ConnektLogger(LogFile.SERVICE).info(s"${channelName.toUpperCase} msg $messageId got delayed by $diff ms by provider: $providerName")
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
              ConnektLogger(LogFile.SERVICE).debug(s"Events fetch null providerName for messageId : $messageId")
            case Failure(f) =>
              ConnektLogger(LogFile.SERVICE).error(s"Erroneous $channelName cargo value for messageId : $messageId with error : ", f)
          }
        }
        else {
          ConnektLogger(LogFile.SERVICE).debug(s"$channelName Event: ${ce.eventType} is in the exclusion list for metrics publish, messageID: $messageId")
        }
      }
      else {
        ConnektLogger(LogFile.SERVICE).info(s"The app config for the channel: $channelName is not defined.")
      }
      List(FlowResponseStatus(Status.Success))
    case _ =>
      ConnektLogger(LogFile.SERVICE).info(s"LatencyMetrics for channel callback event not implemented yet.")
      List(FlowResponseStatus(Status.Failed))
  }
}
