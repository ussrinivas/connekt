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

import java.nio.charset.{Charset, CharsetEncoder}

import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.busybees.streams.flows.NIOFlow
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils._
import org.apache.commons.codec.CharEncoding
import org.apache.commons.lang.StringUtils

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class SmsChannelFormatter(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends NIOFlow[ConnektRequest, SmsPayloadEnvelope](parallelism)(ec) {

  lazy val appLevelConfigService = ServiceFactory.getUserProjectConfigService
  private val defaultEncoder: CharsetEncoder = Charset.forName(CharEncoding.US_ASCII).newEncoder

  override def map: ConnektRequest => List[SmsPayloadEnvelope] = message => {

    try {
      ConnektLogger(LogFile.PROCESSORS).info(s"SMSChannelFormatter received message: ${message.id}")
      ConnektLogger(LogFile.PROCESSORS).trace(s"SMSChannelFormatter received message: ${message.toString}")

      val smsInfo = message.channelInfo.asInstanceOf[SmsRequestInfo]

      val ttl = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(6.hour.toSeconds)

      val senderMask = appLevelConfigService.getProjectConfiguration(message.appName, s"sender-mask-${Channel.SMS.toString}").get.get.value.toUpperCase //this must be a success

      if (smsInfo.receivers.nonEmpty && ttl > 0) {

        var isUnicodeMessage: Boolean = false
        val rD = message.channelData.asInstanceOf[SmsRequestData]

        //Check if unicode
        defaultEncoder.reset
        if (!defaultEncoder.canEncode(rD.body)) isUnicodeMessage = true

        //Cutoff timestamp
        val dvt = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(0).toString

        val payload = SmsPayload(smsInfo.receivers, rD, isUnicodeMessage, senderMask, dvt, "0")
        List(SmsPayloadEnvelope(message.id, message.clientId, message.stencilId.orEmpty, smsInfo.appName, message.contextId.orEmpty, payload, message.meta))
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
        throw ConnektPNStageException(message.id, message.clientId, message.destinations, InternalStatus.StageError, message.appName, message.platform, message.contextId.orEmpty, message.meta, "SMSChannelFormatter::".concat(e.getMessage), e)
    }
  }
}
