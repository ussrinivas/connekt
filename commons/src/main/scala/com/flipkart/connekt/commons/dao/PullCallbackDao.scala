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

class PullCallbackDao(tableName: String, hTableFactory: THTableFactory) extends CallbackDao(tableName: String, hTableFactory: THTableFactory) {
  override def channelEventPropsMap(channelCallbackEvent: CallbackEvent): Map[String, Array[Byte]] = {
    val pullCallbackEvent = channelCallbackEvent.asInstanceOf[PullCallbackEvent]
    Map[String, Array[Byte]](
      "messageId" -> pullCallbackEvent.messageId.getUtf8Bytes,
      "contactId" -> pullCallbackEvent.contactId.getUtf8Bytes,
      "eventId" -> pullCallbackEvent.eventId.getUtf8Bytes,
      "clientId" -> pullCallbackEvent.clientId.getUtf8Bytes,
      "contextId" -> pullCallbackEvent.contextId.getUtf8Bytes,
      "eventType" -> pullCallbackEvent.eventType.getUtf8Bytes,
      "appName" -> pullCallbackEvent.appName.getUtf8Bytes,
      "timestamp" -> pullCallbackEvent.timestamp.getBytes
    )
  }

  override def mapToChannelEvent(channelEventPropsMap: Map[String, Array[Byte]]): CallbackEvent = {
    PullCallbackEvent(
      messageId = channelEventPropsMap.getS("messageId"),
      contactId = channelEventPropsMap.getS("contactId"),
      eventId = channelEventPropsMap.getS("eventId"),
      clientId = channelEventPropsMap.getS("clientId"),
      contextId = channelEventPropsMap.getS("contextId"),
      eventType = channelEventPropsMap.getS("eventType"),
      appName = channelEventPropsMap.getS("appName"),
      timestamp = channelEventPropsMap.getL("timestamp").asInstanceOf[Long]
    )
  }

  override def fetchEventMapFromList(event: List[CallbackEvent]): Map[String, List[CallbackEvent]] = {
    val pullCallbackEvents = event.map(_.asInstanceOf[PullCallbackEvent])
    pullCallbackEvents.groupBy(p => p.messageId)
  }

  override def fetchCallbackEvents(requestId: String, event: ChannelRequestInfo, fetchRange: Option[(Long, Long)]): Map[String, List[CallbackEvent]] = {
    val pullRequestInfo = event.asInstanceOf[PullRequestInfo]
    pullRequestInfo.userIds.toList.map(pullRequestInfo.appName + _)
                                  .flatMap(fetchCallbackEvents(requestId, _, fetchRange))
                                  .asInstanceOf[List[(PullCallbackEvent, Long)]]
                                  .map(_._1)
                                  .groupBy(_.contactId)
  }

}
