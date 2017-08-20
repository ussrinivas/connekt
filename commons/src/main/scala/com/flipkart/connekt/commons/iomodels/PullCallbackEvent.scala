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
package com.flipkart.connekt.commons.iomodels

import com.flipkart.connekt.commons.utils.DateTimeUtils
import com.flipkart.connekt.commons.utils.StringUtils.{StringHandyFunctions, _}
import org.apache.commons.lang.RandomStringUtils

case class PullCallbackEvent(messageId: String,
                            eventType: String,
                            clientId: String,
                            contextId: String,
                            appName: String,
                            timestamp: Long = System.currentTimeMillis(),
                            eventId: String = RandomStringUtils.randomAlphabetic(10)) extends CallbackEvent {

  def this(messageId: String, eventType: String, clientId: String, contextId: String, appName: String) {
    this(
      messageId = messageId,
      clientId = clientId,
      contextId = contextId,
      eventType = eventType,
      appName = appName,
      timestamp = System.currentTimeMillis()
    )
  }

  def validate() = {
    require(eventType.isDefined, s"`eventType` field cannot be empty or null, `messageId`: $messageId")
  }

  override def contactId: String = s"${appName.toLowerCase}"

  override def toPublishFormat: fkint.mp.connekt.PullCallbackEvent = {
    fkint.mp.connekt.PullCallbackEvent(messageId = messageId, appName = appName, contextId = contextId, eventType = eventType,
      timestamp = DateTimeUtils.getStandardFormatted(timestamp))
  }

  override def namespace: String = "fkint/mp/connekt/PullCallbackEvent"
}
