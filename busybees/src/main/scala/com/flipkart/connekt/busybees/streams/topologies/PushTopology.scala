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

import java.util.Random

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl._
import com.flipkart.connekt.busybees.BusyBeesBoot
import com.flipkart.connekt.busybees.models.WNSRequestTracker
import com.flipkart.connekt.busybees.streams.ConnektTopology
import com.flipkart.connekt.busybees.streams.flows.dispatchers._
import com.flipkart.connekt.busybees.streams.flows.eventcreators.PNBigfootEventCreator
import com.flipkart.connekt.busybees.streams.flows.formaters._
import com.flipkart.connekt.busybees.streams.flows.profilers.TimedFlowOps._
import com.flipkart.connekt.busybees.streams.flows.reponsehandlers._
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

object AndroidProtocols extends Enumeration {
  val http, xmpp = Value
}

class PushTopology(kafkaConsumerConfig: Config) extends ConnektTopology[PNCallbackEvent] with SyncDelegate {

  SyncManager.get().addObserver(this, List(SyncType.CLIENT_QUEUE_CREATE))

  implicit val system = BusyBeesBoot.system
  implicit val ec = BusyBeesBoot.system.dispatcher
  implicit val mat = BusyBeesBoot.mat
  val ioMat = BusyBeesBoot.ioMat

  val ioDispatcher = system.dispatchers.lookup("akka.actor.io-dispatcher")

  var sourceSwitches: List[Promise[String]] = _

  override def source: Source[ConnektRequest, NotUsed] = Source.fromGraph(GraphDSL.create() { implicit b =>
    val topics = ServiceFactory.getPNMessageService.getTopicNames(Channel.PUSH).get
    val groupId = kafkaConsumerConfig.getString("group.id")

    ConnektLogger(LogFile.PROCESSORS).info(s"Creating composite source for topics: ${topics.toString()}")

    val merge = b.add(Merge[ConnektRequest](topics.size))
    val handles = ListBuffer[Promise[String]]()

    for (portNum <- 0 until merge.n) {
      val p = Promise[String]()
      new KafkaSource[ConnektRequest](kafkaConsumerConfig, topic = topics(portNum), groupId)(p.future) ~> merge.in(portNum)
      handles += p
    }

    sourceSwitches = handles.toList

    SourceShape(merge.out)
  })

  override def transform = Flow.fromGraph(GraphDSL.create() { implicit b =>

    val render = b.add(new RenderFlow().flow)
    val merger = b.add(Merge[PNCallbackEvent](4))

    val platformPartition = b.add(new Partition[ConnektRequest](4, {
      case ios if "ios".equals(ios.channelInfo.asInstanceOf[PNRequestInfo].platform.toLowerCase) =>
        ConnektLogger(LogFile.PROCESSORS).debug(s"routing ios message: ${ios.id}")
        0
      case android if "android".equals(android.channelInfo.asInstanceOf[PNRequestInfo].platform.toLowerCase) =>
        ConnektLogger(LogFile.PROCESSORS).debug(s"routing android message: ${android.id}")
        1
      case windows if "windows".equals(windows.channelInfo.asInstanceOf[PNRequestInfo].platform.toLowerCase) =>
        ConnektLogger(LogFile.PROCESSORS).debug(s"routing windows message: ${windows.id}")
        2
      case openweb if "openweb".equals(openweb.channelInfo.asInstanceOf[PNRequestInfo].platform.toLowerCase) =>
        ConnektLogger(LogFile.PROCESSORS).debug(s"routing openweb message: ${openweb.id}")
        3
    }))

    render.out ~> platformPartition.in


    /**
     * Apple Topology
     *
     * iosRequest ~> iosFormatter ~> apnsDispatcher
     */

    val fmtIOSParallelism = ConnektConfig.getInt("topology.push.iosFormatter.parallelism").get
    val apnsDispatcherParallelism = ConnektConfig.getInt("topology.push.apnsDispatcher.parallelism").getOrElse(1024)
    val fmtIOS = b.add(new IOSChannelFormatter(fmtIOSParallelism)(ioDispatcher).flow)
    val apnsPrepare = b.add(new APNSDispatcherPrepare().flow)
    val apnsDispatcher = b.add(new APNSDispatcher(apnsDispatcherParallelism)(ioDispatcher).flow.timedAs("apnsRTT"))
    val apnsResponseHandle = b.add(new APNSResponseHandler().flow)

    platformPartition.out(0) ~> fmtIOS ~> apnsPrepare ~> apnsDispatcher ~> apnsResponseHandle ~> merger.in(0)

    platformPartition.out(1) ~> androidTopology ~> merger.in(1)

    /**
     * Windows Topology
     *
     *                                          |----------|                                      |-----------|     |-----------|
     *                                          |          |                                      |   WNS     | ~>  |   Retry   | ~>  out-merger
     * windows request -> windows-formatter  -> |    wns   |  ~> wnsHttpPrepare ~> wnsPoolFlow ~> |  RESPONSE |     | Partition |
     *                                          | Payload  |                                      |  HANDLER  |     |           |
     *                                      |~~>|   Merge  |                                      |           |     |           |~~>|
     *                                      |   |----------|                                      |-----------|     |-----------|   |
     *                                      |                                                                                       |
     *                                      |~~~~~~~~~~~~~~~~~~~~~~~~~~~  wnsRetryMapper <~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
     */

    val wnsHttpPrepare = b.add(new WNSDispatcherPrepare().flow)
    val wnsPoolFlow = b.add(HttpDispatcher.wnsPoolClientFlow.timedAs("wnsRTT"))

    val wnsPayloadMerge = b.add(MergePreferred[WNSPayloadEnvelope](1))
    val wnsRetryMapper = b.add(Flow[WNSRequestTracker].map(_.request) /*.buffer(10, OverflowStrategy.backpressure)*/)

    val fmtWindowsParallelism = ConnektConfig.getInt("topology.push.windowsFormatter.parallelism").get
    val fmtWindows = b.add(new WindowsChannelFormatter(fmtWindowsParallelism)(ioDispatcher).flow)

    val wnsRHandler = b.add(new WNSResponseHandler()(ioMat, ec).flow)

    val wnsRetryPartition = b.add(new Partition[Either[WNSRequestTracker, PNCallbackEvent]](2, {
      case Right(_) => 0
      case Left(_) => 1
    }))

    platformPartition.out(2) ~>  fmtWindows ~>  wnsPayloadMerge
                                                wnsPayloadMerge.out ~> wnsHttpPrepare  ~> wnsPoolFlow ~> wnsRHandler ~> wnsRetryPartition.in
                                                                                                                        wnsRetryPartition.out(0).map(_.right.get) ~> merger.in(2)
                                                wnsPayloadMerge.preferred <~ wnsRetryMapper <~ wnsRetryPartition.out(1).map(_.left.get).outlet
    /**
     * OpenWeb Topology
     */
    val fmtOpenWebParallelism = ConnektConfig.getInt("topology.push.openwebFormatter.parallelism").get
    val fmtOpenWeb = b.add(new OpenWebChannelFormatter(fmtOpenWebParallelism)(ioDispatcher).flow)

    //providers [standard]
    val openWebHttpPrepare = b.add(new OpenWebDispatcherPrepare().flow)
    val openWebPoolFlow = b.add(HttpDispatcher.openWebPoolClientFlow.timedAs("openWebRTT"))
    val openWebResponseHandle = b.add(new OpenWebResponseHandler().flow)

    platformPartition.out(3) ~> fmtOpenWeb ~> openWebHttpPrepare ~> openWebPoolFlow ~> openWebResponseHandle ~> merger.in(3)

    FlowShape(render.in, merger.out)
  })

