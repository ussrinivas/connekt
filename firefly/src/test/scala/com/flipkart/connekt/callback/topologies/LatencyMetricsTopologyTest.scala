package com.flipkart.connekt.callback.topologies

import akka.stream.scaladsl.{Sink, Source}
import com.flipkart.connekt.busybees.tests.streams.TopologyUTSpec
import com.flipkart.connekt.commons.iomodels.SmsCallbackEvent
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.firefly.flows.metrics.LatencyMetrics

class LatencyMetricsTopologyTest extends TopologyUTSpec with Instrumented {
  val latency = new LatencyMetrics().flow
  "HbaseLookupTopology Test" should "run" in {
    val smsCallback = SmsCallbackEvent(messageId = "c37d3855-c349-48c9-b3af-724eade554f0",
      eventType = "sms_delivered",
      receiver = "+911234567843",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "{\"deliveredTS\":\"1515060997000\",\"cause\":\"SUCCESS\",\"externalId\":\"3440781747693252725-262142534416137710\",\"provider\":\"gupshup\",\"errCode\":\"000\"}",
      timestamp = 1515060997000L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).via(latency).runWith(Sink.ignore)
    Thread.sleep(15000)
  }
  "HbaseLookupTopology Test" should "cargo blank" in {
    val smsCallback = SmsCallbackEvent(messageId = "62b7d6a1-cdb8-414d-8954-972bae4aec2c",
      eventType = "CLICK",
      receiver = "+911234567843",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "{\"name\":null,\"url\":\"https://dl.flipkart.com/dl/order_details?order_id=OD110852793615523000&token=40f33000a4114bc29ccecd5ba8170d2c&affid=SMSb98efcd97fd08cb92&utm_medium=sms&utm_source=promo&utm_campaign=SMSb98efcd97fd08cb92&u=62b7d6a1-cdb8-414d-8954-972bae4aec2c&utm_content=click&cmpid=sms_promo_SMSb98efcd97fd08cb92\",\"useragent\":\"Mozilla/5.0 (X11; Ubuntu; Linux i686; rv:24.0) Gecko/20100101 Firefox/24.0\"}",
      timestamp = 1509625346666L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).via(latency).runWith(Sink.ignore)
    Thread.sleep(15000)
  }
  "HbaseLookupTopology Test" should "cargo any" in {
    val smsCallback = SmsCallbackEvent(messageId = "089dae77-051d-4146-ad25-ea86ed761608",
      eventType = "sms_delivered",
      receiver = "+911234567843",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "{}",
      timestamp = 1509800169441L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).via(latency).runWith(Sink.ignore)
    Thread.sleep(15000)
  }

  "HbaseLookupTopology Test" should "cargo excssep" in {
    val smsCallback = SmsCallbackEvent(messageId = "1c4f3564-3687-4f72-a43d-f75dd111d0",
      eventType = "sms_received",
      receiver = "+911234567843",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "{\"externalId\":\"3458299854161064066-166744\",\"provider\":\"gupshup-v2\",\"errCode\":\"000\"}",
      timestamp = 1509614097879L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).via(latency).runWith(Sink.ignore)
    Thread.sleep(15000)
  }

  "False DeliveredTS Test" should "run" in {
    val smsCallback = SmsCallbackEvent(messageId = "3ce2de85-be4f-4ccb-a91b-1ce8b424ab4c",
      eventType = "sms_delivered",
      receiver = "+911234567843",
      clientId = "affordability",
      appName = "flipkart",
      contextId = "",
      cargo = "{\"deliveredTS\":\"N/A\",\"cause\":\"SUCCESS\",\"externalId\":\"3440781747693252725-262142534416137710\",\"provider\":\"gupshup\",\"errCode\":\"000\"}",
      timestamp = 1509698165000L,
      eventId = "iaUAuOefuD")

    Source.single(smsCallback).via(latency).runWith(Sink.ignore)
    Thread.sleep(20000)
  }
}
