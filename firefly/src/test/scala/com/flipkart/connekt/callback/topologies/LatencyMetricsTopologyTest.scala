package com.flipkart.connekt.callback.topologies

import akka.stream.scaladsl.Source
import com.flipkart.connekt.busybees.tests.streams.TopologyUTSpec
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.iomodels.SmsCallbackEvent
import com.flipkart.connekt.firefly.sinks.metrics.LatencyMetrics

/**
  * Created by grishma.s on 11/10/17.
  */
class LatencyMetricsTopologyTest extends TopologyUTSpec with Instrumented {
  val latency = new LatencyMetrics()
//  "HbaseLookupTopology Test" should "run" in {
//    val smsCallback = SmsCallbackEvent(messageId = "28a017b0-5621-4a84-9974-86a188c66ff5",
//      eventType = "sms_delivered",
//      receiver = "+918100989832",
//      clientId = "affordability",
//      appName = "flipkart",
//      contextId = "",
//      cargo = "{\"deliveredTS\":\"1509698165000\",\"cause\":\"SUCCESS\",\"externalId\":\"3440781747693252725-262142534416137710\",\"provider\":\"sinfini\",\"errCode\":\"000\"}",
//      timestamp = 1510294552014L,
//      eventId = "iaUAuOefuD")
//
//    Source.single(smsCallback).runWith(latency.sink)
//    Thread.sleep(20000)
//  }
//  "HbaseLookupTopology Test" should "cargo blank" in {
//    val smsCallback = SmsCallbackEvent(messageId = "2f9d1549-c842-44ff-932c-5526aa2bed24",
//      eventType = "sms_delivered",
//      receiver = "+918100989832",
//      clientId = "affordability",
//      appName = "flipkart",
//      contextId = "",
//      cargo = "",
//      timestamp = 1510294552014L,
//      eventId = "iaUAuOefuD")
//
//    Source.single(smsCallback).runWith(latency.sink)
//    Thread.sleep(20000)
//  }
  "HbaseLookupTopology Test" should "run" in {
    val smsCallback = SmsCallbackEvent(messageId = "3ce2de85-be4f-4ccb-a91b-1ce8b424ab4c",
      eventType = "sms_delivered",
      receiver = "+918885472168",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "{\"deliveredTS\":\"1509698165000\",\"cause\":\"SUCCESS\",\"externalId\":\"3440781747693252725-262142534416137710\",\"provider\":\"gupshup\",\"errCode\":\"000\"}",
      timestamp = 1509698165000L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
  "HbaseLookupTopology Test" should "cargo blank" in {
    val smsCallback = SmsCallbackEvent(messageId = "0076fa0f-1685-4bbe-aef8-41723668e597",
      eventType = "sms_delivered",
      receiver = "+918885472168",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "",
      timestamp = 1509625346666L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
  "HbaseLookupTopology Test" should "cargo any" in {
    val smsCallback = SmsCallbackEvent(messageId = "089dae77-051d-4146-ad25-ea86ed761608",
      eventType = "sms_delivered",
      receiver = "+918885472168",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "{\"provider\": \"sinfini\"}",
      timestamp = 1509800169441L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
  "HbaseLookupTopology Test" should "cargo null" in {
    val smsCallback = SmsCallbackEvent(messageId = "18ec4832-f1f2-4ccb-9f80-9a366b9b986e",
      eventType = "sms_delivered",
      receiver = "+918885472168",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = null,
      timestamp = 1509621577000L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
  "HbaseLookupTopology Test" should "cargo excep" in {
    val smsCallback = SmsCallbackEvent(messageId = "1c4f3564-3687-4f72-a43d-f75dd111d0",
      eventType = "sms_delivered",
      receiver = "+918885472168",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "RenderFlow - Java.lang",
      timestamp = 1509614097879L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
}
