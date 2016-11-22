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

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import com.flipkart.connekt.busybees.models.{EmailRequestTracker, EmailResponse}
import com.flipkart.connekt.busybees.streams.flows.MapAsyncFlowStage
import com.flipkart.connekt.commons.entities.{Stencil, StencilEngine}
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try
import com.flipkart.connekt.commons.core.Wrappers._


class EmailProviderResponseFormatter(implicit m: Materializer, ec: ExecutionContext) extends MapAsyncFlowStage[(Try[HttpResponse], EmailRequestTracker), (Try[EmailResponse], EmailRequestTracker)](96) with Instrumented {

  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: ((Try[HttpResponse], EmailRequestTracker)) => Future[List[(Try[EmailResponse], EmailRequestTracker)]] = responseTrackerPair => Future(profile("map") {

    //val providerResponseHandlerStencil = stencilService.getStencilsByName(s"${emailPayloadEnvelope.appName.toLowerCase}-email-$selectedProvider").find(_.component.equalsIgnoreCase("parse")).get
    val providerResponseHandlerStencil = new Stencil("handler-sendgrid", StencilEngine.GROOVY,
      """
        |
        |package com.flipkart.connekt.commons.entities.fabric;
        |
        |import groovy.json.*
        |import com.fasterxml.jackson.databind.node.ObjectNode;
        |
        |import com.flipkart.connekt.commons.entities.fabric.GroovyFabric
        |import com.flipkart.connekt.commons.entities.fabric.EngineFabric
        |import com.flipkart.connekt.busybees.models.EmailResponse
        |
        |
        |public class SendgridV3ResponseGroovy implements GroovyFabric , EngineFabric  {
        |
        |  public Object compute(String id, ObjectNode context) {
        |
        |    println(context)
        |
        |    def messageIdObject = context.get('headers').find { hR ->
        |       hR.get('lowercaseName') != null && hR.get('lowercaseName').asText()  == "x-message-id"
        |    }
        |
        |    def messageId =  messageIdObject != null ? messageIdObject.get('value').asText() : null
        |
        |    def statusCode = context.get('statusCode').asInt()
        |
        |
        |    return new EmailResponse("sendgrid", messageId,statusCode, "")
        |
        |  }
        |}
      """.stripMargin)



    val emailResponse = responseTrackerPair._1.flatMap(hR => Try_{
      val httpResponse = Await.result(hR.toStrict(30.seconds), 5.seconds)

      val result = stencilService.materialize(providerResponseHandlerStencil,Map("statusCode" -> httpResponse._1.intValue(), "headers" -> httpResponse.headers, "body" -> httpResponse._3.getString).getJsonNode).asInstanceOf[EmailResponse]

      assert(result != null, "Provider Parser Failed, NULL Returned")

      result
    })

    List(emailResponse -> responseTrackerPair._2)
  })(m.executionContext)

}
