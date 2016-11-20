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

import com.flipkart.connekt.busybees.streams.flows.transformers.AppLevelConfigType
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.Channel._
import com.flipkart.connekt.commons.entities.{AppLevelConfig, Channel, ConfigFormat}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.{EmailPayloadEnvelope, ProviderEnvelope, SmsPayloadEnvelope}
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.util.Random

class ChooseProvider[T <: ProviderEnvelope](channel: Channel) extends MapFlowStage[T, T] {

  lazy val appLevelConfigService = ServiceFactory.getAppLevelConfigService

  override val map: (T) => List[T] = payload => {
    val selectedProvider = pickProvider(payload.provider.toList, channel, payload.appName)
    val out = payload match {
      case email: EmailPayloadEnvelope =>
        email.copy(provider = email.provider :+ selectedProvider)
      case sms: SmsPayloadEnvelope =>
        sms.copy(provider = sms.provider :+ selectedProvider)
    }
    List(out.asInstanceOf[T])
  }

  def pickProvider(alreadyTriedProviders: List[String], channel: Channel, appName: String): String = {

    val appLevelConfig = appLevelConfigService.getAppLevelConfig(appName, Channel.SMS).getOrElse(List.empty[AppLevelConfig]).find(_.config.equalsIgnoreCase(AppLevelConfigType.providerShare)).get

    val selectedProvider = appLevelConfig.format match {
      case ConfigFormat.JSON =>
        val jsonObj = appLevelConfig.value.getObj[Map[String,String]]
        if (!StringUtils.isNullOrEmpty(jsonObj)) {

          val remainingProviders = jsonObj.filterKeys(!alreadyTriedProviders.contains(_))

          val maxValue = remainingProviders.foldLeft(0)((a, b) => {
            a + Try_(b._2.toString.toInt).getOrElse(0)
          })

          val randomGenerator = new Random()
          val randomNumber = randomGenerator.nextInt(maxValue)

          var counter = 0
          val randomProvider = remainingProviders.keySet.toList.sorted.map(key => {
            val providerShare = Try_(remainingProviders(key).toString.toInt).getOrElse(0)
            val compareValue = providerShare + counter
            counter += providerShare
            key -> compareValue
          }).find(_._2 > randomNumber).getOrElse(remainingProviders.head)._1

          randomProvider.toLowerCase
        } else {
          null
        }
      case _ =>
        ConnektLogger(LogFile.SERVICE).error(s"No provider defined for client $appName")
        null
    }
    selectedProvider
  }
}

