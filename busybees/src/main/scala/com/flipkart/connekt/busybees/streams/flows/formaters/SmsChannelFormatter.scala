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
import org.apache.commons.lang.StringUtils

import scala.concurrent.ExecutionContextExecutor

class SmsChannelFormatter(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends NIOFlow[ConnektRequest, SmsPayloadEnvelope](parallelism)(ec) {

  override def map: ConnektRequest => List[SmsPayloadEnvelope] = message => {

    try {
      ConnektLogger(LogFile.PROCESSORS).info(s"SMSChannelFormatter received message: ${message.id}")
      ConnektLogger(LogFile.PROCESSORS).trace(s"SMSChannelFormatter received message: ${message.toString}")

      val smsInfo = message.channelInfo.asInstanceOf[SmsRequestInfo]
      val ttl = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(1l)
      val rD = message.channelData.asInstanceOf[SmsRequestData]

      val info = SmsUtil.getSmsInfo(rD.body)

      if (smsInfo.receivers.nonEmpty && ttl > 0) {
        val receiverMap = smsInfo.receivers.groupBy(r => r.countryCode.trim)
        receiverMap.map(r => {
          val payload = SmsPayload(r._2, rD, smsInfo.sender, ttl.toString)
          val meta = SmsMeta(info.isUnicodeMessage, info.smsParts, SmsUtil.getCharset(rD.body).displayName(), info.smsLength).asMap
          val isIntl = if (r._1.equals("91") || r._1.equals("+91")) "0" else "1"
          SmsPayloadEnvelope(message.id, message.clientId, message.stencilId.orEmpty, smsInfo.appName, message.contextId.orEmpty, payload, isIntl, message.meta ++ meta)
        }).toList
      } else if (smsInfo.receivers.nonEmpty) {
        ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping ttl-expired message: ${message.id}")
        smsInfo.receivers.map(s => SmsCallbackEvent(message.id, StringUtils.EMPTY, info.smsParts.toString, SmsUtil.getCharset(rD.body).displayName(), info.smsLength.toString, InternalStatus.TTLExpired, s, message.clientId, null, smsInfo.appName, Channel.SMS, message.contextId.orEmpty)).persist
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
