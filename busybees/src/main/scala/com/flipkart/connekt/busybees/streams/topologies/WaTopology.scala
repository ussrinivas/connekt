package com.flipkart.connekt.busybees.streams.topologies

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.SourceShape
import akka.stream.scaladsl._
import akka.stream.scaladsl.{GraphDSL, Merge, Source}
import com.flipkart.connekt.busybees.models.SmsRequestTracker
import com.flipkart.connekt.busybees.streams.flows.profilers.TimedFlowOps._
import com.flipkart.connekt.busybees.streams.ConnektTopology
import com.flipkart.connekt.busybees.streams.flows.dispatchers.HttpDispatcher
import com.flipkart.connekt.busybees.streams.flows.formaters.SmsChannelFormatter
import com.flipkart.connekt.busybees.streams.flows.{ChooseProvider, FlowMetrics, RenderFlow, SMSTrackingFlow}
import com.flipkart.connekt.busybees.streams.flows.reponsehandlers.{SmsResponseHandler, WaResponseHandler}
import com.flipkart.connekt.busybees.streams.flows.transformers.{SmsProviderPrepare, SmsProviderResponseFormatter, WaProviderPrepare}
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.core.Wrappers.Try_
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.busybees.streams.topologies.WaTopology._
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, SmsCallbackEvent, SmsPayloadEnvelope, WACallbackEvent}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.streams.FirewallRequestTransformer
import com.flipkart.connekt.commons.sync.{SyncDelegate, SyncType}
import com.flipkart.connekt.commons.sync.SyncType.SyncType
import com.typesafe.config.Config

import scala.concurrent.ExecutionContextExecutor

/**
  * Created by saurabh.mimani on 14/11/17.
  */
class WaTopology(kafkaConsumerConfig: Config) extends ConnektTopology[WACallbackEvent] with SyncDelegate {

  override def onUpdate(_type: SyncType, args: List[AnyRef]): Any = {
    _type match {
      case SyncType.CLIENT_QUEUE_CREATE => Try_ {
        ConnektLogger(LogFile.SERVICE).info(s"Busybees Restart for CLIENT_QUEUE_CREATE Client: ${args.head}, New Topic: ${args.last} ")
        restart
      }
      case _ =>
    }
  }


  override def sources: Map[CheckPointGroup, Source[ConnektRequest, NotUsed]] = {
    List(Channel.WA).flatMap {value =>
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

object WaTopology {

  private val firewallStencilId: Option[String] = ConnektConfig.getString("sys.firewall.stencil.id")

  def waTransformFlow(implicit ioMat:ActorMaterializer, ioDispatcher:  ExecutionContextExecutor): Flow[ConnektRequest, SmsCallbackEvent, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b  =>

    /**
      * Whatsapp Topology
      *
      *                     +-------------------+        +----------+     +-----------------+      +-----------------------+     +---------------------+     +----------------+     +----------------------------+     +---------------------+     +-------------------------+      +--..
      *  ConnektRequest --> |SmsChannelFormatter| |----> |  Merger  | --> | ChooseProvider  |  --> | SeparateIntlReceivers | --> |  SmsProviderPrepare | --> |  SmsDispatcher | --> |SmsProviderResponseFormatter| --> |  SmsResponseHandler | --> |Response / Error Splitter| -+-> |Merger
      *                     +-------------------+ |      +----------+     +-----------------+      +-----------------------+     +---------------------+     +----------------+     +----------------------------+     +---------------------+     +-------------------------+  |   +-----
      *                                           +-------------------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------+
      */

    val render = b.add(new RenderFlow().flow)
    val trackSmsParallelism = ConnektConfig.getInt("topology.sms.tracking.parallelism").get
    val tracking = b.add(new SMSTrackingFlow(trackSmsParallelism)(ioDispatcher).flow)
    val waPrepare = b.add(new WaProviderPrepare().flow)

    val waHttpPoolFlow = b.add(HttpDispatcher.waPoolClientFlow.timedAs("waRTT"))

    val providerHandlerParallelism = ConnektConfig.getInt("topology.sms.parse.parallelism").get
//    val smsResponseFormatter = b.add(new SmsProviderResponseFormatter(providerHandlerParallelism)(ioMat,ioDispatcher).flow)
    val waResponseHandler = b.add(new WaResponseHandler(providerHandlerParallelism)(ioMat,ioDispatcher).flow)

    val smsRetryPartition = b.add(new Partition[Either[SmsRequestTracker, SmsCallbackEvent]](2, {
      case Right(_) => 0
      case Left(_) => 1
    }))

//    render.out ~> tracking ~> fmtSMS ~> smsPayloadMerge
//    smsPayloadMerge.out ~> chooseProvider ~> smsPrepare ~> firewallTransformer ~> smsHttpPoolFlow ~> smsResponseFormatter ~> smsResponseHandler ~> smsRetryPartition.in
//    smsPayloadMerge.preferred <~ smsRetryMapper <~ smsRetryPartition.out(1).map(_.left.get).outlet

    FlowShape(render.in, smsRetryPartition.out(0).map(_.right.get).outlet)
  })


}
