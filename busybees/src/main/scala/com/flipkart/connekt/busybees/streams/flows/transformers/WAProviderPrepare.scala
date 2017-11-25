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
import com.flipkart.connekt.busybees.streams.flows.formaters.WAPayloadFormatter
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
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
            (httpRequest -> tracker)
        }
      }).toList
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
          "WAProviderPrepare::".concat(e.getMessage),
          e
        )
    }
  }

}

