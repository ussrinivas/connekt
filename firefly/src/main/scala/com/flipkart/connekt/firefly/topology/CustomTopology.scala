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

import akka.{Done, NotUsed}
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import com.codahale.metrics.Gauge
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{TopologyInputDatatype, TopologyOutputDatatype}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.firefly.FireflyBoot
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

trait CustomTopology[I <: TopologyInputDatatype, E <: TopologyOutputDatatype] extends Instrumented {

  type CheckPointGroup = String

  def sources: Map[CheckPointGroup, Source[I, NotUsed]]

  def transformers: Map[CheckPointGroup, Flow[I, E, NotUsed]]

  def sink: Sink[E, NotUsed]

  implicit val system = FireflyBoot.system
  implicit val ec = FireflyBoot.system.dispatcher
  implicit val mat = FireflyBoot.mat
  val ioDispatcher = system.dispatchers.lookup("akka.actor.io-dispatcher")
  val ioMat = FireflyBoot.ioMat

  private val killSwitch = KillSwitches.shared(UUID.randomUUID().toString)
  private var shutdownComplete: Future[Done] = _

  def graphs(): List[RunnableGraph[NotUsed]] = {
    val sourcesMap = sources
    transformers.filterKeys(sourcesMap.contains).map { case (group, flow) =>
      sourcesMap(group).via(killSwitch.flow).withAttributes(ActorAttributes.dispatcher("akka.actor.default-pinned-dispatcher"))
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

  def run(implicit mat: Materializer): Unit = {
    ConnektLogger(LogFile.PROCESSORS).info(s"Starting Topology " + this.getClass.getSimpleName)
    registry.register(getMetricName("topology.status"), new Gauge[Int] {
      override def getValue: Int = {
        Option(shutdownComplete).map(tp => if (tp.isCompleted) 0 else 1).getOrElse(-1)
      }
    })
    graphs().foreach(_.run())
  }

  def shutdown(): Future[Done] = {
    ConnektLogger(LogFile.PROCESSORS).info(s"Shutting Down " + this.getClass.getSimpleName)
    killSwitch.shutdown()
    shutdownComplete.onSuccess { case _ => ConnektLogger(LogFile.PROCESSORS).info(s"Shutdown Complete" + this.getClass.getSimpleName) }
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
