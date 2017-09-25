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
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class EmailChannelFormatter(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends NIOFlow[ConnektRequest, EmailPayloadEnvelope](parallelism)(ec) {

  private lazy val projectConfigService = ServiceFactory.getUserProjectConfigService

  override def map: ConnektRequest => List[EmailPayloadEnvelope] = message => profile("map") {

    try {
      ConnektLogger(LogFile.PROCESSORS).debug(s"EmailChannelFormatter received message: ${message.id}")
      //ConnektLogger(LogFile.PROCESSORS).trace(s"EmailChannelFormatter received message: {}", supplier(message))

      val emailInfo = message.channelInfo.asInstanceOf[EmailRequestInfo]

      val ttl = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(30.days.toSeconds)

      val cc = Option(emailInfo.cc).getOrElse(Set.empty)
      val bcc = Option(emailInfo.bcc).getOrElse(Set.empty)

      if (emailInfo.to.nonEmpty && ttl > 0) {

        val emailReceivers = emailInfo.to ++ cc ++ bcc
        val excludedAddress = emailReceivers.filter(_.address.isDefined).filterNot { e => ExclusionService.lookup(message.channel, message.appName, e.address).getOrElse(true) }
        excludedAddress.map(ex => EmailCallbackEvent(message.id, message.clientId, ex.address, InternalStatus.ExcludedRequest , emailInfo.appName, Channel.EMAIL, message.contextId.orEmpty)).persist
        ServiceFactory.getReportingService.recordChannelStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Channel.EMAIL, message.appName, InternalStatus.ExcludedRequest, excludedAddress.size)

        val filteredTo = emailInfo.to.diff(excludedAddress).filter(_.address.isDefined)
        val filteredCC = cc.diff(excludedAddress).filter(_.address.isDefined)
        val filteredBcc = bcc.diff(excludedAddress).filter(_.address.isDefined) ++ projectConfigService.getProjectConfiguration(emailInfo.appName, "bcc-email-address").get.map(_.value.getObj[EmailAddress])
        val fromAddress = Option(emailInfo.from).getOrElse(projectConfigService.getProjectConfiguration(emailInfo.appName, "default-from-email").get.get.value.getObj[EmailAddress])

        val payload = EmailPayload(
          to = filteredTo,
          cc = filteredCC,
          bcc = filteredBcc,
          data = message.channelData.asInstanceOf[EmailRequestData],
          from = fromAddress,
          replyTo = Option(emailInfo.replyTo).getOrElse(fromAddress)
        )

        if (payload.to.nonEmpty)
          List(EmailPayloadEnvelope(messageId = message.id, appName = emailInfo.appName, contextId = message.contextId.orEmpty, clientId = message.clientId, payload = payload, meta = message.meta))
        else {
          ConnektLogger(LogFile.PROCESSORS).warn(s"EmailChannelFormatter dropping message due to suppressed address passed in `to` : ${message.id}")
          emailInfo.cc.map(e => EmailCallbackEvent(message.id, message.clientId, e.address, InternalStatus.Rejected, emailInfo.appName, message.contextId.orEmpty, "`to` cannot be empty.")).persist
          ServiceFactory.getReportingService.recordChannelStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Channel.EMAIL, message.appName, InternalStatus.Rejected, emailInfo.cc.size)
          List.empty
        }
      } else if (emailInfo.to.nonEmpty) {
        ConnektLogger(LogFile.PROCESSORS).warn(s"EmailChannelFormatter dropping ttl-expired message: ${message.id}")
        emailInfo.to.map(e => EmailCallbackEvent(message.id, message.clientId, e.address, InternalStatus.TTLExpired, emailInfo.appName, message.contextId.orEmpty)).persist
        emailInfo.cc.map(e => EmailCallbackEvent(message.id, message.clientId, e.address, InternalStatus.TTLExpired, emailInfo.appName, message.contextId.orEmpty)).persist
        ServiceFactory.getReportingService.recordChannelStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Channel.EMAIL, message.appName, InternalStatus.TTLExpired, emailInfo.to.size + emailInfo.cc.size)
        List.empty
      } else
        List.empty
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"EmailChannelFormatter error for ${message.id}", e)
        throw ConnektStageException(message.id, message.clientId, message.destinations, InternalStatus.StageError, message.appName, Channel.EMAIL, message.contextId.orEmpty, message.meta, "EmailChannelFormatter::".concat(e.getMessage), e)
    }
  }
}
