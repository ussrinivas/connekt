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

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import com.flipkart.connekt.busybees.models.SmsRequestTracker
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.entities.{Channel, Stencil, StencilEngine}
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels.SmsPayloadEnvelope
import com.flipkart.connekt.commons.services.KeyChainManager
import com.flipkart.connekt.commons.utils.StringUtils._


class SmsProviderPrepare extends MapFlowStage[SmsPayloadEnvelope, (HttpRequest, SmsRequestTracker)] {

  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: (SmsPayloadEnvelope) => List[(HttpRequest, SmsRequestTracker)] = smsPayloadEnvelope => {

    val selectedProvider = smsPayloadEnvelope.provider.last
    val credentials = KeyChainManager.getSimpleCredential(s"${smsPayloadEnvelope.appName}-${Channel.SMS}.$selectedProvider").get

    val headers: Map[String, String] = Map("X-MID" -> "123234", "X-CNAME" -> smsPayloadEnvelope.clientId, "X-TID" -> smsPayloadEnvelope.templateId)

    val tracker = SmsRequestTracker(messageId = smsPayloadEnvelope.messageId,
      clientId = smsPayloadEnvelope.clientId,
      receivers = smsPayloadEnvelope.payload.receivers.split(",").toSet,
      provider = selectedProvider,
      appName = smsPayloadEnvelope.appName,
      contextId = smsPayloadEnvelope.contextId,
      request = smsPayloadEnvelope,
      meta = smsPayloadEnvelope.meta)

    //    val providerStencil = stencilService.getStencilsByName(s"${smsPayloadEnvelope.appName.toLowerCase}-sms-$selectedProvider").find(_.component.equalsIgnoreCase("prepare")).get
    val providerStencil = new Stencil("lorem-ipsum", StencilEngine.GROOVY,
      """
        |package com.flipkart.connekt.busybees.streams.flows.transformers
        |
        |import akka.http.scaladsl.model.*
        |import akka.http.scaladsl.model.headers.RawHeader
        |import com.fasterxml.jackson.databind.node.ObjectNode
        |import com.flipkart.connekt.commons.entities.fabric.GroovyFabric
        |import groovy.json.JsonBuilder
        |import org.codehaus.jettison.json.JSONObject
        |
        |public class GupshupGroovy implements GroovyFabric {
        |
        |  public Object compute(String id, ObjectNode context) {
        |    def data = context.get('data').get('payload')
        |    def credential = context.get('credentials')

        |    def output = [:]
        |
        |    output['v'] = "1.1"
        |    output['method'] = "sendMessage"
        |    output['auth_scheme'] = "PLAIN"
        |    output['format'] = "json"
        |    output['userid'] = credential.get("username").asText()
        |    output['password'] = credential.get("password").asText()
        |    output['msg_type'] = data.get("messageType").asText()
        |    output['dvt'] = data.get("dvt").asText()
        |    output['isIntl'] = data.get("isIntl").asText()
        |    output['send_to'] = data.get("receivers").asText()
        |    output['mask'] = data.get("senderMask").asText()
        |    output['msg'] = URLEncoder.encode(data.get("messageBody").get("body").asText(), "UTF-8")
        |
        |    def jObject = new JSONObject(new JsonBuilder(output).toString())
        |
        |    def uri = Uri$.MODULE$.apply("https://enterprise.smsgupshup.com/FlipKartGatewayAPI/rest").toString()
        |
        |    Uri gupshupUri = appendUri(uri, jObject)
        |
        |    def requestEntity = HttpEntity$.MODULE$.apply(ContentTypes$.MODULE$.application$divjson(), "")
        |
        |    def httpRequest = new HttpRequest(HttpMethods.GET(), gupshupUri, scala.collection.immutable.Seq$.MODULE$.empty(), requestEntity, HttpProtocols$.MODULE$.HTTP$div1$u002E1())
        |      .addHeader(new RawHeader("X-MID", context.get('headers').get("X-MID").asText()))
        |      .addHeader(new RawHeader("X-CNAME", context.get('headers').get("X-CNAME").asText()))
        |      .addHeader(new RawHeader("X-TID", context.get('headers').get("X-TID").asText()))
        |
        |    return httpRequest
        |  }
        |
        |  static Uri appendUri(String uri, JSONObject jObject) throws URISyntaxException {
        |    URI oldUri = new URI(uri);
        |
        |    String newQuery = oldUri.toString() + "?";
        |
        |    for (String key : jObject.keys()) {
        |      String keyStr = (String) key;
        |      String keyvalue = jObject.get(keyStr);
        |      newQuery += keyStr + "=" + keyvalue + "&";
        |    }
        |
        |    Uri newUri = Uri$.MODULE$.apply(newQuery)
        |
        |    return newUri;
        |  }
        |}
      """.stripMargin)

    val result = stencilService.materialize(providerStencil, Map("data" -> smsPayloadEnvelope, "credentials" -> credentials, "headers" -> headers).getJsonNode)

    val httpRequest = result.asInstanceOf[HttpRequest]
      .addHeader(RawHeader("x-message-id", smsPayloadEnvelope.messageId))
      .addHeader(RawHeader("x-context-id", smsPayloadEnvelope.contextId))

    println(s"Http request : $httpRequest")

    List(Tuple2(httpRequest, tracker))
  }
}
