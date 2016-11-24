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

import akka.NotUsed
import akka.event.Logging
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.stream.{ActorAttributes, Attributes, Materializer}
import com.flipkart.connekt.busybees.BusyBeesBoot
import com.flipkart.connekt.commons.iomodels.{CallbackEvent, ConnektRequest}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise

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

  val sourceSwitches: scala.collection.mutable.ListBuffer[Promise[String]] = ListBuffer()

  def graphs(): List[RunnableGraph[NotUsed]] = {
    val sourcesMap = sources
    transformers.filterKeys(sourcesMap.contains).map { case (group, flow) =>
      sourcesMap(group).withAttributes(ActorAttributes.dispatcher("akka.actor.default-pinned-dispatcher")).via(flow).to(sink)
        .withAttributes(Attributes.logLevels(onElement = Logging.InfoLevel, onFinish = Logging.InfoLevel, onFailure = Logging.ErrorLevel))
    }.toList
  }

  def run(implicit mat: Materializer) = graphs().foreach(_.run())

  def shutdown()

  def restart(implicit mat: Materializer): Unit = {
    shutdown()
    Thread.sleep(30 * 1000)
    run(mat)
  }
}
