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
package com.flipkart.connekt.busybees.streams.flows

import com.flipkart.connekt.commons.iomodels.{EmailPayloadEnvelope, ProviderEnvelope}
import com.flipkart.connekt.commons.services.ConnektConfig

import scala.collection.JavaConverters._
import scala.util.Random

class ChooseProvider(channel:String) extends  MapFlowStage[ProviderEnvelope,ProviderEnvelope] {
  override val map: (ProviderEnvelope) => List[ProviderEnvelope] = payload => {

    val selectedProvider = s"$channel:dummy"

    val out = payload match {
      case email:EmailPayloadEnvelope =>
        email.copy( provider = email.provider :+ selectedProvider)
      case sms : _ =>
        payload
    }

    List(out)
  }

  def chooseSmsProvider(platform: String): String = {

    val smsProviders = ConnektConfig.getList[java.util.HashMap[String, String]]("sms.providers.share").map(_.asScala).map(p => {
      p("name").toString -> p("value").toString.toInt
    }).toMap

    val maxValue = smsProviders.foldLeft(0)(_ + _._2)
    val randomGenerator = new Random()
    val randomNumber = randomGenerator.nextInt(maxValue)
    var counter = 0

    smsProviders.keySet.toList.sorted.map(key => {
      val compareValue = smsProviders(key) + counter
      counter += smsProviders(key)
      key -> compareValue
    }).find(_._2 > randomNumber).getOrElse(smsProviders.head)._1
  }
}
