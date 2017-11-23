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

import com.flipkart.connekt.commons.entities.{Channel, Stencil}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.WAResponseStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils.JSONUnMarshallFunctions

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
        Some(WARequest(ImageWAPayload(
          FileData(waRequestData.attachment.get.name, waRequestData.attachment.get.caption.getOrElse("")), destination
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
