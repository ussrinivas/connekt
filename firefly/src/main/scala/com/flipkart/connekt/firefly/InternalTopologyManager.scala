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
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.sync.SyncType.SyncType
import com.flipkart.connekt.commons.sync.SyncDelegate
import com.typesafe.config.Config

/**
  * Created by grishma.s on 13/10/17.
  */
class InternalTopologyManager(kafkaConsumerConnConf: Config)(implicit am: ActorMaterializer, sys: ActorSystem) extends SyncDelegate {

//  SyncManager.get().addObserver(this, List(SyncType.INTERNAL_CLIENT_TOPOLOGY))

  private val triggers = scala.collection.mutable.Map[String, KillSwitch]()

  private lazy val CALLBACK_QUEUE_NAME = ConnektConfig.get("firefly.latency.metric.kafka.topic").getOrElse("ckt_callback_events_%s")
  private val LATENCY_METRIC_NAME = "LatencyMetrics"

  private val kafkaGroupNames = List(
    Map("name" -> Channel.SMS.toString.toUpperCase.format(LATENCY_METRIC_NAME),
      "topic" -> CALLBACK_QUEUE_NAME.format(Channel.SMS.toString.toLowerCase))
  )

  private def startTopology(topicName: String, kafkaGroupName: String): Unit = {
    val (streamComplete, killSwitch) = new InternalTopology(kafkaConsumerConnConf, topicName, kafkaGroupName).start()
    triggers += kafkaGroupName -> killSwitch
    streamComplete.onComplete( _ =>  triggers -= kafkaGroupName)(am.executionContext)
  }

  override def onUpdate(syncType: SyncType, args: List[AnyRef]): Any = {}

  def restoreState(): Unit ={
    if(kafkaGroupNames.nonEmpty)
      kafkaGroupNames.foreach(kafkaGroupName => startTopology(kafkaGroupName.get("topic").toString, kafkaGroupName.get("name").toString))
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