  val androidTopology = if ( xmppShare == 100) xmppOnlyTopology else if ( xmppShare == 0 ) httpOnlyTopology else combinedTopology

  lazy val httpOnlyTopology = GraphDSL.create() {
    implicit b ⇒
      val fmtAndroidParallelism = ConnektConfig.getInt("topology.push.androidFormatter.parallelism").get
      val fmtAndroid = b.add(new AndroidHttpChannelFormatter(fmtAndroidParallelism)(ioDispatcher).flow)
      val gcmHttpPrepare = b.add(new GCMHttpDispatcherPrepare().flow)

      val gcmPoolFlow = b.add(HttpDispatcher.gcmPoolClientFlow.timedAs("gcmRTT"))

      val gcmResponseHandle = b.add(new GCMResponseHandler()(ioMat, ioDispatcher).flow)

      fmtAndroid ~> gcmHttpPrepare ~> gcmPoolFlow ~> gcmResponseHandle
      FlowShape(fmtAndroid.in, gcmResponseHandle.out)
  }

  lazy val xmppOnlyTopology = GraphDSL.create() {
    implicit b ⇒
      val fmtAndroidParallelism = ConnektConfig.getInt("topology.push.androidFormatter.parallelism").get
      val fmtAndroid = b.add(new AndroidXmppChannelFormatter(fmtAndroidParallelism)(ioDispatcher).flow)
      val gcmXmppPrepare = b.add(new GCMXmppDispatcherPrepare().flow)
      val gcmXmppPoolFlow = b.add(new GcmXmppDispatcher)
      val downstreamHandler = b.add(new XmppDownstreamHandler()(ioMat, ioDispatcher).flow)
      val upstreamHandler = b.add(new XmppUpstreamHandler()(ioMat, ioDispatcher).flow)
      val merger = b.add(Merge[PNCallbackEvent](2))

      fmtAndroid ~> gcmXmppPrepare ~> gcmXmppPoolFlow.in
                                gcmXmppPoolFlow.out0 ~> downstreamHandler ~> merger.in(0)
                                gcmXmppPoolFlow.out1 ~> upstreamHandler ~> merger.in(1)
      FlowShape(fmtAndroid.in, merger.out)
  }

  lazy val xmppShare:Float = ConnektConfig.get("android.protocol.xmppshare").getOrElse("100").toFloat

  def chooseProtocol = if ( randomGenerator.nextInt(100) < xmppShare ) AndroidProtocols.xmpp else AndroidProtocols.http

  lazy val randomGenerator = new Random()

  lazy val combinedTopology = GraphDSL.create() {
    implicit b ⇒
      val platformPartition = b.add(new Partition[ConnektRequest](2, { _ =>
        chooseProtocol match {
          case AndroidProtocols.xmpp => 0
          case AndroidProtocols.http => 1
        }
      }))
      val merger = b.add(Merge[PNCallbackEvent](2))
      platformPartition.out(0) ~> xmppOnlyTopology ~> merger.in(0)
      platformPartition.out(1) ~> httpOnlyTopology ~> merger.in(1)

      FlowShape(platformPartition.in, merger.out)
  }

  override def sink: Sink[PNCallbackEvent, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit b =>

    val evtCreator = b.add(new PNBigfootEventCreator)
    val metrics = b.add(new FlowMetrics[fkint.mp.connekt.PNCallbackEvent](Channel.PUSH).flow)
    evtCreator.out ~> metrics ~> Sink.ignore

    SinkShape(evtCreator.in)
  })

  override def shutdown() = {
    /* terminate in top-down approach from all Source(s) */
    sourceSwitches.foreach(_.success("PushTopology signal source shutdown"))
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
}
