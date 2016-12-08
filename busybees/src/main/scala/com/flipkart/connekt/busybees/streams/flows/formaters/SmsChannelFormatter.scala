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
package com.flipkart.connekt.busybees.streams.flows.formaters

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.streams.errors.ConnektStageException
import com.flipkart.connekt.busybees.streams.flows.NIOFlow
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.SmsUtil
import com.flipkart.connekt.commons.utils.StringUtils._
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import org.apache.commons.lang
import org.apache.commons.lang.StringUtils

import scala.concurrent.ExecutionContextExecutor

class SmsChannelFormatter(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends NIOFlow[ConnektRequest, SmsPayloadEnvelope](parallelism)(ec) {

  lazy val appLevelConfigService = ServiceFactory.getUserProjectConfigService

  override def map: ConnektRequest => List[SmsPayloadEnvelope] = message => {

    try {
      ConnektLogger(LogFile.PROCESSORS).info(s"SMSChannelFormatter received message: ${message.id}")
      ConnektLogger(LogFile.PROCESSORS).trace(s"SMSChannelFormatter received message: ${message.toString}")

      val smsInfo = message.channelInfo.asInstanceOf[SmsRequestInfo]
      val ttl = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(1l)
      val rD = message.channelData.asInstanceOf[SmsRequestData]
      val smsMeta = SmsUtil.getSmsInfo(rD.body)

      if (smsInfo.receivers.nonEmpty && ttl > 0) {
        val appDefaultCountryCode = appLevelConfigService.getProjectConfiguration(message.appName.toLowerCase, "app-local-country-code").get.get.value.getObj[ObjectNode]
        val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

        val formattedReceivers = smsInfo.receivers.map(r => {
          try {
            val validateNum: PhoneNumber = phoneUtil.parse(r, appDefaultCountryCode.get("localRegion").asText.trim.toUpperCase)
            if (phoneUtil.isValidNumber(validateNum)) {
              phoneUtil.format(validateNum, PhoneNumberFormat.E164)
            } else {
              ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping invalid numbers: $r")
              SmsCallbackEvent(message.id, lang.StringUtils.EMPTY, InternalStatus.InvalidNumber, r, message.clientId, lang.StringUtils.EMPTY, message.appName, message.contextId.orEmpty, s"SMSChannelFormatter dropping invalid numbers: $r").persist
              ServiceFactory.getReportingService.recordPushStatsDelta(message.clientId, message.contextId, message.stencilId, Option(lang.StringUtils.EMPTY), message.appName, InternalStatus.TTLExpired)
              null
            }
          } catch {
            case e: Exception =>
              ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping invalid numbers: $r", e)
              SmsCallbackEvent(message.id, lang.StringUtils.EMPTY, InternalStatus.InvalidNumber, r, message.clientId, lang.StringUtils.EMPTY, message.appName, message.contextId.orEmpty, e.getMessage).persist
              ServiceFactory.getReportingService.recordPushStatsDelta(message.clientId, message.contextId, message.stencilId, Option(lang.StringUtils.EMPTY), message.appName, InternalStatus.TTLExpired)
              null
          }
        }).filter(_ != null)

        val meta = SmsMeta(smsMeta.isUnicodeMessage, smsMeta.smsParts, SmsUtil.getCharset(rD.body).displayName()).asMap
        val payload = SmsPayload(formattedReceivers, rD, smsInfo.sender, ttl.toString)
        List(SmsPayloadEnvelope(message.id, message.clientId, message.stencilId.orEmpty, smsInfo.appName, message.contextId.orEmpty, payload, message.meta ++ meta))
      } else if (smsInfo.receivers.nonEmpty) {
        ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping ttl-expired message: ${message.id}")
        smsInfo.receivers.map(s => SmsCallbackEvent(message.id, StringUtils.EMPTY, InternalStatus.TTLExpired, s, message.clientId, null, smsInfo.appName, Channel.SMS, message.contextId.orEmpty)).persist
        ServiceFactory.getReportingService.recordPushStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Option(message.platform), message.appName, InternalStatus.TTLExpired, smsInfo.receivers.size)
        List.empty
      } else
        List.empty
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"SMSChannelFormatter error for ${message.id}", e)
        throw ConnektStageException(message.id, message.clientId, Channel.SMS, message.destinations, InternalStatus.StageError, message.appName, Channel.SMS, message.contextId.orEmpty, message.meta, "SMSChannelFormatter::".concat(e.getMessage), e)
    }
  }
}
