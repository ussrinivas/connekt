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

import com.flipkart.connekt.commons.factories.THTableFactory
import com.flipkart.connekt.commons.iomodels.{CallbackEvent, ChannelRequestInfo, EmailCallbackEvent, EmailRequestInfo}
import com.flipkart.connekt.commons.dao.HbaseDao._

class EmailCallbackDao(tableName: String, hTableFactory: THTableFactory) extends CallbackDao(tableName: String, hTableFactory: THTableFactory) {
  override def channelEventPropsMap(channelCallbackEvent: CallbackEvent): Map[String, Array[Byte]] = {
    val emailCallbackEvent = channelCallbackEvent.asInstanceOf[EmailCallbackEvent]
    Map[String, Array[Byte]](
      "messageId" -> emailCallbackEvent.messageId.getUtf8Bytes,
      "eventId" -> emailCallbackEvent.eventId.getUtf8Bytes,
      "clientId" -> emailCallbackEvent.clientId.getUtf8Bytes,
      "address" -> emailCallbackEvent.address.getUtf8Bytes,
      "eventType" -> emailCallbackEvent.eventType.getUtf8Bytes,
      "appName" -> emailCallbackEvent.appName.getUtf8Bytes,
      "contextId" -> emailCallbackEvent.contextId.getUtf8Bytes,
      "cargo" -> Option(emailCallbackEvent.cargo).map(_.getUtf8Bytes).orNull,
      "timestamp" -> emailCallbackEvent.timestamp.getBytes
    )
  }

  override def mapToChannelEvent(channelEventPropsMap: Map[String, Array[Byte]]): CallbackEvent = {
    EmailCallbackEvent(
      messageId = channelEventPropsMap.getS("messageId"),
      eventId = channelEventPropsMap.getS("eventId"),
      address = channelEventPropsMap.getS("address"),
      eventType = channelEventPropsMap.getS("eventType"),
      appName = channelEventPropsMap.getS("appName"),
      contextId = channelEventPropsMap.getS("contextId"),
      clientId = channelEventPropsMap.getS("clientId"),
      cargo = channelEventPropsMap.getS("cargo"),
      timestamp = channelEventPropsMap.getL("timestamp").asInstanceOf[Long]
    )
  }

  override def fetchEventMapFromList(event: List[CallbackEvent]): Map[String, List[CallbackEvent]] = {
    val emailCallbackEvents = event.map(_.asInstanceOf[EmailCallbackEvent])
    emailCallbackEvents.groupBy(p => p.messageId)
  }

  override def fetchCallbackEvents(requestId: String, event: ChannelRequestInfo, fetchRange: Option[(Long, Long)]): Map[String, List[CallbackEvent]] = {
    val emailRequestInfo = event.asInstanceOf[EmailRequestInfo]
    (emailRequestInfo.to ++ emailRequestInfo.cc).toList.map(eId => emailRequestInfo.appName + eId.address).flatMap(fetchCallbackEvents(requestId, _, fetchRange)).asInstanceOf[List[(EmailCallbackEvent, Long)]].map(_._1).groupBy(_.address)
  }


}
