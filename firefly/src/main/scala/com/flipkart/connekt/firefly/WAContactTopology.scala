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
package com.flipkart.connekt.firefly

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source}
import akka.{Done, NotUsed}
import com.flipkart.connekt.busybees.streams.flows.FlowMetrics
import com.flipkart.connekt.busybees.streams.flows.profilers.TimedFlowOps._
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.ContactPayload
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.firefly.dispatcher.HttpDispatcher
import com.flipkart.connekt.firefly.flows.dispatchers.WAContactHttpDispatcherPrepare
import com.flipkart.connekt.firefly.flows.responsehandlers.{WAContactResponseHandler, WAContactResponseStatus}
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.concurrent.duration._

class WAContactTopology(kafkaConsumerConnConf: Config, topicName: String, kafkaGroupName: String)(implicit am: ActorMaterializer, sys: ActorSystem) {
  private implicit val ec = am.executionContext

  private val waContactSize: Int = ConnektConfig.getInt("wa.contact.batch.size").getOrElse(1000)
  private val waContactTimeLimit: Int = ConnektConfig.getInt("wa.contact.wait.time.limit.sec").getOrElse(2)

  def start(): (Future[Done], KillSwitch) = {

    var streamCompleted: Future[Done] = null
    val killSwitch = KillSwitches.shared(UUID.randomUUID().toString)
    val waKafkaThrottle = ConnektConfig.getOrElse("wa.contact.throttle.rps", 2)

    val waKafkaSource = new KafkaSource[ContactPayload](kafkaConsumerConnConf, topicName, kafkaGroupName)

    val source = Source.fromGraph(waKafkaSource)
      .via(killSwitch.flow)
      .groupedWithin(waContactSize, waContactTimeLimit.second)
      .throttle(waKafkaThrottle, 1.second, waKafkaThrottle, ThrottleMode.Shaping)
      .via(waContactTransformFlow)
      .watchTermination() {
        case (materializedValue, completed) =>
          streamCompleted = completed
          materializedValue
      }
      .to(sink)

    source.run()

    ConnektLogger(LogFile.SERVICE).info(s"Started WAContactTopology for topic $topicName")
    (streamCompleted, killSwitch)
  }

  def waContactTransformFlow: Flow[Seq[ContactPayload], WAContactResponseStatus, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b =>

    val dispatcherPrepFlow = b.add(new WAContactHttpDispatcherPrepare().flow)
    val httpCachedClient = b.add(HttpDispatcher.insecureHttpFlow.timedAs("waContactRTT"))
    val waContactResponseFormatter = b.add(new WAContactResponseHandler().flow)

    dispatcherPrepFlow ~> httpCachedClient ~> waContactResponseFormatter

    FlowShape(dispatcherPrepFlow.in, waContactResponseFormatter.out)
  })


  def sink: Sink[WAContactResponseStatus, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit b =>
    val metrics = b.add(new FlowMetrics[WAContactResponseStatus]("wa.contact").flow)
    metrics ~> Sink.ignore
    SinkShape(metrics.in)
  })

}
