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

trait ConnektTopology[E <: CallbackEvent] extends SyncDelegate{

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
  val isTopologyEnabled = new AtomicBoolean(true)

  SyncManager.get().addObserver(this, List(SyncType.CLIENT_QUEUE_CREATE))
  SyncManager.get().addObserver(this, List(SyncType.TOPOLOGY_UPDATE))

  protected var killSwitch: SharedKillSwitch = _
  private var shutdownComplete:Future[Done] = _

  override def onUpdate(_type: SyncType, args: List[AnyRef]): Any = {
    _type match {
      case SyncType.CLIENT_QUEUE_CREATE => Try_ {
        ConnektLogger(LogFile.SERVICE).info(s"Topology Restart for CLIENT_QUEUE_CREATE Client: ${args.head}, New Topic: ${args.last} ")
        restart
      }
      case SyncType.TOPOLOGY_UPDATE => Try_ {
        if (args.last.toString.equals(channelName)) {
          args.head.toString match {
            case "start" =>
              if (isTopologyEnabled.get()) {
                ConnektLogger(LogFile.SERVICE).info(s"${args.last.toString.toUpperCase} channel topology is already up.")
              } else {
                ConnektLogger(LogFile.SERVICE).info(s"${args.last.toString.toUpperCase} channel topology restarting.")
                run(mat)
                isTopologyEnabled.set(true)
              }
            case "stop" =>
              if (isTopologyEnabled.get()) {
                ConnektLogger(LogFile.SERVICE).info(s"${args.last.toString.toUpperCase} channel topology shutting down.")
                killSwitch.shutdown()
                isTopologyEnabled.set(false)
              } else {
                ConnektLogger(LogFile.SERVICE).info(s"${args.last.toString.toUpperCase} channel topology is already stopped.")
              }
          }
        }
      }
      case _ =>
    }
  }

  def graphs(): List[RunnableGraph[NotUsed]] = {
    isTopologyEnabled.set(true)
    val sourcesMap = sources
    killSwitch = KillSwitches.shared(UUID.randomUUID().toString)
    transformers.filterKeys(sourcesMap.contains).map { case (group, flow) =>
      sourcesMap(group)
        .via(killSwitch.flow)
        .withAttributes(ActorAttributes.dispatcher("akka.actor.default-pinned-dispatcher"))
        .via(flow)
        .watchTermination(){
          case (materializedValue, completed) =>
            shutdownComplete = completed
            materializedValue
        }
        .to(sink)
        .withAttributes(Attributes.logLevels(onElement = Logging.InfoLevel, onFinish = Logging.InfoLevel, onFailure = Logging.ErrorLevel))
    }.toList
  }

  def run(implicit mat: Materializer): Unit = graphs().foreach(_.run())

  def shutdown(): Future[Done] = {
    ConnektLogger(LogFile.PROCESSORS).info(s"Shutting Down " + this.getClass.getSimpleName)
    killSwitch.shutdown()
    shutdownComplete.onSuccess{ case _ => ConnektLogger(LogFile.PROCESSORS).info(s"Shutdown Complete" + this.getClass.getSimpleName)}
    Option(shutdownComplete).getOrElse(Future.successful(Done))
  }

  private val rand = new scala.util.Random

  def restart(implicit mat: Materializer): Unit = {
    //wait for a random time btwn 0 and 2 minutes.
    Thread.sleep(rand.nextInt(120) * 1000)
    val shutdownComplete = shutdown()
    Try_(Await.ready(shutdownComplete, 30.seconds))
    run(mat)
  }
}
