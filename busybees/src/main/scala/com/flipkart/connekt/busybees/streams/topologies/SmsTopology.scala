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
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl._
import com.flipkart.connekt.busybees.BusyBeesBoot
import com.flipkart.connekt.busybees.streams.ConnektTopology
import com.flipkart.connekt.busybees.streams.flows.dispatchers._
import com.flipkart.connekt.busybees.streams.flows.eventcreators.SMSBigfootEventCreator
import com.flipkart.connekt.busybees.streams.flows.formaters._
import com.flipkart.connekt.busybees.streams.flows.profilers.TimedFlowOps._
import com.flipkart.connekt.busybees.streams.flows.reponsehandlers._
import com.flipkart.connekt.busybees.streams.flows.transformers.SmsProviderPrepare
import com.flipkart.connekt.busybees.streams.flows.{FlowMetrics, RenderFlow}
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.sync.SyncType.SyncType
import com.flipkart.connekt.commons.sync.{SyncDelegate, SyncManager, SyncType}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.typesafe.config.Config

import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise

class SmsTopology(kafkaConsumerConfig: Config) extends ConnektTopology[SMSCallbackEvent] with SyncDelegate {

  SyncManager.get().addObserver(this, List(SyncType.CLIENT_QUEUE_CREATE))

  implicit val system = BusyBeesBoot.system
  implicit val ec = BusyBeesBoot.system.dispatcher
  implicit val mat = BusyBeesBoot.mat
  val ioMat = BusyBeesBoot.ioMat

  val ioDispatcher = system.dispatchers.lookup("akka.actor.io-dispatcher")

  val sourceSwitches: scala.collection.mutable.ListBuffer[Promise[String]] = ListBuffer()

  private def createMergedSource(checkpointGroup: CheckPointGroup, topics: Seq[String]): Source[ConnektRequest, NotUsed] = Source.fromGraph(GraphDSL.create() { implicit b =>

    val groupId = kafkaConsumerConfig.getString("group.id")
    ConnektLogger(LogFile.PROCESSORS).info(s"Creating composite source for topics: ${topics.toString()}")

    val merge = b.add(Merge[ConnektRequest](topics.size))

    for (portNum <- 0 until merge.n) {
      val p = Promise[String]()
      val consumerGroup = s"${groupId}_$checkpointGroup"
      new KafkaSource[ConnektRequest](kafkaConsumerConfig, topic = topics(portNum), consumerGroup)(p.future) ~> merge.in(portNum)
      sourceSwitches += p
    }

    SourceShape(merge.out)
  })

  override def sources: Map[CheckPointGroup, Source[ConnektRequest, NotUsed]] = {
    List( Channel.SMS).flatMap { platform =>
      ServiceFactory.getSMSMessageService.getTopicNames(Channel.SMS, Option("trans")).get match {
        case platformTopics if platformTopics.nonEmpty => Option(platform.toString -> createMergedSource(platform, platformTopics))
        case _ => None
      }
    }.toMap
  }

  def smsTransformFlow = Flow.fromGraph(GraphDSL.create() { implicit b =>

    /**
      * IOS Topology
      * +---------------+     +----------------+      +------------------+      +-----------------+     +-------------------+      +--..
      * ConnektRequest ---> |   IOSFilter   | --> |  IOSFormatter  | ---> |APNSRequestPrepare|  --> | APNSDispatcher  | --> |APNSResponseHandler| ---> |Merger
      * +---------------+     +----------------+      +------------------+      +-----------------+     +-------------------+      +-------
      */

    val render = b.add(new RenderFlow().flow)
    val fmtSMSParallelism = ConnektConfig.getInt("topology.push.openwebFormatter.parallelism").get
    val smsFilter = b.add(Flow[ConnektRequest].filter(_.channelInfo.isInstanceOf[SmsRequestInfo]))
    val fmtSMS = b.add(new SmsChannelFormatter(fmtSMSParallelism)(ioDispatcher).flow)
    val smsPrepare = b.add(new SmsProviderPrepare().flow)
    val smsHttpPoolFlow = b.add(HttpDispatcher.smsPoolClientFlow.timedAs("smsRTT"))
    val smsResponseHandle = b.add(new SMSResponseHandler()(ioMat, ioDispatcher).flow)

    render.out ~> smsFilter ~> fmtSMS ~> smsPrepare ~> smsHttpPoolFlow ~> smsResponseHandle

    FlowShape(render.in, smsResponseHandle.out)
  })


  override def sink: Sink[SMSCallbackEvent, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit b =>

    /**
     * Sink Topology
     *
     *                        +---------------------+     +-----------------+          ...-----+
     *   PNCallbackEvent ---> | BigfootEventCreator | --> | MetricsRecorder | --->  IgnoreSink |
     *                        +---------------------+     +-----------------+      +-----------+
     */

    val evtCreator = b.add(new SMSBigfootEventCreator)
    val metrics = b.add(new FlowMetrics[fkint.mp.connekt.SMSCallbackEvent](Channel.PUSH, Channel.SMS).flow)
    evtCreator.out ~> metrics ~> Sink.ignore
    val y = SinkShape(evtCreator.in)
    y
  })

  override def shutdown() = {
    /* terminate in top-down approach from all Source(s) */
    sourceSwitches.foreach(_.success("SmsTopology signal source shutdown"))
  }

  override def onUpdate(_type: SyncType, args: List[AnyRef]): Any = {
    _type match {
      case SyncType.CLIENT_QUEUE_CREATE => Try_ {
        ConnektLogger(LogFile.SERVICE).info(s"Busybees Restart for CLIENT_QUEUE_CREATE Client: ${args.head}, New Topic: ${args.last} ")
        restart
      }
      case _ =>
    }
  }

  override def transformers: Map[CheckPointGroup, Flow[ConnektRequest, SMSCallbackEvent, NotUsed]] = {
    Map(Channel.SMS.toString -> smsTransformFlow)
  }
}
