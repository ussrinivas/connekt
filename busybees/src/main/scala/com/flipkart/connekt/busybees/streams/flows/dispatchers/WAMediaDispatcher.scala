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
package com.flipkart.connekt.busybees.streams.flows.dispatchers


import akka.http.scaladsl.model.{HttpEntity, _}
import akka.parboiled2.util.Base64
import akka.util.ByteString
import com.flipkart.connekt.busybees.models.WAMediaRequestTracker
import com.flipkart.connekt.busybees.streams.errors.ConnektStageException
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig

class WAMediaDispatcher extends MapFlowStage[ConnektRequest, (HttpRequest, WAMediaRequestTracker)] with Instrumented {
  private val baseUrl = ConnektConfig.getString("wa.base.uri").get
  private val mediaUploadUri = baseUrl + "/api/upload_outgoing_media.php"

  override val map: (ConnektRequest) => (List[(HttpRequest, WAMediaRequestTracker)]) = connektRequest => profile("map"){
    try {
    connektRequest.channelData.asInstanceOf[WARequestData].attachment match {
      case Some(attachment: Attachment) =>
        val waRequestTracker = WAMediaRequestTracker(connektRequest.id, connektRequest)
        val httpRequest = HttpRequest(HttpMethods.POST, mediaUploadUri, entity = createEntity(attachment))
        List(httpRequest -> waRequestTracker)
      case _ => List()
    }} catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"WAMediaDispatcher error for ${connektRequest.id}", e)
        throw ConnektStageException(connektRequest.id, connektRequest.clientId, connektRequest.destinations, InternalStatus.StageError, connektRequest.appName, Channel.WA, connektRequest.contextId.orEmpty, connektRequest.meta, "WAMediaDispatcher::".concat(e.getMessage), e)
    }
  }

  private def createEntity(attachment: Attachment): RequestEntity = {
    val payload = WARequest(WAMediaPayload(attachment.name))
    val data = Base64.rfc2045().decode(attachment.base64Data)

    val httpEntity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, ByteString(data))
    val fileFormData = Multipart.FormData.BodyPart.Strict("file", httpEntity, Map("filename" -> attachment.name))
    val jsonFormData = Multipart.FormData.BodyPart.Strict("json_query", payload.getJson, Map.empty)

    Multipart.FormData(jsonFormData, fileFormData).toEntity()
  }

}
