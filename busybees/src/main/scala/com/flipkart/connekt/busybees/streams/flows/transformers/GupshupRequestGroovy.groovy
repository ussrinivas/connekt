package com.flipkart.connekt.busybees.streams.flows.transformers

import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.RawHeader
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonBuilder
import org.codehaus.jettison.json.JSONObject

class GupshupRequestGroovy {

  static compute(String id, ObjectNode context) {
    def data = context.get('data').get('payload')
    def credential = context.get('credentials')
    println(context.get('data'))

    def output = [:]

    output['v'] = "1.1"
    output['method'] = "sendMessage"
    output['auth_scheme'] = "PLAIN"
    output['userid'] = credential.get("username").asText()
    output['password'] = credential.get("password").asText()
    output['msg_type'] = data.get("messageType").asText()
    output['dvt'] = data.get("dvt").asText()
    output['isIntl'] = data.get("isIntl").asText()
    def receivers = data.get("receivers").elements()
    String numbers = ""
    receivers.eachWithIndex { JsonNode entry, int i ->
      numbers += entry.asText() + ","
    }
    output['send_to'] = numbers
    output['mask'] = data.get("senderMask").asText()
    output['msg'] = URLEncoder.encode(data.get("messageBody").get("body").asText(), "UTF-8")

    def jObject = new JSONObject(new JsonBuilder(output).toString())

    def uri = Uri$.MODULE$.apply("https://enterprise.smsgupshup.com/FlipKartGatewayAPI/rest").toString()

    Uri gupshupUri = appendUri(uri, jObject)

    def requestEntity = HttpEntity$.MODULE$.apply(ContentTypes$.MODULE$.application$divjson(), "")

    def httpRequest = new HttpRequest(HttpMethods.GET(), gupshupUri, scala.collection.immutable.Seq$.MODULE$.empty(), requestEntity, HttpProtocols$.MODULE$.HTTP$div1$u002E1())
      .addHeader(new RawHeader("X-MID", context.get('headers').get("X-MID").asText()))
      .addHeader(new RawHeader("X-CNAME", context.get('headers').get("X-CNAME").asText()))
      .addHeader(new RawHeader("X-TID", context.get('headers').get("X-TID").asText()))

    print(httpRequest)
    return httpRequest
  }

  static Uri appendUri(String uri, JSONObject jObject) throws URISyntaxException {
    URI oldUri = new URI(uri);

    String newQuery = oldUri.toString();

    for (String key : jObject.keys()) {
      String keyStr = (String) key;
      String keyvalue = jObject.get(keyStr);
      newQuery += "&" + keyStr + "=" + keyvalue;
    }

    Uri newUri = Uri$.MODULE$.apply(newQuery)

    return newUri;
  }

  static void main(String[] args) {

    ObjectNode onode = (ObjectNode) new ObjectMapper().readTree("{\n" +
      "    \"data\": {\n" +
      "        \"messageId\": null,\n" +
      "        \"clientId\": \"connekt-sms\",\n" +
      "        \"templateId\": \"\",\n" +
      "        \"receiver\": \"7760947385\",\n" +
      "        \"appName\": \"phonepe\",\n" +
      "        \"contextId\": \"\",\n" +
      "        \"payload\": {\n" +
      "            \"receivers\": [\"7760947385\",\"adsfafd\"],\n" +
      "            \"messageBody\": {\n" +
      "                \"type\": \"SMS\",\n" +
      "                \"body\": \"sending sms using gupshup\"\n" +
      "            },\n" +
      "            \"messageType\": \"Text\",\n" +
      "            \"senderMask\": \"FLPKRT\",\n" +
      "            \"dvt\": \"1\",\n" +
      "            \"isIntl\": \"0\"\n" +
      "        },\n" +
      "        \"meta\": {},\n" +
      "        \"headers\": {},\n" +
      "        \"provider\": [\n" +
      "            \"gupshup\"\n" +
      "        ]\n" +
      "    },\n" +
      "    \"credentials\": {\n" +
      "        \"username\": \"2000035120\",\n" +
      "        \"password\": \"ISuiZ6m8Y\",\n" +
      "        \"empty\": false\n" +
      "    },\n" +
      "    \"headers\": {\n" +
      "        \"X-MID\": null,\n" +
      "        \"X-CNAME\": \"connekt-sms\",\n" +
      "        \"X-TID\": \"\"\n" +
      "    }\n" +
      "}");


    compute('Hello World', onode)

  }
}
