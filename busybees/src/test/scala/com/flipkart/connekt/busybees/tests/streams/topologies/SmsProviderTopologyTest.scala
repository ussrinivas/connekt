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
import com.flipkart.connekt.busybees.streams.topologies.SmsTopology
import com.flipkart.connekt.busybees.tests.streams.TopologyUTSpec
import com.flipkart.connekt.commons.iomodels.ConnektRequest
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.Await
import scala.concurrent.duration._

class SmsProviderTopologyTest extends TopologyUTSpec {

  "SMS Provider Topology Test" should "run" in {

    HttpDispatcher.init(ConnektConfig.getConfig("react").get)

    val cRequest = s"""
                      |{
                      |  "id" : "212",
                      |  "channel": "SMS",
                      |  "sla": "H",
                      |  "channelData" :{
                      |  		"type": "SMS",
                      |      "body": "sending sms using gupshup https://www.flipkart.com/bpl-vivid-80cm-32-hd-ready-led-tv/p/itmeepv8jxfjmhkh?pid=TVSEEPV8C6JTWZFW&fm=merchandising&iid=M_ed3d80d5-0a06-4ade-b601-25fd0b45e6fe.e346afa1-a20b-452e-884f-a886b4c8e2a3&otracker=hp_omu_Deals+on+TVs+and+Appliances_2_e346afa1-a20b-452e-884f-a886b4c8e2a3_e346afa1-a20b-452e-884f-a886b4c8e2a3_0 "
                      |    },
                      |
                      |  "channelInfo": {
                      |   	"type" : "SMS",
                      |   	"appName" : "flipkart",
                      |     "receivers": ["+917760947385"]
                      |  },
                      |  "clientId" : "connekt-sms",
                      |
                      |  "meta": {}
                      |}
                   """.stripMargin.getObj[ConnektRequest]


    val result = Source.single(cRequest)
      .via(SmsTopology.smsTransformFlow)
      .runWith(Sink.head)

    val response = Await.result(result, 120.seconds)
    println(response)

    assert(response != null)
  }

}
