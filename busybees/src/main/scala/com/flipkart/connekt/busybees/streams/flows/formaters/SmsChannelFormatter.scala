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

import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.busybees.streams.flows.NIOFlow
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class SmsChannelFormatter(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends NIOFlow[ConnektRequest, SmsPayloadEnvelope](parallelism)(ec) {

  override def map: ConnektRequest => List[SmsPayloadEnvelope] = message => {

    try {
      ConnektLogger(LogFile.PROCESSORS).info(s"SMSChannelFormatter received message: ${message.id}")
      ConnektLogger(LogFile.PROCESSORS).trace(s"SMSChannelFormatter received message: ${message.toString}")

      val smsInfo = message.channelInfo.asInstanceOf[SmsRequestInfo]

      val ttl = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(6.hour.toSeconds)

      if (smsInfo.receivers.nonEmpty && ttl > 0) {
        val payload = SmsPayload(smsInfo.receivers.mkString(","), message.channelData.asInstanceOf[SmsRequestData], "Text", "FLPKRT", "1", "0")
        List(SmsPayloadEnvelope(message.id, message.clientId, message.stencilId.orEmpty, smsInfo.appName, message.contextId.orEmpty, payload, message.meta))
      } else if (smsInfo.receivers.nonEmpty) {
        ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping ttl-expired message: ${message.id}")
        smsInfo.receivers.map(s => SMSCallbackEvent(message.id, InternalStatus.TTLExpired, s, message.clientId, null, smsInfo.appName, Channel.SMS, message.contextId.orEmpty)).persist
        ServiceFactory.getReportingService.recordPushStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Option(message.platform), message.appName, InternalStatus.TTLExpired, smsInfo.receivers.size)
        List.empty
      } else
        List.empty
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"SMSChannelFormatter error for ${message.id}", e)
        throw ConnektPNStageException(message.id, message.clientId, message.deviceId, InternalStatus.StageError, message.appName, message.platform, message.contextId.orEmpty, message.meta, "SMSChannelFormatter::".concat(e.getMessage), e)
    }
  }
}
