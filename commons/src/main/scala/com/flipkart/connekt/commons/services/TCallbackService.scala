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
package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.iomodels.CallbackEvent

import scala.util.Try

trait TCallbackService extends TService {

  def persistCallbackEvents(channel: Channel.Value, events: List[CallbackEvent]): Try[List[String]]

  def syncPersistCallbackEvents(channel: Channel.Value, events: List[CallbackEvent]): Try[List[String]]

  def enqueueCallbackEvents(events: List[CallbackEvent], queueName:String): Try[Unit]

  def fetchCallbackEvent(requestId: String, contactId: String, channel: Channel.Value): Try[List[(CallbackEvent, Long)]]

  def fetchCallbackEventByContactId(contactId: String, channel: Channel.Value, minTimestamp: Long, maxTimestamp: Long): Try[List[(CallbackEvent, Long)]]

  def fetchEventsMapForContactId(contactId: String, channel: Channel.Value, minTimestamp: Long, maxTimestamp: Long): Try[Map[String, List[CallbackEvent]]]

  def fetchCallbackEventByMId(messageId: String, channel: Channel.Value, timeStampRange: Option[(Long, Long)] = None): Try[Map[String, List[CallbackEvent]]]

  def deleteCallBackEvent(requestId: String, forContact: String, channel: Channel.Value): Try[List[CallbackEvent]]

}
