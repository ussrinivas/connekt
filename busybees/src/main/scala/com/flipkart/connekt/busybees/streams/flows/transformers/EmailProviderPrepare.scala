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
import akka.stream.Materializer
import com.flipkart.connekt.busybees.models.EmailRequestTracker
import com.flipkart.connekt.busybees.streams.flows.MapAsyncFlowStage
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.EmailPayloadEnvelope
import com.flipkart.connekt.commons.services.KeyChainManager
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.{ExecutionContext, Future}


class EmailProviderPrepare(parallelism: Int)(implicit ec: ExecutionContext) extends MapAsyncFlowStage[EmailPayloadEnvelope, (HttpRequest, EmailRequestTracker)](parallelism) {

  private lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: (EmailPayloadEnvelope) => Future[List[(HttpRequest, EmailRequestTracker)]] = emailPayloadEnvelope => Future(profile("map") {

    val selectedProvider = emailPayloadEnvelope.provider.last
    val credentials = KeyChainManager.getSimpleCredential(s"email.${emailPayloadEnvelope.appName.toLowerCase}.$selectedProvider").get
    ConnektLogger(LogFile.PROCESSORS).debug(s"EmailProviderPrepare received message: ${emailPayloadEnvelope.messageId}")
    //ConnektLogger(LogFile.PROCESSORS).trace(s"EmailProviderPrepare received message: {}", supplier(emailPayloadEnvelope))

    val tracker = EmailRequestTracker(messageId = emailPayloadEnvelope.messageId,
      clientId = emailPayloadEnvelope.clientId,
      to = emailPayloadEnvelope.payload.to.map(_.address),
      cc = emailPayloadEnvelope.payload.cc.map(_.address),
      appName = emailPayloadEnvelope.appName,
      contextId = emailPayloadEnvelope.contextId,
      provider = selectedProvider,
      request = emailPayloadEnvelope,
      meta = emailPayloadEnvelope.meta)

    val providerStencil = stencilService.getStencilsByName(s"ckt-email-$selectedProvider").find(_.component.equalsIgnoreCase("prepare")).get

    val result = stencilService.materialize(providerStencil, Map("data" -> emailPayloadEnvelope, "credentials" -> credentials).getJsonNode)

    val httpRequest = result.asInstanceOf[HttpRequest]
      .addHeader(RawHeader("x-message-id", emailPayloadEnvelope.messageId))
      .addHeader(RawHeader("x-context-id", emailPayloadEnvelope.contextId))

    List(Tuple2(httpRequest, tracker))
  })(ec)
}
