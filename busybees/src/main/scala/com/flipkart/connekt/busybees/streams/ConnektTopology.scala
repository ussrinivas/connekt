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

import akka.{Done, NotUsed}
import akka.event.Logging
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.stream.{ActorAttributes, Attributes, KillSwitches, Materializer}
import com.flipkart.connekt.busybees.BusyBeesBoot
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{CallbackEvent, ConnektRequest}
import com.flipkart.connekt.commons.core.Wrappers._
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

trait ConnektTopology[E <: CallbackEvent] {

  type CheckPointGroup = String

  def sources: Map[CheckPointGroup, Source[ConnektRequest, NotUsed]]

  def transformers: Map[CheckPointGroup, Flow[ConnektRequest, E, NotUsed]]

  def sink: Sink[E, NotUsed]

  implicit val system = BusyBeesBoot.system
  implicit val ec = BusyBeesBoot.system.dispatcher
  implicit val mat = BusyBeesBoot.mat
  val ioMat = BusyBeesBoot.ioMat
  val ioDispatcher = system.dispatchers.lookup("akka.actor.io-dispatcher")

  private val killSwitch = KillSwitches.shared(UUID.randomUUID().toString)
  private var shutdownComplete:Future[Done] = _

  def graphs(): List[RunnableGraph[NotUsed]] = {
    val sourcesMap = sources
    transformers.filterKeys(sourcesMap.contains).map { case (group, flow) =>
      sourcesMap(group).via(killSwitch.flow).withAttributes(ActorAttributes.dispatcher("akka.actor.default-pinned-dispatcher"))
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

  def run(implicit mat: Materializer): Unit = {
    ConnektLogger(LogFile.PROCESSORS).info(s"Starting Topology " + this.getClass.getSimpleName)
    graphs().foreach(_.run())
  }

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
