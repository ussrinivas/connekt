package com.flipkart.connekt.busybees.streams.flows.transformers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.models.ResponsePerReceiver
import com.flipkart.connekt.busybees.models.SmsResponse
import scala.collection.immutable.List
import scala.collection.immutable.Nil$

class GupshupResponseHandlerGroovy {
  static Object compute(String id, ObjectNode context) {

    def body = context.get('body')
    def statusCode = context.get('statusCode').asInt()
    def messageLength = context.get('messageLength').asInt()
    List<ResponsePerReceiver> list = Nil$.MODULE$;

    if (body.has("data")) {
      def data = body.get("data")
      def responseMessages = data.get("response_messages").elements()
      while (responseMessages.hasNext()) {
        def receiver = responseMessages.next()
        list = list.$colon$colon(new ResponsePerReceiver(receiver.get("status").asText().trim(), receiver.get("phone").asText().trim(),
          receiver.get("id").asText().trim(), receiver.get("details").asText().trim(), getCode(receiver.get("status").asText())))
      }

    } else {
      def responseMessages = body.get("response")
      list = list.$colon$colon(new ResponsePerReceiver(responseMessages.get("status").asText().trim(), responseMessages.get("phone").asText().trim(),
        responseMessages.get("id").asText().trim(), responseMessages.get("details").asText().trim(), getCode(responseMessages.get("status").asText())))
    }

    new SmsResponse(messageLength, statusCode, "", list)
  }

  static int getCode(String status) {
    if (status.trim().equalsIgnoreCase("success")) {
      return 200
    } else {
      return 422
    }
  }

  static void main(String[] args) {

    ObjectNode onode = (ObjectNode) new ObjectMapper().readTree("{\n" +
      "    \"statusCode\": 200,\n" +
      "    \"messageLength\": 116,\n" +
      "    \"body\": {\n" +
      "        \"response\": {\n" +
      "            \"id\": \"3190770177537081446-171847820064190579\",\n" +
      "            \"phone\": \"917760947385\",\n" +
      "            \"details\": \"\",\n" +
      "            \"status\": \"success\"\n" +
      "        }\n" +
      "    }\n" +
      "}");

    compute('Hello World', onode)
  }
}
