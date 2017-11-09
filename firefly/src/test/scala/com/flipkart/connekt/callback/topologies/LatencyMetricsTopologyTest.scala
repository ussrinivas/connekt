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
  "HbaseLookupTopology Test" should "run" in {
    val smsCallback = SmsCallbackEvent(messageId = "6f55f6bc-8737-4b26-b40c-eb4078ade202",
      eventType = "sms_delivered",
      receiver = "+919842399355",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "{\"provider\": \"sinfini\"}",
      timestamp = 1502733655449L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
  "HbaseLookupTopology Test" should "cargo blank" in {
    val smsCallback = SmsCallbackEvent(messageId = "6f55f6bc-8737-4b26-b40c-eb4078ade202",
      eventType = "sms_delivered",
      receiver = "+919842399355",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "",
      timestamp = 1502733655449L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
  "HbaseLookupTopology Test" should "cargo any" in {
    val smsCallback = SmsCallbackEvent(messageId = "6f55f6bc-8737-4b26-b40c-eb4078ade202",
      eventType = "sms_delivered",
      receiver = "+919842399355",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "{\"hey\":\"dsd\"}",
      timestamp = 1502733655449L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
  "HbaseLookupTopology Test" should "cargo null" in {
    val smsCallback = SmsCallbackEvent(messageId = "6f55f6bc-8737-4b26-b40c-eb4078ade202",
      eventType = "sms_delivered",
      receiver = "+919842399355",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = null,
      timestamp = 1502733655449L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
  "HbaseLookupTopology Test" should "cargo excep" in {
    val smsCallback = SmsCallbackEvent(messageId = "6f55f6bc-8737-4b26-b40c-eb4078ade202",
      eventType = "sms_delivered",
      receiver = "+919842399355",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "RenderFlow - Java.lang",
      timestamp = 1502733655449L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).runWith(latency.sink)
    Thread.sleep(20000)
  }
}
