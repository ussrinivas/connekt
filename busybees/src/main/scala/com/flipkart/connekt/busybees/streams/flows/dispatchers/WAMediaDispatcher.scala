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
import com.flipkart.connekt.busybees.models.{WAMediaRequestTracker}
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig

class WAMediaDispatcher extends MapFlowStage[ConnektRequest, (HttpRequest, WAMediaRequestTracker)] with Instrumented {
  private val baseUrl = ConnektConfig.getString("wa.base.uri").get
  private val mediaUploadUri = baseUrl + "/api/upload_outgoing_media.php"

  override val map: (ConnektRequest) => (List[(HttpRequest, WAMediaRequestTracker)]) = connektRequest => profile("map"){
    connektRequest.channelData.asInstanceOf[WARequestData].attachment match {
      case Some(attachment: Attachment) =>
        val waRequestTracker = WAMediaRequestTracker(connektRequest)

        val entity = createEntity(attachment, connektRequest.id)
        val httpRequest = HttpRequest(HttpMethods.POST, uri = mediaUploadUri, entity = entity)
        List(httpRequest -> waRequestTracker)
      case _ => List()
    }
  }

  private def createEntity(attachment: Attachment, id: String): RequestEntity = {
    val payload = WARequest(WAMediaPayload(s"${id}_${attachment.name}"))
    val data = Base64.rfc2045().decode(attachment.base64Data)

    val httpEntity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, ByteString(data))
    val fileFormData = Multipart.FormData.BodyPart.Strict("file", httpEntity, Map("filename" -> attachment.name))
    val jsonFormData = Multipart.FormData.BodyPart.Strict("json_query", payload.getJson, Map.empty)

    Multipart.FormData(jsonFormData, fileFormData).toEntity()
  }

}
