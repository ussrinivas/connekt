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
package com.flipkart.connekt.busybees.streams

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}
import com.flipkart.connekt.busybees.BusyBeesBoot
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{CallbackEvent, ConnektRequest}
import com.flipkart.connekt.commons.sync.SyncType.SyncType
import com.flipkart.connekt.commons.sync.{SyncDelegate, SyncManager, SyncType}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

case class GroupWiseConfig(topologyEnabled: AtomicBoolean, killSwitch: SharedKillSwitch)

trait ConnektTopology[E <: CallbackEvent] extends SyncDelegate {

  type CheckPointGroup = String

  def sources: Map[CheckPointGroup, Source[ConnektRequest, NotUsed]]

  def transformers: Map[CheckPointGroup, Flow[ConnektRequest, E, NotUsed]]

  def sink: Sink[E, NotUsed]

  def channelName: String

  implicit val system = BusyBeesBoot.system
  implicit val ec = BusyBeesBoot.system.dispatcher
  implicit val mat = BusyBeesBoot.mat
  val ioMat = BusyBeesBoot.ioMat
  val ioDispatcher = system.dispatchers.lookup("akka.actor.io-dispatcher")

  SyncManager.get().addObserver(this, List(SyncType.CLIENT_QUEUE_CREATE))
  SyncManager.get().addObserver(this, List(SyncType.EMAIL_TOPOLOGY_UPDATE))
  SyncManager.get().addObserver(this, List(SyncType.ANDROID_TOPOLOGY_UPDATE))
  SyncManager.get().addObserver(this, List(SyncType.IOS_TOPOLOGY_UPDATE))
  SyncManager.get().addObserver(this, List(SyncType.OPENWEB_TOPOLOGY_UPDATE))
  SyncManager.get().addObserver(this, List(SyncType.SMS_TOPOLOGY_UPDATE))

  private var killSwitches: Map[String, GroupWiseConfig] = Map.empty

  private var shutdownComplete: Future[Done] = _

  override def onUpdate(_type: SyncType, args: List[AnyRef]): Any = {
    _type match {
      case SyncType.CLIENT_QUEUE_CREATE => Try_ {
        if (args.head.toString.equals(channelName)) {
          ConnektLogger(LogFile.SERVICE).info(s"Topology Restart for CLIENT_QUEUE_CREATE Client: ${args(1)}, New Topic: ${args.last} ")
          restart
        }
      }
      case SyncType.ANDROID_TOPOLOGY_UPDATE | SyncType.IOS_TOPOLOGY_UPDATE | SyncType.OPENWEB_TOPOLOGY_UPDATE |
           SyncType.SMS_TOPOLOGY_UPDATE | SyncType.EMAIL_TOPOLOGY_UPDATE | SyncType.WINDOW_TOPOLOGY_UPDATE => Try_ {
        val topologyName = args.last.toString
        args.head.toString match {
          case "start" if killSwitches.get(topologyName).isDefined || killSwitches.isEmpty =>
            if (killSwitches.nonEmpty && killSwitches(topologyName).topologyEnabled.get()) {
              ConnektLogger(LogFile.SERVICE).info(s"${topologyName.toUpperCase} channel topology is already up.")
            } else {
              ConnektLogger(LogFile.SERVICE).info(s"${topologyName.toUpperCase} channel topology restarting.")
              run(mat)
              killSwitches(topologyName).topologyEnabled.set(true)
            }
          case "stop" if killSwitches.get(topologyName).isDefined || killSwitches.isEmpty =>
            if (killSwitches.nonEmpty && killSwitches(topologyName).topologyEnabled.get()) {
              ConnektLogger(LogFile.SERVICE).info(s"${topologyName.toUpperCase} channel topology shutting down.")
              killSwitches(topologyName).killSwitch.shutdown
              killSwitches(topologyName).topologyEnabled.set(false)
            } else {
              ConnektLogger(LogFile.SERVICE).info(s"${topologyName.toUpperCase} channel topology is already stopped.")
            }
          case _ => None
        }
      }
      case _ =>
    }
  }

  def graphs: List[RunnableGraph[NotUsed]] = {
    val sourcesMap = sources
    transformers.filterKeys(sourcesMap.contains).map { case (group, flow) =>
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

  def run(implicit dmat: Materializer): Unit = {
    ConnektLogger(LogFile.PROCESSORS).info(s"Starting Topology " + this.getClass.getSimpleName)
    graphs.foreach(_.run())
  }

  def shutdown(topology: String): Future[Done] = {
    ConnektLogger(LogFile.PROCESSORS).info(s"Shutting Down " + topology.toUpperCase)
    killSwitches(topology).killSwitch.shutdown()
    shutdownComplete.onSuccess { case _ => ConnektLogger(LogFile.PROCESSORS).info(s"Shutdown Complete" + this.getClass.getSimpleName) }
    Option(shutdownComplete).getOrElse(Future.successful(Done))
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
    run(mat)
  }
}
