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

import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Sink, Source}
import com.flipkart.connekt.busybees.models.SmsRequestTracker
import com.flipkart.connekt.busybees.streams.flows.formaters.SmsChannelFormatter
import com.flipkart.connekt.busybees.streams.flows.reponsehandlers.SmsResponseHandler
import com.flipkart.connekt.busybees.streams.flows.transformers.{SmsProviderPrepare, SmsProviderResponseFormatter}
import com.flipkart.connekt.busybees.streams.flows.{ChooseProvider, RenderFlow, SMSTrackingFlow, TrackingFlow}
import com.flipkart.connekt.busybees.tests.streams.TopologyUTSpec
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.iomodels.ConnektRequest
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.Await
import scala.concurrent.duration._

class SmsProviderTopologyTest extends TopologyUTSpec {

  "SMS Provider Topology Test" should "run" in {

    lazy implicit val poolClientFlow = Http().superPool[SmsRequestTracker]()


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
      .via(new RenderFlow().flow)
      .via(new SMSTrackingFlow(4).flow)
      .via(new SmsChannelFormatter(64)(system.dispatchers.lookup("akka.actor.io-dispatcher")).flow)
      .via(new ChooseProvider(Channel.SMS).flow)
      .via(new SmsProviderPrepare().flow)
      .via(poolClientFlow)
      .via(new SmsProviderResponseFormatter().flow)
      .via(new SmsResponseHandler().flow)
      .runWith(Sink.foreach(println))

    val response = Await.result(result, 800.seconds)
    println(response)

    assert(response != null)
  }

}
