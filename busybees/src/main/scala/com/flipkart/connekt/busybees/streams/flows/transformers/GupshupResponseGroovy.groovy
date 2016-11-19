package com.flipkart.connekt.commons.entities.fabric

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.models.SmsResponse

public class GupshupResponseGroovy   {

  static compute(String id, ObjectNode context) {

    def response = context.get("body").asText().split("\\|")

    print(response)
    return new SmsResponse( response[0], response[1], response[2])

  }

  static void main(String[] args) {

    ObjectNode onode = (ObjectNode) new ObjectMapper().readTree("{\n" +
      "    \"statusCode\": 200,\n" +
      "    \"headers\": [\n" +
      "        {\n" +
      "            \"products\": [\n" +
      "                {\n" +
      "                    \"product\": \"Apache-Coyote\",\n" +
      "                    \"version\": \"1.1\",\n" +
      "                    \"comment\": \"\"\n" +
      "                }\n" +
      "            ]\n" +
      "        },\n" +
      "        {\n" +
      "            \"date\": {\n" +
      "                \"year\": 2016,\n" +
      "                \"month\": 11,\n" +
      "                \"day\": 17,\n" +
      "                \"hour\": 18,\n" +
      "                \"minute\": 58,\n" +
      "                \"second\": 48,\n" +
      "                \"weekday\": 4,\n" +
      "                \"clicks\": 1479409128000,\n" +
      "                \"isLeapYear\": true\n" +
      "            }\n" +
      "        }\n" +
      "    ],\n" +
      "    \"body\": \"success | 917760947385 | 3186811217203757173-74130518786549315\\n\\n\"\n" +
      "}");


    compute('Hello World', onode)

  }
}
