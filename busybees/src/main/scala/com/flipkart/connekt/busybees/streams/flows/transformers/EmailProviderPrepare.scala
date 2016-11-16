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

import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import akka.http.scaladsl.model.headers.RawHeader
import com.flipkart.connekt.busybees.models.EmailRequestTracker
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.entities.{Stencil, StencilEngine}
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels.EmailPayloadEnvelope
import com.flipkart.connekt.commons.services.KeyChainManager
import com.flipkart.connekt.commons.utils.StringUtils._


class EmailProviderPrepare extends MapFlowStage[EmailPayloadEnvelope, (HttpRequest, EmailRequestTracker)] {

  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: (EmailPayloadEnvelope) => List[(HttpRequest, EmailRequestTracker)] = emailPayloadEnvelope => {

    val selectedProvider = emailPayloadEnvelope.provider.last
    val credentials = KeyChainManager.getSimpleCredential(s"email.${emailPayloadEnvelope.appName.toLowerCase}.$selectedProvider").get

    val tracker = EmailRequestTracker(messageId = emailPayloadEnvelope.messageId,
      clientId = emailPayloadEnvelope.clientId,
      to = emailPayloadEnvelope.payload.to.map(_.address),
      cc = emailPayloadEnvelope.payload.cc.map(_.address),
      appName = emailPayloadEnvelope.appName,
      contextId = emailPayloadEnvelope.contextId,
      meta = emailPayloadEnvelope.meta)

    //val providerStencil = stencilService.getStencilsByName(s"${emailPayloadEnvelope.appName.toLowerCase}-email-$selectedProvider").find(_.component.equalsIgnoreCase("prepare")).get
    val providerStencil = new Stencil("lorem-ipsum", StencilEngine.GROOVY,
      """
        |
        |package com.flipkart.connekt.commons.entities.fabric;
        |
        |import groovy.json.*
        |import com.fasterxml.jackson.databind.node.ObjectNode;
        |
        |import com.flipkart.connekt.commons.entities.fabric.GroovyFabric
        |import akka.http.scaladsl.model.*
        |import akka.http.scaladsl.model.headers.RawHeader
        |
        |
        |public class SendgridV3Groovy implements GroovyFabric {
        |
        |  public Object compute(String id, ObjectNode context) {
        |
        |    def data = context.get('data').get('payload')
        |    println(data)
        |
        |    def output = [:]
        |
        |    output['personalizations'] = []
        |
        |    def mail_one = [:]
        |
        |    mail_one['to'] = []
        |    data.get('to').each { eId ->
        |       mail_one['to'].push([email: eId.get('address').asText(), name: eId.get('name').asText()])
        |    }
        |
        |    if(data.get('cc')){
        |       mail_one['cc'] = []
        |       data.get('cc').each { eId ->
        |           mail_one['cc'].push([email: eId.get('address'), name: eId.get('name')])
        |       }
        |    }
        |
        |    if(data.get('bcc')){
        |       mail_one['bcc'] = []
        |       data.get('bcc').each { eId ->
        |           mail_one['bcc'].push([email: eId.get('address').asText(), name: eId.get('name').asText()])
        |       }
        |    }
        |
        |    output['personalizations'].push(mail_one)
        |
        |    output['from'] = [email: data.get('from').get('address').asText(), name: data.get('from').get('name').asText()]
        |    output['subject'] = data.get('data').get('subject').asText()
        |    output['content'] = []
        |
        |    output['content'].push([type: "text/plain", value: data.get('data').get('text').asText()])
        |    output['content'].push([type: "text/html", value: data.get('data').get('html').asText()])
        |
        |    def payload = new JsonBuilder(output).toString()
        |
        |    println(payload)
        |
        |    def uri = Uri$.MODULE$.apply("https://pkxpgttj1n.api.sendgrid.com/v3/mail/send")
        |    def requestEntity = HttpEntity$.MODULE$.apply(ContentTypes$.MODULE$.application$divjson(), payload)
        |    def httpRequest = new HttpRequest(HttpMethods.POST(), uri, scala.collection.immutable.Seq$.MODULE$.empty(), requestEntity, HttpProtocols$.MODULE$.HTTP$div1$u002E1())
        |      .addHeader(new RawHeader("Authorization", context.get('credentials').get("password").asText()))
        |
        |
        |    return httpRequest
        |
        |  }
        |}
      """.stripMargin)

    val result = stencilService.materialize(providerStencil, Map("data" -> emailPayloadEnvelope, "credentials" -> credentials).getJsonNode)

    val httpRequest = result.asInstanceOf[HttpRequest]
      .addHeader(RawHeader("x-message-id", emailPayloadEnvelope.messageId))
      .addHeader(RawHeader("x-context-id", emailPayloadEnvelope.contextId))

    List(Tuple2(httpRequest, tracker))
  }
}
