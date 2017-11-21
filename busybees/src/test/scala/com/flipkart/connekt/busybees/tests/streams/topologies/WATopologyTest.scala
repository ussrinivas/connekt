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
                      |         "name":"meme.jpg",
                      |         "mime":"application/jpeg",
                      |         "caption":"This is your lovely Image"
                      |      }
                      |    },
                      |
                      |  "channelInfo": {
                      |   	"type" : "WA",
                      |     "destinations": ["919343459079"]
                      |  },
                      |  "clientId" : "connekt-sms",
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
