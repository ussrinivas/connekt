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
import com.flipkart.connekt.commons.utils.StringUtils.ByteArrayHandyFunctions

class WACallbackDao(tableName: String, hTableFactory: THTableFactory) extends CallbackDao(tableName: String, hTableFactory: THTableFactory) {
  override def channelEventPropsMap(channelCallbackEvent: CallbackEvent): Map[String, Array[Byte]] = {
    val waCallbackEvent = channelCallbackEvent.asInstanceOf[WACallbackEvent]
    val m = scala.collection.mutable.Map[String, Array[Byte]](
      "messageId" -> waCallbackEvent.messageId.getUtf8Bytes,
      "eventType" -> waCallbackEvent.eventType.getUtf8Bytes,
      "destination" -> waCallbackEvent.destination.getUtf8Bytes,
      "eventId" -> waCallbackEvent.eventId.getUtf8Bytes,
      "clientId" -> waCallbackEvent.clientId.getUtf8Bytes,
      "contextId" -> waCallbackEvent.contextId.getUtf8Bytes,
      "appName" -> waCallbackEvent.appName.getUtf8Bytes,
      "cargo" -> waCallbackEvent.cargo.getUtf8BytesNullWrapped,
      "timestamp" -> waCallbackEvent.timestamp.getBytes
    )
    waCallbackEvent.providerMessageId.foreach(m += "providerMessageId" -> _.getUtf8Bytes)
    m.toMap
  }

  override def mapToChannelEvent(channelEventPropsMap: Map[String, Array[Byte]]): CallbackEvent = {
    WACallbackEvent(
      messageId = channelEventPropsMap.getS("messageId"),
      providerMessageId = Option(channelEventPropsMap.getS("providerMessageId")),
      eventType = channelEventPropsMap.getS("eventType"),
      destination = channelEventPropsMap.getS("destination"),
      eventId = channelEventPropsMap.getS("eventId"),
      clientId = channelEventPropsMap.getS("clientId"),
      contextId = channelEventPropsMap.getS("contextId"),
      appName = channelEventPropsMap.getS("appName"),
      cargo = channelEventPropsMap.get("cargo").map(v => v.getStringNullable).orNull,
      timestamp = channelEventPropsMap.getL("timestamp").asInstanceOf[Long]
    )
  }

  override def fetchEventMapFromList(event: List[CallbackEvent]): Map[String, List[CallbackEvent]] = {
    val waCallbackEvents = event.map(_.asInstanceOf[WACallbackEvent])
    waCallbackEvents.groupBy(p => p.messageId)
  }

  override def fetchCallbackEvents(requestId: String, event: ChannelRequestInfo, fetchRange: Option[(Long, Long)]): Map[String, List[CallbackEvent]] = {
    val waRequestInfo = event.asInstanceOf[WARequestInfo]
    waRequestInfo.destinations.toList.map(waRequestInfo.appName + _)
      .flatMap(fetchCallbackEvents(requestId, _, fetchRange))
      .asInstanceOf[List[(WACallbackEvent, Long)]]
      .map(_._1)
      .groupBy(_.destination)
  }
}
