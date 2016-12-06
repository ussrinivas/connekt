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

case class SmsCallbackEvent(messageId: String,
                            providerMessageId: String,
                            eventType: String,
                            receiver: String,
                            clientId: String,
                            provider: String,
                            appName: String,
                            contextId: String,
                            cargo: String = null,
                            timestamp: Long = System.currentTimeMillis(),
                            eventId: String = RandomStringUtils.randomAlphabetic(10)) extends CallbackEvent {

  def validate() = {
    require(contextId == null || contextId.hasOnlyAllowedChars, s"`contextId` field can only contain [A-Za-z0-9_\\.\\-\\:\\|] allowed chars, `messageId`: $messageId, `contextId`: $contextId")
    require(contextId == null || contextId.length <= 20, s"`contextId` can be max 20 characters, `messageId`: $messageId, `contextId`: $contextId")
    require(eventType.isDefined, s"`eventType` field cannot be empty or null, `messageId`: $messageId")
  }

  override def contactId: String = s"${appName.toLowerCase}$receiver"

  override def toPublishFormat: fkint.mp.connekt.SmsCallbackEvent = {
    fkint.mp.connekt.SmsCallbackEvent(messageId = messageId, providerMessageId = providerMessageId, clientId = clientId, appName = appName, contextId = contextId, eventType = eventType,
      cargo = cargo, receiver = receiver.getJson, provider = provider, timestamp = DateTimeUtils.getStandardFormatted(timestamp))
  }

  override def namespace: String = "fkint/mp/connekt/SmsCallbackEvent"
}
