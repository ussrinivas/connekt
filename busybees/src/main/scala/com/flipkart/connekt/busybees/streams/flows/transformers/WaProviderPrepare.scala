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

import java.util

import akka.http.scaladsl.model.HttpRequest
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.busybees.models.{SmsRequestTracker, WaRequestTracker}
import com.flipkart.connekt.busybees.streams.errors.ConnektStageException
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.MessageStatus.{InternalStatus, WaResponseStatus}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.KeyChainManager
import com.flipkart.connekt.commons.utils.StringUtils.{JSONUnMarshallFunctions, _}

import scala.collection.JavaConverters._

class WaProviderPrepare extends MapFlowStage[SmsPayloadEnvelope, (HttpRequest, WaRequestTracker)] {

  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: ConnektRequest => List[(HttpRequest, WaRequestTracker)] = connektRequest => profile("map") {

    try {
      val credentials = KeyChainManager.getSimpleCredential(s"whatsapp.${connektRequest.appName}").get

      val tracker = WaRequestTracker(
        messageId = connektRequest.id,
        clientId = connektRequest.clientId,
        destination = connektRequest.channelInfo.asInstanceOf[WaRequestInfo].destinations.head,
        appName = connektRequest.appName,
        contextId = connektRequest.contextId.getOrElse(""),
        meta = connektRequest.meta
      )

      val waPayload = WaPayloadService.makePayload(connektRequest)
      val providerStencil = stencilService.getStencilsByName(s"wa-${connektRequest.appName}").find(_.component.equalsIgnoreCase("prepare")).get

      val result = stencilService.materialize(providerStencil, Map("data" -> connektRequest, "credentials" -> credentials, "tracker" -> tracker).getJsonNode)

      val httpRequests = result.asInstanceOf[util.LinkedHashMap[HttpRequest, String]].asScala.map { case (request, updatedTracker) =>
        (request , updatedTracker.getObj[WaRequestTracker])
      }.toList

      httpRequests
    }
    catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"WaChannelFormatter error for ${connektRequest.id}", e)
        throw ConnektStageException(
          connektRequest.id,
          connektRequest.clientId,
          connektRequest.destinations,
          InternalStatus.StageError,
          connektRequest.appName,
          Channel.WA,
          connektRequest.contextId.getOrElse(""),
          connektRequest.meta,
          "SMSChannelFormatter::".concat(e.getMessage),
          e
        )
    }
  }

}

object WaMessageIdMappingService{
  lazy implicit val stencilService = ServiceFactory.getStencilService
  def makePayload(connektRequest: ConnektRequest): WaPayload = {
    val waRequestData = connektRequest.channelData.asInstanceOf[WARequestData]
    waRequestData.type match {
      case "hsm" =>
        val providerStencil = stencilService.getStencilsByName(connektRequest.stencilId.get).headOption match {
          case None =>
            ServiceFactory.getReportingService.recordChannelStatsDelta(connektRequest.clientId, connektRequest.contextId, connektRequest.stencilId, Channel.WA, connektRequest.appName, WaResponseStatus.StencilNotFound)
            val event = WACallbackEvent(connektRequest.id, WaResponseStatus.StencilNotFound, connektRequest.destinations.head, connektRequest.clientId, connektRequest.appName, connektRequest.contextId.get, WaResponseStatus.StencilNotFound, System.currentTimeMillis())
            event.enqueue
          case Some(stencil) =>
            stencilService.materialize(stencil, Map("connektRequest" -> connektRequest).getJsonNode).asInstanceOf[WaPayload]
        }
      case "PDF" =>
        PDFWaPayload(
          DocumentData(waRequestData.fileName, waRequestData.caption),
          connektRequest.channelInfo.asInstanceOf[WaRequestInfo].destinations.head
        )
    }
  }
}

