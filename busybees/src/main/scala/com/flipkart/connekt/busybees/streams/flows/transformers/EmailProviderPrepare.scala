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

import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import akka.http.scaladsl.model.headers.RawHeader
import com.flipkart.connekt.busybees.models.EmailRequestTracker
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.entities.{Stencil, StencilEngine}
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels.EmailPayloadEnvelope
import com.flipkart.connekt.commons.services.KeyChainManager
import com.flipkart.connekt.commons.utils.StringUtils._


class EmailProviderPrepare extends MapFlowStage[EmailPayloadEnvelope, (HttpRequest, EmailRequestTracker)] {


  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: (EmailPayloadEnvelope) => List[(HttpRequest, EmailRequestTracker)] = emailPayloadEnvelope => {

    val selectedProvider = emailPayloadEnvelope.provider.last
    val credentials = KeyChainManager.getSimpleCredential(s"email.${emailPayloadEnvelope.appName.toLowerCase}.$selectedProvider").get

    val tracker = EmailRequestTracker(messageId = emailPayloadEnvelope.messageId,
      clientId = emailPayloadEnvelope.clientId,
      to = emailPayloadEnvelope.payload.to.map(_.address),
      cc = emailPayloadEnvelope.payload.cc.map(_.address),
      appName = emailPayloadEnvelope.appName,
      contextId = emailPayloadEnvelope.contextId,
      provider = selectedProvider,
      meta = emailPayloadEnvelope.meta)

    val providerStencil = stencilService.getStencilsByName(s"ckt-email-$selectedProvider").find(_.component.equalsIgnoreCase("prepare")).get

    val result = stencilService.materialize(providerStencil, Map("data" -> emailPayloadEnvelope, "credentials" -> credentials).getJsonNode)

    val httpRequest = result.asInstanceOf[HttpRequest]
      .addHeader(RawHeader("x-message-id", emailPayloadEnvelope.messageId))
      .addHeader(RawHeader("x-context-id", emailPayloadEnvelope.contextId))

    List(Tuple2(httpRequest, tracker))
  }
}
