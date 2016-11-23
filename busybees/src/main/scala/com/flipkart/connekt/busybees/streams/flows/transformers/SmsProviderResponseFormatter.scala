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
import com.fasterxml.jackson.databind.node.ObjectNode
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
        |package com.flipkart.connekt.busybees.streams.flows.transformers
        |
        |import com.fasterxml.jackson.databind.ObjectMapper
        |import com.fasterxml.jackson.databind.node.ObjectNode
        |import com.flipkart.connekt.busybees.models.ResponsePerReceiver
        |import com.flipkart.connekt.busybees.models.SmsResponse
        |import scala.collection.immutable.List
        |import scala.collection.immutable.Nil$
        |import com.flipkart.connekt.commons.entities.fabric.GroovyFabric
        |
        |public class GupshupResponseHandlerGroovy implements GroovyFabric{
        |  public Object compute(String id, ObjectNode context) {
        |
        |    def body = context.get('body')
        |    def statusCode = context.get('statusCode').asInt()
        |    def messageLength = context.get('messageLength').asInt()
        |    List<ResponsePerReceiver> list = Nil$.MODULE$;
        |
        |    if (body.has("data")) {
        |      def data = body.get("data")
        |      def responseMessages = data.get("response_messages").elements()
        |      while (responseMessages.hasNext()) {
        |        def receiver = responseMessages.next()
        |        list = list.$colon$colon(new ResponsePerReceiver(receiver.get("status").asText().trim(), receiver.get("phone").asText().trim(),
        |          receiver.get("id").asText().trim(), receiver.get("details").asText().trim(), getCode(receiver.get("status").asText())))
        |      }
        |    } else {
        |      def response = body.get("response")
        |      list = list.$colon$colon(new ResponsePerReceiver(response.get("status").asText().trim(), response.get("phone").asText().trim(),
        |        response.get("id").asText().trim(), response.get("details").asText().trim(), getCode(response.get("status").asText())))
        |    }
        |
        |    new SmsResponse(messageLength, statusCode, "", list)
        |  }
        |
        |  public int getCode(String status) {
        |    if (status.trim().equalsIgnoreCase("success")) {
        |      return 200
        |    } else {
        |      return 422
        |    }
        |  }
        |}
        |
      """.stripMargin)

    val tracker = responseTrackerPair._2
    val smsResponse = responseTrackerPair._1.flatMap(hR => Try_ {
      val httpResponse = Await.result(hR.toStrict(30.seconds), 5.seconds)

      val result = stencilService.materialize(providerResponseHandlerStencil, Map("statusCode" -> httpResponse._1.intValue(),
        "messageLength" -> tracker.request.payload.messageBody.body.length,
        "body" -> httpResponse.entity.getString.getObj[ObjectNode]).getJsonNode).asInstanceOf[SmsResponse]

      assert(result != null, "Provider Parser Failed, NULL Returned")

      result
    })

    List(smsResponse -> responseTrackerPair._2)
  })(m.executionContext)

}
