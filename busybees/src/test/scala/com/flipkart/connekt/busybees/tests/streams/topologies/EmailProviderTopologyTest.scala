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
import com.flipkart.connekt.busybees.models.EmailRequestTracker
import com.flipkart.connekt.busybees.streams.flows.formaters.EmailChannelFormatter
import com.flipkart.connekt.busybees.streams.flows.reponsehandlers.EmailResponseHandler
import com.flipkart.connekt.busybees.streams.flows.transformers.{EmailProviderPrepare, EmailProviderResponseFormatter}
import com.flipkart.connekt.busybees.streams.flows.{ChooseProvider, EmailTrackingFlow, RenderFlow, TrackingFlow}
import com.flipkart.connekt.busybees.tests.streams.TopologyUTSpec
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.iomodels.ConnektRequest
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.Await
import scala.concurrent.duration._

class EmailProviderTopologyTest extends TopologyUTSpec {

  "Email Provider Topology Test" should "run" in {

    lazy implicit val poolClientFlow = Http().superPool[EmailRequestTracker]()

    val cRequest = s"""
                      |{ "id" : "123456789",
                      |	"channel": "EMAIL",
                      |	"sla": "H",
                      |	"channelData": {
                      |		"type": "EMAIL",
                      |		"subject": "Hello Kinshuk with url and attachment. GoodLuck!",
                      |		"text": "Text",
                      |   "html" : "<b>html</b>  <a href='http://www.google.com'>link</a>",
                      |    "attachments" : [{
                      |       "base64Data" : "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
                      |       "name" : "one.png",
                      |       "mime" : "image/png"
                      |    }]
                      |	},
                      |	"channelInfo" : {
                      |	    "type" : "EMAIL",
                      |     "appName" : "phonepe",
                      |     "to" : [{ "name": "Kinshuk", "address": "kinshuk.bairagi@flipkart.com" }]
                      |	},
                      |  "clientId" : "123456",
                      |	"meta": {}
                      |}
                   """.stripMargin.getObj[ConnektRequest]

    val result = Source.single(cRequest)
      .via(new RenderFlow().flow)
      .via(new EmailTrackingFlow(4).flow)
      .via(new EmailChannelFormatter(64)(system.dispatchers.lookup("akka.actor.io-dispatcher")).flow)
      .via(new ChooseProvider(Channel.EMAIL).flow)
      .via(new EmailProviderPrepare().flow)
      .via(poolClientFlow)
      .via(new EmailProviderResponseFormatter().flow)
      .via(new EmailResponseHandler().flow)
      .runWith(Sink.foreach(println))

    val response = Await.result(result, 80.seconds)

    println(response)

    assert(response != null)
  }

}
