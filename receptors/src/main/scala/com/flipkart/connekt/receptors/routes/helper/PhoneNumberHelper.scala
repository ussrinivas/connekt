package com.flipkart.connekt.receptors.routes.helper

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.utils.StringUtils.JSONUnMarshallFunctions
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat

import scala.util.Try

object PhoneNumberHelper {

  val appLevelConfigService = ServiceFactory.getUserProjectConfigService
  val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

  def validateNumber(appName: String, number: String): Boolean = {
    val appDefaultCountryCode = appLevelConfigService.getProjectConfiguration(appName.toLowerCase, "app-local-country-code").get.get.value.getObj[ObjectNode]
    val validateNum = Try(phoneUtil.parse(number, appDefaultCountryCode.get("localRegion").asText.trim.toUpperCase))
    validateNum.isSuccess && phoneUtil.isValidNumber(validateNum.get)
  }

  def validateNFormatNumber(appName: String, number: String): Option[String] = {
    val appDefaultCountryCode = appLevelConfigService.getProjectConfiguration(appName.toLowerCase, "app-local-country-code").get.get.value.getObj[ObjectNode]
    val validateNum = Try(phoneUtil.parse(number, appDefaultCountryCode.get("localRegion").asText.trim.toUpperCase))
    if (validateNum.isSuccess && phoneUtil.isValidNumber(validateNum.get)) {
      Some(phoneUtil.format(validateNum.get, PhoneNumberFormat.E164))
    } else
      None
  }
}
