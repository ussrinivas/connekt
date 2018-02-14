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
package com.flipkart.connekt.firefly.topology

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source}
import com.flipkart.connekt.busybees.streams.flows.FlowMetrics
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{CallbackEvent, Constants}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.sync.{SyncManager, SyncType}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.flows.metrics.LatencyMetrics
import com.flipkart.connekt.firefly.models.FlowResponseStatus
import com.typesafe.config.Config

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class LatencyMeteringTopology(kafkaConsumerConfig: Config) extends CustomTopology[CallbackEvent, FlowResponseStatus] {

  private val latencyMetricsKafkaThrottle = ConnektConfig.getOrElse("latencyMetrics.kafka.throttle.rps", 100)
  private lazy val CALLBACK_QUEUE_NAME = ConnektConfig.get("firefly.latency.metric.kafka.topic").getOrElse("ckt_callback_events_%s")

  def latencyTransformFlow: Flow[CallbackEvent, FlowResponseStatus, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b =>
    val latencyMetrics = b.add(new LatencyMetrics().flow)
    FlowShape(latencyMetrics.in, latencyMetrics.out)
  })

  override def sources: Map[CheckPointGroup, Source[CallbackEvent, NotUsed]] = {

    val enabledTopology = ListBuffer[String]()
    SyncManager.getNodeData(SyncManager.getBucketNodePath + "/" + SyncType.SMS_LATENCY_METER_TOPOLOGY_UPDATE).map(_.message.head) match {
      case Some("stop") =>
        ConnektLogger(LogFile.SERVICE).info(s"SmsLatencyMeter Topology stopped by admin.")
      case _ =>
        enabledTopology += Channel.SMS
    }

    SyncManager.getNodeData(SyncManager.getBucketNodePath + "/" + SyncType.WA_LATENCY_METER_TOPOLOGY_UPDATE).map(_.message.head) match {
      case Some("stop") =>
        ConnektLogger(LogFile.SERVICE).info(s"WaLatencyMeter Push Topology stopped by admin.")
      case _ =>
        enabledTopology += Channel.WA
    }

    enabledTopology.flatMap { channel =>
      Map(channel + Constants.LatencyMeterConstants.LATENCY_METER -> createMergedSource[CallbackEvent](channel + Constants.LatencyMeterConstants.LATENCY_METER, Seq(CALLBACK_QUEUE_NAME.format(channel.toLowerCase)), kafkaConsumerConfig)
        .throttle(latencyMetricsKafkaThrottle,
          1.second,
          latencyMetricsKafkaThrottle,
          ThrottleMode.Shaping))
    }.toMap
  }

  override def transformers: Map[CheckPointGroup, Flow[CallbackEvent, FlowResponseStatus, NotUsed]] =
    Map(Constants.LatencyMeterConstants.WA_LATENCY_METER.toString -> latencyTransformFlow,
      Constants.LatencyMeterConstants.SMS_LATENCY_METER.toString -> latencyTransformFlow)

  override def sink: Sink[FlowResponseStatus, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit b =>
    val metrics = b.add(new FlowMetrics[FlowResponseStatus]("latency.meter").flow)
    metrics ~> Sink.ignore
    SinkShape(metrics.in)
  })

  override def topologyName: String = Constants.LatencyMeterConstants.WA_LATENCY_METER + "_" + Constants.LatencyMeterConstants.SMS_LATENCY_METER
}
