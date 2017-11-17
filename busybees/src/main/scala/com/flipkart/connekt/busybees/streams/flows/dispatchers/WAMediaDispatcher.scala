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

import java.io.{File, PrintWriter}
import java.nio.file.{Path, Paths}

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader}
import akka.parboiled2.util.Base64
import akka.stream.javadsl.FileIO
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.busybees.models.WARequestTracker
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.iomodels.{Attachment, ConnektRequest, WARequestData}
import com.flipkart.connekt.commons.metrics.Instrumented

//import scala.reflect.io.File

class WAMediaDispatcher extends MapFlowStage[ConnektRequest, (HttpRequest, WARequestTracker)] with Instrumented {
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
        //      val data = Base64.rfc2045().decode(attachment.base64Data)
        val fileName = s"/tmp/${attachment.name}"
        new PrintWriter(fileName) { write(attachment.base64Data); close() }

        val mediaPayload =
          s"""
             |{
             |	"payload":
             |	{
             |  		"filename": "${attachment.name}"
             |	}
             |}
        """.stripMargin
        val uri = "https://10.85.185.89:32785"
        val requestEntity = HttpEntity(ContentTypes.`application/json`, mediaPayload)
        val file = new File(fileName)
        //      val mediaEntity = HttpEntity(MediaTypes.`application/octet-stream`, file.toFile.length, FileIO.fromPath(Paths.get(fileName), 10000)) // the chunk size here is currently critical for performance
        val mediaEntity = HttpEntity(Base64.rfc2045().decode(attachment.base64Data)) // the chunk size here is currently critical for performance

        def httpRequest = HttpRequest(HttpMethods.POST, uri, scala.collection.immutable.Seq.empty[HttpHeader], requestEntity)
        httpRequest.withEntity(mediaEntity)
        List(httpRequest -> waRequestTracker)
      case _ => List()
    }
  }
}
