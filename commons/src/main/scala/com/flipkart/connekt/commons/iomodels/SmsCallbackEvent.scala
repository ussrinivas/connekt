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
import com.flipkart.connekt.commons.utils.StringUtils.JSONMarshallFunctions
import org.apache.commons.lang.RandomStringUtils

case class SmsCallbackEvent(messageId: String,
                            providerMessageId: String,
                            smsParts: String,
                            encoding: String,
                            smsLength: String,
                            isInternationalNumber: String,
                            eventType: String,
                            receiver: String,
                            clientId: String,
                            provider: String,
                            appName: String,
                            contextId: String,
                            cargo: String = null,
                            timestamp: Long = System.currentTimeMillis(),
                            eventId: String = RandomStringUtils.randomAlphabetic(10)) extends CallbackEvent {

  override def contactId: String = s"${appName.toLowerCase}$receiver"

  override def toPublishFormat: fkint.mp.connekt.SmsCallbackEvent = {
    fkint.mp.connekt.SmsCallbackEvent(messageId = messageId, providerMessageId = providerMessageId, clientId = clientId, appName = appName, contextId = contextId, eventType = eventType, isInternationalNumber = isInternationalNumber,
      cargo = Map("smsParts" -> smsParts, "encoding" -> encoding, "smsLength" -> smsLength).getJson, receiver = receiver.getJson, provider = provider, timestamp = DateTimeUtils.getStandardFormatted(timestamp))
  }

  override def namespace: String = "fkint/mp/connekt/SmsCallbackEvent"
}
