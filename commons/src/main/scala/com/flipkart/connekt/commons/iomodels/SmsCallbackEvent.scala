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
import org.apache.commons.lang.RandomStringUtils

case class SmsCallbackEvent(messageId: String,
                            eventType: String,
                            receiver: String,
                            clientId: String,
                            provider: String,
                            appName: String,
                            channel: String,
                            contextId: String,
                            cargo: String = null,
                            timestamp: Long = System.currentTimeMillis(),
                            eventId: String = RandomStringUtils.randomAlphabetic(10)) extends CallbackEvent {

  override def contactId: String = s"${appName.toLowerCase}$receiver"

  override def toPublishFormat: fkint.mp.connekt.SmsCallbackEvent = {
    fkint.mp.connekt.SmsCallbackEvent(messageId = messageId, appName = appName, contextId = contextId, eventType = eventType, cargo = cargo, receiver = receiver, provider = provider, timestamp = DateTimeUtils.getStandardFormatted(timestamp))
  }

  override def namespace: String = "fkint/mp/connekt/SmsCallbackEvent"

}
