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
import com.flipkart.connekt.busybees.streams.flows.transformers.{SmsProviderPrepare, SmsProviderResponseFormatter}
import com.flipkart.connekt.busybees.streams.flows.{ChooseProvider, RenderFlow}
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
                      |      "body": "sending sms using gupshup"
                      |    },
                      |
                      |  "channelInfo": {
                      |   	"type" : "SMS",
                      |   	"appName" : "FLIPKART",
                      |     "sender": "FLPKRT",
                      |     "receivers": ["7760947385"]
                      |  },
                      |  "clientId" : "connekt-sms",
                      |
                      |  "meta": {}
                      |}
                   """.stripMargin.getObj[ConnektRequest]


    val result = Source.single(cRequest)
      .via(new RenderFlow().flow)
      .via(new SmsChannelFormatter(64)(system.dispatchers.lookup("akka.actor.io-dispatcher")).flow)
      .via(new ChooseProvider(Channel.SMS).flow)
      .via(new SmsProviderPrepare().flow)
      .via(poolClientFlow)
      .via(new SmsProviderResponseFormatter().flow)
      .runWith(Sink.foreach(println))

    val response = Await.result(result, 80.seconds)
    println(response)

    assert(response != null)
  }

}
