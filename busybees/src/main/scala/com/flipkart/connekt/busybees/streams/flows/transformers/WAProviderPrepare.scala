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
import com.flipkart.connekt.commons.utils.StringUtils._

class WAProviderPrepare extends MapFlowStage[ConnektRequest, (HttpRequest, WARequestTracker)] {

  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: ConnektRequest => List[(HttpRequest, WARequestTracker)] = connektRequest => profile("map") {

    try {
//      val credentials = KeyChainManager.getSimpleCredential(s"whatsapp.${connektRequest.appName}").get

      val tracker = WARequestTracker(
        messageId = connektRequest.id,
        clientId = connektRequest.clientId,
        destination = connektRequest.channelInfo.asInstanceOf[WARequestInfo].destinations.head,
        appName = connektRequest.appName,
        contextId = connektRequest.contextId.getOrElse(""),
        connektRequest,
        meta = connektRequest.meta
      )

      val waPayloads = WAPayloadFormatter.makePayload(connektRequest)
      val uri = "https://10.85.185.89:32785/api/rest_send.php"
      waPayloads.map( waPayload => {
        val requestEntity = HttpEntity(ContentTypes.`application/json`, waPayload.getJson)
        def httpRequest = HttpRequest(HttpMethods.POST, uri, scala.collection.immutable.Seq.empty[HttpHeader], requestEntity)
        (httpRequest -> tracker)
      })
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

