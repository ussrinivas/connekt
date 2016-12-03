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

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.streams.errors.ConnektStageException
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder.callbackRecorder
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels.{SmsPayloadEnvelope, _}
import com.flipkart.connekt.commons.utils.SmsUtil
import com.flipkart.connekt.commons.utils.StringUtils._
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import org.apache.commons.lang

import scala.collection.mutable.ListBuffer

class SeparateIntlReceivers extends MapFlowStage[SmsPayloadEnvelope, SmsPayloadEnvelope] {

  lazy val appLevelConfigService = ServiceFactory.getUserProjectConfigService

  override val map: SmsPayloadEnvelope => List[SmsPayloadEnvelope] = smsEnvelope => {
    try {
      val smsPayload = smsEnvelope.payload
      val nationalNumbers = ListBuffer[String]()
      val internationalNumbers = ListBuffer[String]()
      val envelopes = ListBuffer[SmsPayloadEnvelope]()
      val smsMeta = SmsUtil.getSmsInfo(smsPayload.messageBody.body)

      val providersDefaultCountryCode = appLevelConfigService.getProjectConfiguration(smsEnvelope.appName.toLowerCase, "providers-local-country-code").get.get.value.getObj[ObjectNode].get(smsEnvelope.provider.last)
      val appDefaultCountryCode = appLevelConfigService.getProjectConfiguration(smsEnvelope.appName.toLowerCase, "app-local-country-code").get.get.value.getObj[ObjectNode]
      val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

      smsPayload.receivers.foreach(r => {
        try {
          val validateNum: PhoneNumber = phoneUtil.parse(r, appDefaultCountryCode.get("localRegion").asText)
          if (phoneUtil.isValidNumber(validateNum)) {
            if (validateNum.getCountryCode.equals(providersDefaultCountryCode.get("localInitial").asInt))
              nationalNumbers += r
            else
              internationalNumbers += r
          } else {
            ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping invalid numbers: $r")
            SmsCallbackEvent(smsEnvelope.messageId, lang.StringUtils.EMPTY, smsMeta.smsParts.toString, smsMeta.encoding, smsMeta.smsLength.toString, lang.StringUtils.EMPTY, InternalStatus.InvalidNumber, r, smsEnvelope.clientId, lang.StringUtils.EMPTY, smsEnvelope.appName, smsEnvelope.contextId, s"SMSChannelFormatter dropping invalid numbers: $r").persist
            ServiceFactory.getReportingService.recordPushStatsDelta(smsEnvelope.clientId, Option(smsEnvelope.contextId), smsEnvelope.meta.get("stencilId").map(_.toString), Option(lang.StringUtils.EMPTY), smsEnvelope.appName, InternalStatus.TTLExpired)
          }
        } catch {
          case e: Exception =>
            ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping invalid numbers: $r", e)
            SmsCallbackEvent(smsEnvelope.messageId, lang.StringUtils.EMPTY, smsMeta.smsParts.toString, smsMeta.encoding, smsMeta.smsLength.toString, lang.StringUtils.EMPTY, InternalStatus.InvalidNumber, r, smsEnvelope.clientId, lang.StringUtils.EMPTY, smsEnvelope.appName, smsEnvelope.contextId, e.getMessage).persist
            ServiceFactory.getReportingService.recordPushStatsDelta(smsEnvelope.clientId, Option(smsEnvelope.contextId), smsEnvelope.meta.get("stencilId").map(_.toString), Option(lang.StringUtils.EMPTY), smsEnvelope.appName, InternalStatus.TTLExpired)
        }
      })

      if (nationalNumbers.nonEmpty) {
        val newPayload = smsEnvelope.payload.copy(receivers = nationalNumbers.toSet)
        envelopes += smsEnvelope.copy(payload = newPayload, isInternationalNumber = "0")
      }
      if (internationalNumbers.nonEmpty) {
        val newPayload = smsEnvelope.payload.copy(receivers = internationalNumbers.toSet)
        envelopes += smsEnvelope.copy(payload = newPayload, isInternationalNumber = "1")
      }
      envelopes.toList
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"ChooseProvider error", e)
        throw ConnektStageException(smsEnvelope.messageId, smsEnvelope.clientId, Channel.SMS, smsEnvelope.destinations, InternalStatus.RenderFailure, smsEnvelope.appName, Channel.SMS, smsEnvelope.contextId, smsEnvelope.meta, s"ChooseProvider-${e.getMessage}", e)
    }
  }
}
