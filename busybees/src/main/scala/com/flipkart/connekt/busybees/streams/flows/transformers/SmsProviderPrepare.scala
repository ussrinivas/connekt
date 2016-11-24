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
package com.flipkart.connekt.busybees.streams.flows.transformers

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import com.flipkart.connekt.busybees.models.SmsRequestTracker
import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels.SmsPayloadEnvelope
import com.flipkart.connekt.commons.services.KeyChainManager
import com.flipkart.connekt.commons.utils.StringUtils._

class SmsProviderPrepare extends MapFlowStage[SmsPayloadEnvelope, (HttpRequest, SmsRequestTracker)] {

  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: (SmsPayloadEnvelope) => List[(HttpRequest, SmsRequestTracker)] = smsPayloadEnvelope => {

    try {
      val selectedProvider = smsPayloadEnvelope.provider.last
      val credentials = KeyChainManager.getSimpleCredential(s"sms.${smsPayloadEnvelope.appName.toLowerCase}.$selectedProvider").get

      val headers: Map[String, String] = Map("X-MID" -> smsPayloadEnvelope.messageId, "X-CNAME" -> smsPayloadEnvelope.clientId, "X-TID" -> smsPayloadEnvelope.templateId)

      val tracker = SmsRequestTracker(messageId = smsPayloadEnvelope.messageId,
        clientId = smsPayloadEnvelope.clientId,
        receivers = smsPayloadEnvelope.payload.receivers,
        provider = selectedProvider,
        appName = smsPayloadEnvelope.appName,
        contextId = smsPayloadEnvelope.contextId,
        request = smsPayloadEnvelope,
        meta = smsPayloadEnvelope.meta)

      val providerStencil = stencilService.getStencilsByName(s"ckt-sms-$selectedProvider").find(_.component.equalsIgnoreCase("prepare")).get

      val result = stencilService.materialize(providerStencil, Map("data" -> smsPayloadEnvelope, "credentials" -> credentials, "headers" -> headers).getJsonNode)

      val httpRequest = result.asInstanceOf[HttpRequest]
        .addHeader(RawHeader("x-message-id", smsPayloadEnvelope.messageId))
        .addHeader(RawHeader("x-context-id", smsPayloadEnvelope.contextId))

      println(s"Http request : $httpRequest")

      List(Tuple2(httpRequest, tracker))
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"SMSChannelFormatter error for ${smsPayloadEnvelope.messageId}", e)
        throw ConnektPNStageException(smsPayloadEnvelope.messageId, smsPayloadEnvelope.clientId, smsPayloadEnvelope.destinations, InternalStatus.StageError, smsPayloadEnvelope.appName, "", smsPayloadEnvelope.contextId, smsPayloadEnvelope.meta, "SMSChannelFormatter::".concat(e.getMessage), e)
    }
  }
}
