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
package com.flipkart.connekt.commons.dao

import com.flipkart.connekt.commons.dao.HbaseDao._
import com.flipkart.connekt.commons.factories.THTableFactory
import com.flipkart.connekt.commons.iomodels._

class SmsCallbackDao(tableName: String, hTableFactory: THTableFactory) extends CallbackDao(tableName: String, hTableFactory: THTableFactory) {
  override def channelEventPropsMap(channelCallbackEvent: CallbackEvent): Map[String, Array[Byte]] = {
    val smsCallbackEvent = channelCallbackEvent.asInstanceOf[SmsCallbackEvent]
    Map[String, Array[Byte]](
      "messageId" -> smsCallbackEvent.messageId.getUtf8Bytes,
      "eventId" -> smsCallbackEvent.eventId.getUtf8Bytes,
      "clientId" -> smsCallbackEvent.clientId.getUtf8Bytes,
      "receiver" -> smsCallbackEvent.receiver.getUtf8Bytes,
      "eventType" -> smsCallbackEvent.eventType.getUtf8Bytes,
      "appName" -> smsCallbackEvent.appName.getUtf8Bytes,
      "contextId" -> smsCallbackEvent.contextId.getUtf8Bytes,
      "cargo" -> Option(smsCallbackEvent.cargo).map(_.getUtf8Bytes).orNull,
      "timestamp" -> smsCallbackEvent.timestamp.getBytes
    )
  }

  override def mapToChannelEvent(channelEventPropsMap: Map[String, Array[Byte]]): CallbackEvent = {
    SmsCallbackEvent(
      messageId = channelEventPropsMap.getS("messageId"),
      eventId = channelEventPropsMap.getS("eventId"),
      receiver = channelEventPropsMap.getS("receiver"),
      eventType = channelEventPropsMap.getS("eventType"),
      appName = channelEventPropsMap.getS("appName"),
      contextId = channelEventPropsMap.getS("contextId"),
      clientId = channelEventPropsMap.getS("clientId"),
      cargo = channelEventPropsMap.getS("cargo"),
      timestamp = channelEventPropsMap.getL("timestamp").asInstanceOf[Long]
    )
  }

  override def fetchEventMapFromList(event: List[CallbackEvent]): Map[String, List[CallbackEvent]] = {
    val smsCallbackEvents = event.map(_.asInstanceOf[SmsCallbackEvent])
    smsCallbackEvents.groupBy(p => p.messageId)
  }

  override def fetchCallbackEvents(requestId: String, event: ChannelRequestInfo, fetchRange: Option[(Long, Long)]): Map[String, List[CallbackEvent]] = {
    val smsRequestInfo = event.asInstanceOf[SmsRequestInfo]
    smsRequestInfo.receivers.toList.map(smsRequestInfo.appName + _).flatMap(fetchCallbackEvents(requestId, _, fetchRange))
      .asInstanceOf[List[(SmsCallbackEvent, Long)]].map(_._1).groupBy(r => r.receiver)
  }

}
