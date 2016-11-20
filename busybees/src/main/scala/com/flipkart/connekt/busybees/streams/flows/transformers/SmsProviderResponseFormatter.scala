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
import com.flipkart.connekt.busybees.models.{SmsRequestTracker, SmsResponse}
import com.flipkart.connekt.busybees.streams.flows.MapAsyncFlowStage
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.{Stencil, StencilEngine}
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try


class SmsProviderResponseFormatter(implicit m: Materializer, ec: ExecutionContext) extends MapAsyncFlowStage[(Try[HttpResponse], SmsRequestTracker), (Try[SmsResponse], SmsRequestTracker)](96) with Instrumented {

  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: ((Try[HttpResponse], SmsRequestTracker)) => Future[List[(Try[SmsResponse], SmsRequestTracker)]] = responseTrackerPair => Future(profile("map") {

    //val providerResponseHandlerStencil = stencilService.getStencilsByName(s"${smsPayloadEnvelope.appName.toLowerCase}-sms-$selectedProvider").find(_.component.equalsIgnoreCase("parse")).get
    val providerResponseHandlerStencil = new Stencil("handler-gupshup", StencilEngine.GROOVY,
      """
        |
        |package com.flipkart.connekt.commons.entities.fabric;
        |
        |import groovy.json.*
        |import com.fasterxml.jackson.databind.node.ObjectNode;
        |
        |import com.flipkart.connekt.commons.entities.fabric.GroovyFabric
        |import com.flipkart.connekt.commons.entities.fabric.EngineFabric
        |import com.flipkart.connekt.busybees.models.SmsResponse
        |
        |
        |public class GupshupResponseGroovy implements GroovyFabric , EngineFabric  {
        |
        |  public Object compute(String id, ObjectNode context) {
        |
        |    def response = context.get("body").asText().split("\\|")
        |    return new SmsResponse( response[0].trim(), response[1].trim(), response[2].trim(), context.get("messageLength").asInt(), context.get("statusCode").asInt(), "")
        |
        |  }
        |}
      """.stripMargin)

    val smsResponse = responseTrackerPair._1.flatMap(hR => Try_ {
      val httpResponse = Await.result(hR.toStrict(30.seconds), 5.seconds)

      val result = stencilService.materialize(providerResponseHandlerStencil, Map("statusCode" -> httpResponse._1.intValue(), "messageLength" -> httpResponse.entity.getContentLengthOption().getAsLong, "headers" -> httpResponse.headers, "body" -> httpResponse._3.getString).getJsonNode).asInstanceOf[SmsResponse]

      assert(result != null, "Provider Parser Failed, NULL Returned")

      result
    })

    List(smsResponse -> responseTrackerPair._2)
  })(m.executionContext)

}
