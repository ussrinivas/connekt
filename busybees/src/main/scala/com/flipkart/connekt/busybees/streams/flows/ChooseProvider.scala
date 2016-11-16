package com.flipkart.connekt.busybees.streams.flows

import com.flipkart.connekt.commons.iomodels.PayloadEnvelope
import com.flipkart.connekt.commons.services.ConnektConfig

import collection.JavaConverters._
import collection.JavaConverters._
import scala.util.Random

class ChooseProvider(channel:String) extends  MapFlowStage[PayloadEnvelope,PayloadEnvelope] {
  override val map: (PayloadEnvelope) => List[PayloadEnvelope] = payload => {

    val selectedProvider = s"$channel:dummy"
    payload.provider += selectedProvider


    List(payload)
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
