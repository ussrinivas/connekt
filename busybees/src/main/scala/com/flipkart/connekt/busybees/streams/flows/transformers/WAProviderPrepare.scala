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

import akka.http.scaladsl.model._
import com.flipkart.connekt.busybees.models.WARequestTracker
import com.flipkart.connekt.busybees.streams.errors.ConnektStageException
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.entities.{Channel, Stencil}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.{InternalStatus, WAResponseStatus}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._

class WAProviderPrepare extends MapFlowStage[ConnektRequest, (HttpRequest, WARequestTracker)] {

  lazy val baseUrl = ConnektConfig.getString("wa.base.uri").get
  lazy implicit val stencilService = ServiceFactory.getStencilService
  private val sendUri = baseUrl + "/api/rest_send.php"

  override val map: ConnektRequest => List[(HttpRequest, WARequestTracker)] = connektRequest => profile("map") {

    try {
      connektRequest.destinations.map(destination => {
        val tracker = WARequestTracker(
          messageId = connektRequest.id,
          clientId = connektRequest.clientId,
          destination = destination,
          appName = connektRequest.appName,
          contextId = connektRequest.contextId.getOrElse(""),
          meta = connektRequest.meta
        )
        WAPayloadFormatter.makePayload(connektRequest, destination) match {
          case (waPayload: Some[WARequest]) =>
            ConnektLogger(LogFile.PROCESSORS).info(s"WAProviderPrepare sending whatsapp message to: $destination, payload: $waPayload")
            val requestEntity = HttpEntity(ContentTypes.`application/json`, waPayload.getJson)
            def httpRequest = HttpRequest(HttpMethods.POST, sendUri, scala.collection.immutable.Seq.empty[HttpHeader], requestEntity)
            httpRequest -> tracker
        }
      }).toList
    }
    catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"WAProviderPrepare error for ${connektRequest.id}", e)
        throw ConnektStageException(
          connektRequest.id,
          connektRequest.clientId,
          connektRequest.destinations,
          InternalStatus.StageError,
          connektRequest.appName,
          Channel.WA,
          connektRequest.contextId.getOrElse(""),
          connektRequest.meta,
          "WAProviderPrepare::".concat(e.getMessage),
          e
        )
    }
  }
}

object WAPayloadFormatter{
  lazy implicit val stencilService = ServiceFactory.getStencilService
  def makePayload(connektRequest: ConnektRequest, destination: String): Option[WARequest] = {
    val waRequestData = connektRequest.channelData.asInstanceOf[WARequestData]
    waRequestData.waType match {
      case WAType.hsm =>
        stencilService.get(connektRequest.stencilId.get).headOption match {
          case Some(stencil: Stencil) =>
            val hsmData = stencilService.materialize(stencil, connektRequest.channelDataModel).asInstanceOf[String].getObj[HsmData]
            Some(WARequest(HSMWAPayload( hsmData, destination )))
          case _ =>
            ServiceFactory.getReportingService.recordChannelStatsDelta(connektRequest.clientId, connektRequest.contextId, connektRequest.stencilId, Channel.WA, connektRequest.appName, WAResponseStatus.StencilNotFound.toString)
            ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler stencil failure for hsm message to: $destination")
            val event = WACallbackEvent(connektRequest.id, None, destination, WAResponseStatus.StencilNotFound.toString, connektRequest.clientId, connektRequest.appName, connektRequest.contextId.get, WAResponseStatus.StencilNotFound.toString, System.currentTimeMillis())
            event.enqueue
            None
        }
      case WAType.document =>
        val attachment = waRequestData.attachment.get
        Some(WARequest(DocumentWAPayload(
          FileData(attachment.name, attachment.caption.getOrElse("")), destination
        )))
      case WAType.image =>
        val attachment = waRequestData.attachment.get
        Some(WARequest(ImageWAPayload(
          FileData(attachment.name, attachment.caption.getOrElse("")), destination
        )))
      case WAType.text =>
        Some(WARequest(TxtWAPayload(
          waRequestData.message.get, destination
        )))
      case _ =>
        ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaResponseHandler unknown WA ConnektRequest Data type: ${waRequestData.waType}")
        None
    }
  }
}
