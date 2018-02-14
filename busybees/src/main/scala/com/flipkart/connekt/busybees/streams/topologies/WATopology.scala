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
package com.flipkart.connekt.busybees.streams.topologies

import akka.NotUsed
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{GraphDSL, Merge, Source, _}
import akka.stream.{SourceShape, _}
import com.flipkart.connekt.busybees.streams.ConnektTopology
import com.flipkart.connekt.busybees.streams.flows.dispatchers.{HttpDispatcher, WAMediaDispatcher}
import com.flipkart.connekt.busybees.streams.flows.profilers.TimedFlowOps._
import com.flipkart.connekt.busybees.streams.flows.reponsehandlers.{WAMediaResponseHandler, WAResponseHandler}
import com.flipkart.connekt.busybees.streams.flows.transformers.WAProviderPrepare
import com.flipkart.connekt.busybees.streams.flows.{FlowMetrics, WATrackingFlow}
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.busybees.streams.topologies.WATopology._
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.sync.{SyncDelegate, SyncManager, SyncType}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.typesafe.config.Config

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContextExecutor

class WATopology(kafkaConsumerConfig: Config) extends ConnektTopology[WACallbackEvent] with SyncDelegate {

  override def channelName: String = Channel.WA.toString

  override def sources: Map[CheckPointGroup, Source[ConnektRequest, NotUsed]] = {
    val enabledTopology = ListBuffer[Channel.Channel]()
    SyncManager.getNodeData(SyncManager.getBucketNodePath + "/" + SyncType.WA_TOPOLOGY_UPDATE).map(_.message.head) match {
      case Some("stop") =>
        ConnektLogger(LogFile.SERVICE).info(s"WA Topology stopped by admin.")
      case _ =>
        enabledTopology += Channel.WA
    }
    enabledTopology.flatMap {value =>
      ServiceFactory.getMessageService(Channel.WA).getTopicNames(Channel.WA, None).get match {
        case platformTopics if platformTopics.nonEmpty => Option(value.toString -> createMergedSource(value, platformTopics))
        case _ => None
      }
    }.toMap
  }

  private def createMergedSource(checkpointGroup: CheckPointGroup, topics: Seq[String]): Source[ConnektRequest, NotUsed] = Source.fromGraph(GraphDSL.create() { implicit b =>

    val groupId = kafkaConsumerConfig.getString("group.id")
    ConnektLogger(LogFile.PROCESSORS).info(s"WA:: Creating composite source for topics: ${topics.toString()}")

    val merge = b.add(Merge[ConnektRequest](topics.size))

    for (portNum <- 0 until merge.n) {
      val consumerGroup = s"${groupId}_$checkpointGroup"
      new KafkaSource[ConnektRequest](kafkaConsumerConfig, topic = topics(portNum), consumerGroup) ~> merge.in(portNum)
    }

    SourceShape(merge.out)
  })

  override def sink: Sink[WACallbackEvent, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit b =>
    val metrics = b.add(new FlowMetrics[WACallbackEvent](Channel.WA).flow)
    metrics ~> Sink.ignore

    SinkShape(metrics.in)
  })


  override def transformers: Map[CheckPointGroup, Flow[ConnektRequest, WACallbackEvent, NotUsed]] = {
    Map(Channel.WA.toString -> waTransformFlow(ioMat,ioDispatcher))
  }
}

object WATopology {

  def waTransformFlow(implicit ioMat:ActorMaterializer, ioDispatcher:  ExecutionContextExecutor): Flow[ConnektRequest, WACallbackEvent, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b  =>

    /**
      * Whatsapp Topology
      *
      *                     +------------+      +-------------------+         +---------------------+     +-------------------------+             +--------+      +-------------------+      +-------------------+       +-----+
      *  ConnektRequest --> |  Tracking  | -->  |  MediaPartitioner | -+----> |  WAMediaDispatcher  | --> | WaMediaResponseHandler  |  --> +  --> | Merger | -->  | WAProviderPrepare | -->  | WAResponseHandler |  -->  | out |
      *                     +------------+      +-------------------+  |      +---------------------+     +-------------------------+      |      +--------+      +-------------------+      +-------------------+       +-----+
      *                                                                +-------------------------------------------------------------------+
      */

    val mediaPartitioner = b.add(Partition[ConnektRequest](2,
      _.channelData.asInstanceOf[WARequestData].attachment match {
        case Some(_:Attachment) => 0
        case _ => 1
      }))

    val waMediaDispatcher = b.add(new WAMediaDispatcher().flow)
    val waHttpPoolMediaFlow = b.add(HttpDispatcher.waPoolClientFlow.timedAs("waMediaRTT"))
    val waMediaResponseHandler = b.add(new WAMediaResponseHandler().flow)
    val merge = b.add(Merge[ConnektRequest](2))
    val waPrepare = b.add(new WAProviderPrepare().flow)

    val trackingParallelism = ConnektConfig.getInt("topology.wa.tracking.parallelism").getOrElse(5)
    val tracking = b.add(new WATrackingFlow(trackingParallelism)(ioDispatcher).flow)
    val waHttpPoolFlow = b.add(HttpDispatcher.waPoolClientFlow.timedAs("waRTT"))

    val waResponseHandler = b.add(new WAResponseHandler()(ioMat,ioDispatcher).flow)

    tracking ~> mediaPartitioner
                mediaPartitioner.out(0) ~> waMediaDispatcher ~> waHttpPoolMediaFlow ~> waMediaResponseHandler ~> merge.in(0)
                mediaPartitioner.out(1) ~>                                                                       merge.in(1)
                                                                                                                 merge.out ~> waPrepare ~> waHttpPoolFlow ~> waResponseHandler

    FlowShape(tracking.in, waResponseHandler.out)
  })

}
