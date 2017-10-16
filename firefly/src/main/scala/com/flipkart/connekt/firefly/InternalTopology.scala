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
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches, ThrottleMode}
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.iomodels.CallbackEvent
import com.typesafe.config.Config
import akka.stream.scaladsl.Source
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.firefly.sinks.metrics.LatencyMetrics

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by grishma.s on 12/10/17.
  */
class InternalTopology(kafkaConsumerConnConf: Config, topicName: String, kafkaGroupName: String)(implicit am: ActorMaterializer, sys: ActorSystem) {
  private implicit val ec = am.executionContext

  def start(): (Future[Done], KillSwitch) = {

    var streamCompleted:Future[Done] = null
    val killSwitch = KillSwitches.shared(UUID.randomUUID().toString)
    val smsKafkaThrottle = ConnektConfig.getOrElse("sms.kafka.throttle.rps", 10)

    val kafkaCallbackSource = new KafkaSource[CallbackEvent](kafkaConsumerConnConf, topicName, kafkaGroupName)
    val source = Source.fromGraph(kafkaCallbackSource)
      .via(killSwitch.flow)
      .watchTermination(){ case (_, completed) => streamCompleted = completed}
      .throttle(smsKafkaThrottle, 1.second, smsKafkaThrottle, ThrottleMode.Shaping)
      .runWith(new LatencyMetrics().sink)

    ConnektLogger(LogFile.SERVICE).info(s"Started internal latency metric topology of topic ckt_callback_events_sms")
    (streamCompleted , killSwitch)

  }
}
