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
import com.flipkart.connekt.busybees.streams.flows.profilers.TimedFlowOps._
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{ContactPayload, ContactPayloads}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.flows.dispatchers.{HttpDispatcher, WAContactHttpDispatcherPrepare}
import com.flipkart.connekt.firefly.flows.responsehandlers.WAContactResponseHandler
import com.flipkart.connekt.firefly.models.FlowResponseStatus
import com.typesafe.config.Config

import scala.concurrent.duration._

class WAContactTopology(kafkaConsumerConfig: Config, topicName: String) extends CustomTopology[ContactPayloads, FlowResponseStatus] {

  private val waContactSize: Int = ConnektConfig.getInt("wa.contact.batch.size").getOrElse(1000)
  private val waContactTimeLimit: Int = ConnektConfig.getInt("wa.contact.wait.time.limit.sec").getOrElse(2)

  private def createMergedSource(checkpointGroup: CheckPointGroup, topics: Seq[String]): Source[ContactPayload, NotUsed] = Source.fromGraph(GraphDSL.create() { implicit b =>
    val groupId = kafkaConsumerConfig.getString("group.id")
    ConnektLogger(LogFile.PROCESSORS).info(s"Creating composite source for topics: ${topics.toString()}")
    val merge = b.add(Merge[ContactPayload](topics.size))
    for (portNum <- 0 until merge.n) {
      val consumerGroup = s"${groupId}_$checkpointGroup"
      new KafkaSource[ContactPayload](kafkaConsumerConfig, topic = topics(portNum), consumerGroup) ~> merge.in(portNum)
    }
    SourceShape(merge.out)
  })

  override def sources: Map[CheckPointGroup, Source[ContactPayloads, NotUsed]] = {
    val waKafkaThrottle = ConnektConfig.getOrElse("wa.contact.throttle.rps", 2)
    Map(Channel.WA.toString ->
      createMergedSource(Channel.WA, Seq(topicName))
        .groupedWithin(waContactSize, waContactTimeLimit.second)
        .throttle(waKafkaThrottle, 1.second, waKafkaThrottle, ThrottleMode.Shaping)
        .via(Flow[Seq[ContactPayload]].map {
          ContactPayloads
        })
    )
  }

  def waContactTransformFlow: Flow[ContactPayloads, FlowResponseStatus, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b =>

    val dispatcherPrepFlow = b.add(new WAContactHttpDispatcherPrepare().flow)
    val httpCachedClient = b.add(HttpDispatcher.insecureHttpFlow.timedAs("waContactRTT"))
    val waContactResponseFormatter = b.add(new WAContactResponseHandler().flow)

    dispatcherPrepFlow ~> httpCachedClient ~> waContactResponseFormatter

    FlowShape(dispatcherPrepFlow.in, waContactResponseFormatter.out)
  })

  def sink: Sink[FlowResponseStatus, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit b =>
    val metrics = b.add(new FlowMetrics[FlowResponseStatus]("wa.contact").flow)
    metrics ~> Sink.ignore
    SinkShape(metrics.in)
  })

  override def transformers: Map[CheckPointGroup, Flow[ContactPayloads, FlowResponseStatus, NotUsed]] = Map(Channel.WA.toString -> waContactTransformFlow)
}
