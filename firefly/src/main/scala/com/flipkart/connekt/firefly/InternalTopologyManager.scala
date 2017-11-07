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
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.sync.SyncType.SyncType
import com.flipkart.connekt.commons.sync.{SyncDelegate, SyncManager, SyncType}
import com.typesafe.config.Config


class InternalTopologyManager(kafkaConsumerConnConf: Config)(implicit am: ActorMaterializer, sys: ActorSystem) extends SyncDelegate {

  private val triggers = scala.collection.mutable.Map[String, KillSwitch]()

  private lazy val CALLBACK_QUEUE_NAME = ConnektConfig.get("firefly.latency.metric.kafka.topic").getOrElse("ckt_callback_events_%s")
  private val LATENCY_METRIC_NAME = "LatencyMetrics"

  private val kafkaGroupNames: List[Map[String, String]] = List(
    Map("name" -> Channel.SMS.toString.toUpperCase.concat(LATENCY_METRIC_NAME),
      "topic" -> CALLBACK_QUEUE_NAME.format(Channel.SMS.toString.toLowerCase))
  )

  private def startTopology(topicName: String, kafkaGroupName: String): Unit = {
    val (streamComplete, killSwitch) = new InternalTopology(kafkaConsumerConnConf, topicName, kafkaGroupName).start()
    triggers += kafkaGroupName -> killSwitch
    streamComplete.onComplete( _ =>  triggers -= kafkaGroupName)(am.executionContext)
  }

  def stopAllTopologies() = {
    triggers.foreach {
      case (kafkaGroupName, killSwitch) =>
        ConnektLogger(LogFile.SERVICE).info(s"Stopping internal topology $kafkaGroupName on firefly shutdown")
        killSwitch.shutdown()
    }
  }

  override def onUpdate(syncType: SyncType, args: List[AnyRef]): Any = {}

  def restoreState(): Unit ={
    kafkaGroupNames.foreach(kafkaGroupName => {
      val topicName: String = kafkaGroupName("topic")
      val name: String = kafkaGroupName("name")
      startTopology(topicName, name)
    })
  }
}

private object InternalTopologyManager {
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
