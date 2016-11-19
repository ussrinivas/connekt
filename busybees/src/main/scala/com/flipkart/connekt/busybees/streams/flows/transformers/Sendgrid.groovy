package com.flipkart.connekt.busybees.streams.flows.transformers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Created by ayush.agarwal on 17/11/16.
 */

import groovy.json.*
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.flipkart.connekt.commons.entities.fabric.GroovyFabric
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.RawHeader


public class SendgridV3Groovy  {

  static compute(String id, ObjectNode context) {

    def data = context.get('data').get('payload')
    println(data)

    def output = [:]

    output['personalizations'] = []

    def mail_one = [:]

    mail_one['to'] = []
    data.get('to').each { eId ->
      mail_one['to'].push([email: eId.get('address').asText(), name: eId.get('name').asText()])
    }

    if (data.get('cc')) {
      mail_one['cc'] = []
      data.get('cc').each { eId ->
        mail_one['cc'].push([email: eId.get('address'), name: eId.get('name')])
      }
    }

    if (data.get('bcc')) {
      mail_one['bcc'] = []
      data.get('bcc').each { eId ->
        mail_one['bcc'].push([email: eId.get('address').asText(), name: eId.get('name').asText()])
      }
    }

    output['personalizations'].push(mail_one)

    output['from'] = [email: data.get('from').get('address').asText(), name: data.get('from').get('name').asText()]
    output['subject'] = data.get('data').get('subject').asText()
    output['content'] = []

    output['content'].push([type: "text/plain", value: data.get('data').get('text').asText()])
    output['content'].push([type: "text/html", value: data.get('data').get('html').asText()])

    def payload = new JsonBuilder(output).toString()

    println(payload)

    def uri = Uri$.MODULE$.apply("https://pkxpgttj1n.api.sendgrid.com/v3/mail/send")
    def requestEntity = HttpEntity$.MODULE$.apply(ContentTypes$.MODULE$.application$divjson(), payload)
    def httpRequest = new HttpRequest(HttpMethods.POST(), uri, scala.collection.immutable.Seq$.MODULE$.empty(), requestEntity, HttpProtocols$.MODULE$.HTTP$div1$u002E1())
      .addHeader(new RawHeader("Authorization", context.get('credentials').get("password").asText()))


    print(httpRequest)

  }

  static void main(String[] args) {
    // Using a simple println statement to print output to the console
    ObjectMapper mapper = new ObjectMapper();

    ObjectNode onode = (ObjectNode) new ObjectMapper().readTree("{\n" +
      "    \"data\": {\n" +
      "        \"payload\": {\n" +
      "            \"from\": {\n" +
      "                \"address\": \"ayush@gmail.com\",\n" +
      "                \"name\": \"ayush\"\n" +
      "            },\n" +
      "            \"data\": {\n" +
      "                \"subject\": \"subject\",\n" +
      "                \"text\": \"shit\",\n" +
      "                \"html\": \"html\"\n" +
      "            }\n" +
      "        }\n" +
      "    },\n" +
      "    \"credentials\": {\n" +
      "        \"password\": \"Adfad\"\n" +
      "    }\n" +
      "}");


    compute('Hello World', onode)
  }

}
