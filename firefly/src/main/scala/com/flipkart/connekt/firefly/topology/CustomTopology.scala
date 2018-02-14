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

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import akka.{Done, NotUsed}
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import com.codahale.metrics.Gauge
import com.flipkart.connekt.busybees.streams.GroupWiseConfig
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{TopologyInputDatatype, TopologyOutputDatatype}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.sync.SyncType.SyncType
import com.flipkart.connekt.commons.sync.{SyncDelegate, SyncManager, SyncType}
import com.flipkart.connekt.firefly.FireflyBoot
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

trait CustomTopology[I <: TopologyInputDatatype, E <: TopologyOutputDatatype] extends Instrumented with SyncDelegate {

  type CheckPointGroup = String

  def sources: Map[CheckPointGroup, Source[I, NotUsed]]

  def transformers: Map[CheckPointGroup, Flow[I, E, NotUsed]]

  def sink: Sink[E, NotUsed]

  def topologyName: String

  implicit val system = FireflyBoot.system
  implicit val ec = FireflyBoot.system.dispatcher
  implicit val mat = FireflyBoot.mat
  val ioDispatcher = system.dispatchers.lookup("akka.actor.io-dispatcher")
  val ioMat = FireflyBoot.ioMat

  private var killSwitches: Map[String, GroupWiseConfig] = Map.empty
  private var shutdownComplete: Future[Done] = _

  SyncManager.get().addObserver(this, List(SyncType.WA_CONTACT_TOPOLOGY_UPDATE))
  SyncManager.get().addObserver(this, List(SyncType.WA_LATENCY_METER_TOPOLOGY_UPDATE))
  SyncManager.get().addObserver(this, List(SyncType.SMS_LATENCY_METER_TOPOLOGY_UPDATE))

  override def onUpdate(_type: SyncType, args: List[AnyRef]): Any = {
    _type match {
      case SyncType.WA_CONTACT_TOPOLOGY_UPDATE | SyncType.WA_LATENCY_METER_TOPOLOGY_UPDATE | SyncType.SMS_LATENCY_METER_TOPOLOGY_UPDATE => Try_ {
        val tName = args.last.toString
        args.head.toString match {
          case "start" if killSwitches.get(tName).isDefined =>
            if (killSwitches.nonEmpty && killSwitches(tName).topologyEnabled.get()) {
              ConnektLogger(LogFile.SERVICE).info(s"${tName.toUpperCase} channel topology is already up.")
            } else {
              ConnektLogger(LogFile.SERVICE).info(s"${tName.toUpperCase} channel topology restarting.")
              run()(mat)
              killSwitches(tName).topologyEnabled.set(true)
            }
          case "start" if topologyName.contains(tName) =>
            ConnektLogger(LogFile.SERVICE).info(s"${tName.toUpperCase} channel topology restarting.")
            run(Some(tName))(mat)
            killSwitches(tName).topologyEnabled.set(true)
          case "stop" if killSwitches.get(tName).isDefined =>
            if (killSwitches.nonEmpty && killSwitches(tName).topologyEnabled.get()) {
              ConnektLogger(LogFile.SERVICE).info(s"${tName.toUpperCase} channel topology shutting down.")
              killSwitches(tName).killSwitch.shutdown
              killSwitches(tName).topologyEnabled.set(false)
            } else {
              ConnektLogger(LogFile.SERVICE).info(s"${tName.toUpperCase} channel topology is already stopped.")
            }
          case _ => None
        }
      }
      case _ =>
    }
  }

  def graphs(topologyName: Option[String] = None): List[RunnableGraph[NotUsed]] = {
    val sourcesMap = sources
    val transformersModified = if (topologyName.isDefined) {
      Map(topologyName.get -> transformers(topologyName.get))
    } else {
      transformers
    }
    transformersModified.filterKeys(sourcesMap.contains).map { case (group, flow) =>
      val killSwitch = KillSwitches.shared(UUID.randomUUID().toString)
      killSwitches += (group -> GroupWiseConfig(new AtomicBoolean(true), killSwitch))
      sourcesMap(group)
        .via(killSwitch.flow)
        .withAttributes(ActorAttributes.dispatcher("akka.actor.default-pinned-dispatcher"))
        .via(flow)
        .watchTermination() {
          case (materializedValue, completed) =>
            shutdownComplete = completed
            materializedValue
        }
        .to(sink)
        .withAttributes(Attributes.logLevels(onElement = Logging.InfoLevel, onFinish = Logging.InfoLevel, onFailure = Logging.ErrorLevel))
    }.toList
  }

  def createMergedSource[In: ClassTag](checkpointGroup: CheckPointGroup, topics: Seq[String], kafkaConsumerConfig: Config): Source[In, NotUsed] = Source.fromGraph(GraphDSL.create() { implicit b =>
    val groupId = kafkaConsumerConfig.getString("group.id")
    ConnektLogger(LogFile.PROCESSORS).info(s"Creating composite source for topics: ${topics.toString()}")
    val merge = b.add(Merge[In](topics.size))
    for (portNum <- 0 until merge.n) {
      val consumerGroup = s"${groupId}_$checkpointGroup"
      new KafkaSource[In](kafkaConsumerConfig, topic = topics(portNum), consumerGroup) ~> merge.in(portNum)
    }
    SourceShape(merge.out)
  })

  def run(topologyName: Option[String] = None)(implicit dmat: Materializer): Unit = {
    ConnektLogger(LogFile.PROCESSORS).info(s"Starting Topology " + this.getClass.getSimpleName)
    try {
      registry.register(getMetricName("topology.status"), new Gauge[Int] {
        override def getValue: Int = {
          Option(shutdownComplete).map(tp => if (tp.isCompleted) 0 else 1).getOrElse(-1)
        }
      })
    } catch {
      case _: Exception =>
        ConnektLogger(LogFile.PROCESSORS).info(s"Registry: ${getMetricName("topology.status")} already exists.")
    }
    graphs(topologyName).foreach(_.run())
  }

  def shutdownAll(): Future[Done] = {
    ConnektLogger(LogFile.PROCESSORS).info(s"Shutting Down " + this.getClass.getSimpleName)
    killSwitches.foreach(_._2.killSwitch.shutdown())
    shutdownComplete.onSuccess { case _ => ConnektLogger(LogFile.PROCESSORS).info(s"Shutdown Complete" + this.getClass.getSimpleName) }
    Option(shutdownComplete).getOrElse(Future.successful(Done))
  }

  private val rand = new scala.util.Random

  def restart(implicit mat: Materializer): Unit = {
    //wait for a random time btwn 0 and 2 minutes.
    Thread.sleep(rand.nextInt(120) * 1000)
    val shutdownComplete = shutdownAll()
    Try_(Await.ready(shutdownComplete, 30.seconds))
    run()(mat)
  }

}
