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
import com.flipkart.connekt.busybees.models.{SmsRequestTracker, WARequestTracker}
import com.flipkart.connekt.busybees.streams.errors.ConnektStageException
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.busybees.streams.flows.formaters.WAPayloadFormatter
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.MessageStatus.{InternalStatus, WAResponseStatus}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.KeyChainManager
import com.flipkart.connekt.commons.utils.StringUtils.{JSONUnMarshallFunctions, _}

import scala.collection.JavaConverters._

class WaProviderPrepare extends MapFlowStage[ConnektRequest, (HttpRequest, WARequestTracker)] {

  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: ConnektRequest => List[(HttpRequest, WARequestTracker)] = connektRequest => profile("map") {

    try {
      val credentials = KeyChainManager.getSimpleCredential(s"whatsapp.${connektRequest.appName}").get

      val tracker = WARequestTracker(
        messageId = connektRequest.id,
        clientId = connektRequest.clientId,
        destination = connektRequest.channelInfo.asInstanceOf[WARequestInfo].destinations.head,
        appName = connektRequest.appName,
        contextId = connektRequest.contextId.getOrElse(""),
        connektRequest,
        meta = connektRequest.meta
      )

      val waPayload = WAPayloadFormatter.makePayload(connektRequest)
      val providerStencil = stencilService.getStencilsByName(s"wa-${connektRequest.appName}").find(_.component.equalsIgnoreCase("prepare")).get

      val result = stencilService.materialize(providerStencil, Map("data" -> connektRequest, "credentials" -> credentials, "tracker" -> tracker).getJsonNode)

      val httpRequests = result.asInstanceOf[util.LinkedHashMap[HttpRequest, String]].asScala.map { case (request, updatedTracker) =>
        (request , updatedTracker.getObj[WARequestTracker])
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
