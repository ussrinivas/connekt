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
import akka.stream.{ActorMaterializer, KillSwitch}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.sync.SyncDelegate
import com.flipkart.connekt.commons.sync.SyncType.SyncType
import com.typesafe.config.Config


class WAContactTopologyManager(kafkaConsumerConnConf: Config)(implicit am: ActorMaterializer, sys: ActorSystem) extends SyncDelegate {

  private val triggers = scala.collection.mutable.Map[String, KillSwitch]()

  private lazy val WA_TOPIC = ConnektConfig.get("firefly.wa.contact.kafka.topic").getOrElse("WA_CONTACT")
  private val CONSUMER_NAME = "WA_CONTACT_CONSUMER"

  private val kafkaGroupNames: List[Map[String, String]] = List(
    Map("name" -> CONSUMER_NAME, "topic" -> WA_TOPIC)
  )

  private def startTopology(topicName: String, kafkaGroupName: String): Unit = {
    val (streamComplete, killSwitch) = new WAContactTopology(kafkaConsumerConnConf, topicName, kafkaGroupName).start()
    triggers += kafkaGroupName -> killSwitch
    streamComplete.onComplete(_ => triggers -= kafkaGroupName)(am.executionContext)
  }

  def stopAllTopologies() = {
    triggers.foreach {
      case (kafkaGroupName, killSwitch) =>
        ConnektLogger(LogFile.SERVICE).info(s"Stopping internal topology $kafkaGroupName on firefly shutdown")
        killSwitch.shutdown()
    }
  }

  override def onUpdate(syncType: SyncType, args: List[AnyRef]): Any = {}

  def restoreState(): Unit = {
    kafkaGroupNames.foreach(kafkaGroupName => {
      val topicName: String = kafkaGroupName("topic")
      val name: String = kafkaGroupName("name")
      startTopology(topicName, name)
    })
  }
}

private object WAContactTopologyManager {
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
