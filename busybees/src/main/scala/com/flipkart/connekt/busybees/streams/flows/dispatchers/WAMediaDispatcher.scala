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
import com.flipkart.connekt.busybees.models.WARequestTracker
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.{Attachment, ConnektRequest, WARequestData}
import com.flipkart.connekt.commons.metrics.Instrumented

class WAMediaDispatcher extends MapFlowStage[ConnektRequest, (HttpRequest, WARequestTracker)] with Instrumented {

  def createEntity(payload: String, attachment: Attachment): RequestEntity = {
    val data = Base64.rfc2045().decode(attachment.base64Data)

    val httpEntity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, ByteString(data))
    val fileFormData = Multipart.FormData.BodyPart.Strict("file", httpEntity, Map("filename" -> attachment.name))
    val jsonFormData = Multipart.FormData.BodyPart.Strict("json_query", payload, Map.empty)

    Multipart.FormData(jsonFormData, fileFormData).toEntity()
  }


  override val map: (ConnektRequest) => (List[(HttpRequest, WARequestTracker)]) = connektRequest => profile("map"){
    connektRequest.channelData.asInstanceOf[WARequestData].attachment match {
      case Some(attachment: Attachment) =>
        val waRequestTracker = WARequestTracker(
          connektRequest.id,
          connektRequest.clientId,
          connektRequest.destinations.head,
          connektRequest.appName,
          connektRequest.contextId.getOrElse(""),
          connektRequest,
          connektRequest.meta
        )

        val mediaPayload =
          s"""
             |{
             |	"payload":
             |	{
             |  		"filename": "${attachment.name}"
             |	}
             |}
        """.stripMargin
        val uri = "https://10.85.185.89:32785/api/upload_outgoing_media.php"

        val entity = createEntity(mediaPayload, attachment)
        val httpRequest = HttpRequest(HttpMethods.POST, uri = uri, entity = entity)
        List(httpRequest -> waRequestTracker)
      case _ => List()
    }
  }
}
