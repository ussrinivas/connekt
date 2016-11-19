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
import akka.stream.scaladsl._
import com.flipkart.connekt.busybees.models.OpenWebRequestTracker
import com.flipkart.connekt.busybees.streams.flows.RenderFlow
import com.flipkart.connekt.busybees.streams.flows.dispatchers.OpenWebDispatcherPrepare
import com.flipkart.connekt.busybees.streams.flows.formaters.OpenWebChannelFormatter
import com.flipkart.connekt.busybees.streams.flows.reponsehandlers.OpenWebResponseHandler
import com.flipkart.connekt.busybees.tests.streams.TopologyUTSpec
import com.flipkart.connekt.commons.entities.DeviceDetails
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.DeviceDetailsService
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.Await
import scala.concurrent.duration._

class OpenWebTopologyTest extends TopologyUTSpec {

  "OpenWeb TopologyTest" should "run" in {

    val deviceId1 = "Chrome-test-50+"
    val deviceId2 = "Firefox-test"
    val deviceId3 = "Chrome-test-50-"
    val appName = "fklite"

    DeviceDetailsService.add(
      DeviceDetails(
        deviceId = deviceId1,
        userId = "",
        token = "https://android.googleapis.com/gcm/send/eZa-9n7zepc:APA91bGuUxtUAR67d3woSNE9HqasFs_6NrOtv5kxlm6S_2pbWh58cdbu-k8zEekgUGSnIGtZYa8cij5LHVlpgBqARFVJf3FgiYfIIBuKzf01XVbzB8qB39GThAUFVue820zkunO7ccSY",
        osName = "openweb", osVersion = "6.0.1", appName, appVersion = "UT", brand = "UT", model = "",
        keys = Map("auth" -> "7NOAT1SnQH_GHXnhl2fffA==", "p256dh" -> "BBqvI-jmUWxwtGAKWJWyiC5x1K3PQMfZdjcgJv-Y4SA9ctrWDszn9C5DGB4HS9hwAzotJaWTSqAoYM7DzE_3QhY=")
      )
    )

    DeviceDetailsService.add(
      DeviceDetails(
        deviceId = deviceId2,
        userId = "",
        token = "https://updates.push.services.mozilla.com/wpush/v1/gAAAAABYGwbC68AGXw3dUJl5Heoo56ZNllKFYoQSSylqowsCGrUH1FffS_z7eEr3PFQ7uzADzK36sDn61Ic0W81H_89OWl_Wmie4wutsW7ilzBqMz-avWLbt1XjAHrFf5IPURRHVQ5r7",
        osName = "openweb", osVersion = "6.0.1", appName, appVersion = "UT", brand = "UT", model = "",
        keys = Map("auth" -> "l5CvA1vJhjV-Rxw-v50jyw", "p256dh" -> "BOhm3sh40fNM-JGBN1tQUN99Q8kWUNR1GeN8pYbDRYdvFTN9ZawTRMbFiK8xjc64mw5V9safeh_edsWwG7Otj28")
      )
    )

    DeviceDetailsService.add(
      DeviceDetails(
        deviceId = deviceId3,
        userId = "",
        token = "https://android.googleapis.com/gcm/send/eHW_Dj74uEM:APA91bEoKPRfR4VWK8bs5p5LqERfL9iG9RAZeqQvzSoE1wlF3N0varb5v6x8ryenmYXFyD-HJQ6M4t7V8lb3oaRqnV12X-1nSA5K8Yh0jw2dnHRHWyKvdfCZVR8puK_YZ7WNQe9ZwmQL",
        osName = "openweb", osVersion = "6.0.1", appName, appVersion = "UT", brand = "UT", model = "",
        keys = Map.empty
      )
    )

    val cRequest = s"""
                      |{
                      |	"channel": "PN",
                      |	"sla": "H",
                      | "clientId" : "connekt-insomnia",
                      |	"channelData": {
                      |		"type": "PN",
                      |		"data": {
                      |			"title": "Hello Nidhi",
                      |			"message": "Nidhi test for OpenWeb notification chrome."
                      |		}
                      |	},
                      |	"channelInfo": {
                      | "platform" : "openweb",
                      |		"appName": "fklite",
                      |		"type": "PN",
                      |		"ackRequired": true,
                      |		"delayWhileIdle": true,
                      |		"deviceIds": [ "$deviceId1"]
                      |	},
                      |	"meta": {
                      |	}
                      |}
                      |""".stripMargin.getObj[ConnektRequest]

    val openWebPoolClientFlow = Http().superPool[OpenWebRequestTracker]()

    val result = Source.single(cRequest)
      .via(new RenderFlow().flow)
      .via(new OpenWebChannelFormatter(64)(system.dispatchers.lookup("akka.actor.io-dispatcher")).flow)
      .via(new OpenWebDispatcherPrepare().flow)
      .via(openWebPoolClientFlow)
      .via(new OpenWebResponseHandler().flow)
      .runWith(Sink.head)

    val response = Await.result(result, 80.seconds)

    println(response)

    assert(response != null)

    Thread.sleep(15000)
  }
}
