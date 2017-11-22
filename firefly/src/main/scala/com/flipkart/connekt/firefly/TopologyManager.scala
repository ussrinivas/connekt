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

import akka.stream.KillSwitch
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.sync.SyncDelegate
import com.flipkart.connekt.commons.sync.SyncType._

trait TopologyManager extends SyncDelegate {

  val triggers = scala.collection.mutable.Map[String, KillSwitch]()

  protected var kafkaGroupNames: List[Map[String, String]] = Nil

  override def onUpdate(syncType: SyncType, args: List[AnyRef]): Any = {}

  def restoreState(): Unit = {
    kafkaGroupNames.foreach(kafkaGroupName => {
      val topicName: String = kafkaGroupName("topic")
      val name: String = kafkaGroupName("name")
      startTopology(topicName, name)
    })
  }

  def startTopology(topicName: String, kafkaGroupName: String): Unit = {}

  def stopAllTopologies() = {
    triggers.foreach {
      case (kafkaGroupName, killSwitch) =>
        ConnektLogger(LogFile.SERVICE).info(s"Stopping internal topology $kafkaGroupName on firefly shutdown")
        killSwitch.shutdown()
    }
  }

}
