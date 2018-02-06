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
package com.flipkart.connekt.receptors.routes.helper

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils.JSONUnMarshallFunctions
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat

import scala.util.Try

object PhoneNumberHelper extends Instrumented{

  val appLevelConfigService = ServiceFactory.getUserProjectConfigService
  val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

  def validateNumber(appName: String, number: String): Boolean = {
    val validateNum = Try(phoneUtil.parse(number, getLocalRegion(appName)))
    validateNum.isSuccess && phoneUtil.isValidNumber(validateNum.get)
  }

  def validateNFormatNumber(appName: String, number: String): Option[String] = {
    Try(phoneUtil.parse(number, getLocalRegion(appName))) match {
      case number if number.isSuccess && phoneUtil.isValidNumber(number.get) =>
        meter("validateNFormatNumber.success").mark()
        Some(phoneUtil.format(number.get, PhoneNumberFormat.E164))
      case _ =>
        meter("validateNFormatNumber.failed").mark()
        None
    }
  }

  private def getLocalRegion(appName: String) = {
    appLevelConfigService.getProjectConfiguration(appName.toLowerCase, "app-local-country-code")
      .get.get.value
      .getObj[ObjectNode]
      .get("localRegion")
      .asText.trim.toUpperCase
  }
}
