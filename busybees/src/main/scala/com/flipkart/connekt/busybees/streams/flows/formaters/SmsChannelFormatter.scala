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
import com.flipkart.connekt.commons.services.ExclusionService
import com.flipkart.connekt.commons.utils.SmsUtil
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

class SmsChannelFormatter(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends NIOFlow[ConnektRequest, SmsPayloadEnvelope](parallelism)(ec) {

  lazy val appLevelConfigService = ServiceFactory.getUserProjectConfigService

  override def map: ConnektRequest => List[SmsPayloadEnvelope] = message => {

    try {
      ConnektLogger(LogFile.PROCESSORS).info(s"SMSChannelFormatter received message: ${message.id}")
      ConnektLogger(LogFile.PROCESSORS).trace(s"SMSChannelFormatter received message: ${message.toString}")

      val senderMask = appLevelConfigService.getProjectConfiguration(message.appName.toLowerCase, s"sender-mask-${Channel.SMS.toString}") match {
        case Success(s) => s.map(_.value.toUpperCase).orNull
        case Failure(e) =>
          throw ConnektStageException(message.id, message.clientId, message.destinations, InternalStatus.StageError, message.appName, Channel.SMS, message.contextId.orEmpty, message.meta, "SMSChannelFormatter::".concat(e.getMessage), e)
      }

      val smsInfo = message.channelInfo.asInstanceOf[SmsRequestInfo]
      val ttl = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(1l)
      val rD = message.channelData.asInstanceOf[SmsRequestData]
      val smsMeta = SmsUtil.getSmsInfo(rD.body)

      if (ttl > 0) {
        if (smsInfo.receivers.nonEmpty && rD.body.trim.nonEmpty) {

          val (filteredNumbers, excludedNumbers) = smsInfo.receivers.partition { r => ExclusionService.lookup(message.channel, message.appName, r).getOrElse(false) }
          excludedNumbers.map(ex => SmsCallbackEvent(message.id, InternalStatus.ExcludedRequest, ex, message.clientId, smsInfo.appName, Channel.SMS, message.contextId.orEmpty)).persist
          ServiceFactory.getReportingService.recordChannelStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Channel.SMS, message.appName, InternalStatus.ExcludedRequest, excludedNumbers.size)

          val payload = SmsPayload(filteredNumbers, rD, senderMask, ttl.toString)

          if (payload.receivers.nonEmpty)
            List(SmsPayloadEnvelope(message.id, message.clientId, message.stencilId.orEmpty, smsInfo.appName, message.contextId.orEmpty, payload, message.meta ++ smsMeta.asMap))
          else {
            ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping message with no valid receiver : ${message.id}")
            List.empty
          }
        } else {
          ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping message with empty body or no receiver : ${message.id}")
          smsInfo.receivers.map(s => SmsCallbackEvent(message.id, InternalStatus.InvalidRequest, s, message.clientId, smsInfo.appName, Channel.SMS, message.contextId.orEmpty)).persist
          ServiceFactory.getReportingService.recordChannelStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Channel.SMS, message.appName, InternalStatus.InvalidRequest, smsInfo.receivers.size)
          List.empty
        }
      } else {
        ConnektLogger(LogFile.PROCESSORS).warn(s"SMSChannelFormatter dropping ttl-expired message: ${message.id}")
        smsInfo.receivers.map(s => SmsCallbackEvent(message.id, InternalStatus.TTLExpired, s, message.clientId, smsInfo.appName, Channel.SMS, message.contextId.orEmpty)).persist
        ServiceFactory.getReportingService.recordChannelStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Channel.SMS, message.appName, InternalStatus.TTLExpired, smsInfo.receivers.size)
        List.empty
      }
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"SMSChannelFormatter error for ${message.id}", e)
        throw ConnektStageException(message.id, message.clientId, message.destinations, InternalStatus.StageError, message.appName, Channel.SMS, message.contextId.orEmpty, message.meta, "SMSChannelFormatter::".concat(e.getMessage), e)
    }
  }
}
