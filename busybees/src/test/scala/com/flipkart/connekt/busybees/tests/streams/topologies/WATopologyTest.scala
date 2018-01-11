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
package com.flipkart.connekt.busybees.tests.streams.topologies

import akka.stream.scaladsl.{Sink, Source}
import com.flipkart.connekt.busybees.streams.flows.dispatchers.HttpDispatcher
import com.flipkart.connekt.busybees.streams.topologies.{WATopology}
import com.flipkart.connekt.busybees.tests.streams.TopologyUTSpec
import com.flipkart.connekt.commons.iomodels.ConnektRequest
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.Await
import scala.concurrent.duration._

class WATopologyTest extends TopologyUTSpec {

  "WA Topology Test" should "send image file" in {

    HttpDispatcher.init(ConnektConfig.getConfig("react").get)

    val imgBase64 = "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"
    val cRequest = s"""
                      |{
                      |  "id" : "212",
                      |  "channel": "wa",
                      |  "sla": "H",
                      |  "channelData" :{
                      |  		"type": "WA",
                      |     "waType" : "image",
                      |     "attachment": {
                      |         "base64Data":"$imgBase64",
                      |         "name":"fog.jpg",
                      |         "mime":"application/jpeg",
                      |         "caption":"This is your lovely Image"
                      |      }
                      |    },
                      |
                      |  "channelInfo": {
                      |   	"type" : "WA",
                      |     "appName": "flipkart",
                      |     "destinations": ["919343459079"]
                      |  },
                      |  "clientId" : "connekt-wa",
                      |  "meta": {}
                      |}
                   """.stripMargin.getObj[ConnektRequest]


    val result = Source.single(cRequest)
      .via(WATopology.waTransformFlow)
      .runWith(Sink.head)

    val response = Await.result(result, 120.seconds)
    println(response)

    assert(response != null)
  }

  "WA Topology Test" should "send hsm" in {

    HttpDispatcher.init(ConnektConfig.getConfig("react").get)

    val cRequest = s"""
                      |{
                      |  "id" : "213",
                      |  "channel": "wa",
                      |  "sla": "H",
                      |  "channelData" :{
                      |  		"type": "WA",
                      |     "waType" : "hsm"
                      |    },
                      |  "channelInfo": {
                      |   	"type" : "WA",
                      |     "appName": "flipkart",
                      |     "destinations": ["919343459079"]
                      |  },
                      |  "clientId" : "connekt-sms",
                      |  "stencilId" : "STNR9N6VD",
                      |  "channelDataModel": {
                      |    "orderId": "https://flipkart.com",
                      |    "pickupTime" : "Monday"
                      |  },
                      |  "meta": {}
                      |}
                   """.stripMargin.getObj[ConnektRequest]

    val result = Source.single(cRequest)
      .via(WATopology.waTransformFlow)
      .runWith(Sink.head)

    val response = Await.result(result, 120.seconds)
    println(response)

    assert(response != null)
  }

}
