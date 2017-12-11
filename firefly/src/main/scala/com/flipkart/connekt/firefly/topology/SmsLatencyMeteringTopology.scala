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
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import com.flipkart.connekt.busybees.streams.flows.FlowMetrics
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.CallbackEvent
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.flows.metrics.LatencyMetrics
import com.flipkart.connekt.firefly.models.FlowResponseStatus
import com.typesafe.config.Config

import scala.concurrent.duration._

class SmsLatencyMeteringTopology(kafkaConsumerConfig: Config, topicName: String) extends CustomTopology[CallbackEvent, FlowResponseStatus] {

  private val latencyMetricsKafkaThrottle = ConnektConfig.getOrElse("latencyMetrics.kafka.throttle.rps", 100)

  private def createMergedSource(checkpointGroup: CheckPointGroup, topics: Seq[String]): Source[CallbackEvent, NotUsed] = Source.fromGraph(GraphDSL.create() { implicit b =>
    val groupId = kafkaConsumerConfig.getString("group.id")
    ConnektLogger(LogFile.PROCESSORS).info(s"Creating composite source for topics: ${topics.toString()}")
    val merge = b.add(Merge[CallbackEvent](topics.size))
    for (portNum <- 0 until merge.n) {
      val consumerGroup = s"${groupId}_$checkpointGroup"
      new KafkaSource[CallbackEvent](kafkaConsumerConfig, topic = topics(portNum), consumerGroup) ~> merge.in(portNum)
    }
    SourceShape(merge.out)
  })

  def latencyTransformFlow: Flow[CallbackEvent, FlowResponseStatus, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b =>
    val latencyMetrics = b.add(new LatencyMetrics().flow)
    FlowShape(latencyMetrics.in, latencyMetrics.out)
  })


  override def sources: Map[CheckPointGroup, Source[CallbackEvent, NotUsed]] = {
    Map(Channel.WA.toString ->
      createMergedSource(Channel.SMS, Seq(topicName))
        .throttle(latencyMetricsKafkaThrottle, 1.second, latencyMetricsKafkaThrottle, ThrottleMode.Shaping)
    )
  }

  override def transformers: Map[CheckPointGroup, Flow[CallbackEvent, FlowResponseStatus, NotUsed]] = Map(Channel.SMS.toString -> latencyTransformFlow)

  override def sink: Sink[FlowResponseStatus, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit b =>
    val metrics = b.add(new FlowMetrics[FlowResponseStatus]("wa.contact").flow)
    metrics ~> Sink.ignore
    SinkShape(metrics.in)
  })
}
