package com.flipkart.connekt.commons.entities

object PNMessagingCarrier extends Enumeration {
  type PNMessagingCarrier = Value

  val FCM = Value("fcm")
  val APN = Value("apn")
  val WNS = Value("wns")
}
