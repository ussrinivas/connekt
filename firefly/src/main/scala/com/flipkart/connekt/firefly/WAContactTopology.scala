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

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches, ThrottleMode}
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.Contact
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.firefly.dispatcher.HttpDispatcher
import com.flipkart.connekt.firefly.flows.WAHttpDispatcherPrepare
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.concurrent.duration._

class WAContactTopology(kafkaConsumerConnConf: Config, topicName: String, kafkaGroupName: String)(implicit am: ActorMaterializer, sys: ActorSystem) {
  private implicit val ec = am.executionContext

  private val httpCachedClient = HttpDispatcher.httpFlow

  def start(): (Future[Done], KillSwitch) = {

    var streamCompleted: Future[Done] = null
    val killSwitch = KillSwitches.shared(UUID.randomUUID().toString)
    val waKafkaThrottle = ConnektConfig.getOrElse("wa.contact.throttle.rps", 2)

    val dispatcherPerpFlow = new WAHttpDispatcherPrepare().flow

    val waKafkaSource = new KafkaSource[Contact](kafkaConsumerConnConf, topicName, kafkaGroupName)
    val source = Source.fromGraph(waKafkaSource)
      .via(killSwitch.flow)
      .watchTermination() { case (_, completed) => streamCompleted = completed }
      .grouped(3)
      .throttle(waKafkaThrottle, 1.second, waKafkaThrottle, ThrottleMode.Shaping)
      .via(dispatcherPerpFlow)
      .via(httpCachedClient)
      // TODO :: Hbase sink
      .runWith(Sink.foreach(print(_)))

    ConnektLogger(LogFile.SERVICE).info(s"Started internal latency metric topology of topic $topicName")
    (streamCompleted, killSwitch)

  }
}
