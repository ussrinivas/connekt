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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.services.ConnektConfig
import com.typesafe.config.Config

class InternalTopologyManager(kafkaConsumerConnConf: Config)(implicit am: ActorMaterializer, sys: ActorSystem) extends TopologyManager {

  private lazy val CALLBACK_QUEUE_NAME = ConnektConfig.get("firefly.latency.metric.kafka.topic").getOrElse("ckt_callback_events_%s")
  private val LATENCY_METRIC_NAME = "LatencyMetrics"

  kafkaGroupNames = List(
    Map("name" -> Channel.SMS.toString.toUpperCase.concat(LATENCY_METRIC_NAME),
      "topic" -> CALLBACK_QUEUE_NAME.format(Channel.SMS.toString.toLowerCase))
  )

  override def startTopology(topicName: String, kafkaGroupName: String): Unit = {
    val (streamComplete, killSwitch) = new InternalTopology(kafkaConsumerConnConf, topicName, kafkaGroupName).start()
    triggers += kafkaGroupName -> killSwitch
    streamComplete.onComplete(_ => triggers -= kafkaGroupName)(am.executionContext)
  }

}

object InternalTopologyManager {
  var instance: InternalTopologyManager = _

  def apply(kafkaConsumerConnConf: Config)(implicit am: ActorMaterializer, sys: ActorSystem): InternalTopologyManager = {
    if (null == instance)
      this.synchronized {
        instance = new InternalTopologyManager(kafkaConsumerConnConf)(am, sys)
        instance.restoreState()
      }
    instance
  }
}
