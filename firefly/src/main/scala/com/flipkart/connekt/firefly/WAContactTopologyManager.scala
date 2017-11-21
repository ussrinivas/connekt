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
import com.flipkart.connekt.commons.services.ConnektConfig
import com.typesafe.config.Config

class WAContactTopologyManager(kafkaConsumerConnConf: Config)(implicit am: ActorMaterializer, sys: ActorSystem) extends TopologyManager {

  private lazy val WA_TOPIC = ConnektConfig.get("firefly.wa.contact.kafka.topic").get
  private val CONSUMER_NAME = "WA_CONTACT_CONSUMER"

  kafkaGroupNames = List(
    Map("name" -> CONSUMER_NAME, "topic" -> WA_TOPIC)
  )

  override def startTopology(topicName: String, kafkaGroupName: String): Unit = {
    val (streamComplete, killSwitch) = new WAContactTopology(kafkaConsumerConnConf, topicName, kafkaGroupName).start()
    triggers += kafkaGroupName -> killSwitch
    streamComplete.onComplete(_ => triggers -= kafkaGroupName)(am.executionContext)
  }

}

object WAContactTopologyManager {
  var instance: WAContactTopologyManager = _

  def apply(kafkaConsumerConnConf: Config)(implicit am: ActorMaterializer, sys: ActorSystem): WAContactTopologyManager = {
    if (null == instance)
      this.synchronized {
        instance = new WAContactTopologyManager(kafkaConsumerConnConf)(am, sys)
        instance.restoreState()
      }
    instance
  }
}
