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

import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.Channel._
import com.flipkart.connekt.commons.entities.ConfigFormat
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels.{EmailPayloadEnvelope, ProviderEnvelope}
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.util.Random

class ChooseProvider[T <: ProviderEnvelope](channel: Channel) extends MapFlowStage[T, T] {

  lazy val appLevelConfigService = ServiceFactory.getUserProjectConfigService

  override val map: (T) => List[T] = payload => {
    try{
      val selectedProvider = pickProvider(payload.provider.toList, channel, payload.appName)
      val out = payload match {
        case email: EmailPayloadEnvelope =>
          email.copy(provider = email.provider :+ selectedProvider)
        case _ =>
          payload
      }
      List(out.asInstanceOf[T])
    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"RenderFlow error", e)
        throw new ConnektPNStageException(payload.messageId, payload.clientId,payload.destinations, InternalStatus.RenderFailure, payload.appName, channel, payload.contextId,  payload.meta , s"ChooseProvider-${e.getMessage}", e)
    }
  }

  def pickProvider(alreadyTriedProviders: List[String], channel: Channel, appName: String): String = {

    val appProviderShareConfig = appLevelConfigService.getProjectConfiguration(appName, s"share-${channel.toString}").get.get //this must be a success
    assert(appProviderShareConfig.format == ConfigFormat.JSON ,"Provider Config Must be JSON Format")
    //TODO : get out of this restriction. If not defined do auto share
    assert(!StringUtils.isNullOrEmpty(appProviderShareConfig.value) ,"Provider Percentage must be defined")
    val jsonObj = appProviderShareConfig.value.getObj[Map[String,String]]

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

  }

}
