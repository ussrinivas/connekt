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

case class EmailCallbackEvent(messageId: String,
                              clientId: String,
                              address:String,
                              eventType: String,
                              appName: String,
                              contextId: String,
                              cargo: String = null,
                              timestamp: Long = System.currentTimeMillis(),
                              eventId: String = RandomStringUtils.randomAlphabetic(10)) extends CallbackEvent {

  override def contactId: String =  s"${appName.toLowerCase}$address"

  override def namespace: String = "fkint/mp/connekt/EmailCallbackEvent"

  override def toPublishFormat: fkint.mp.connekt.EmailCallbackEvent = {
    fkint.mp.connekt.EmailCallbackEvent(messageId = messageId, address = address,eventType = eventType, appName = appName, contextId = contextId, cargo = cargo, timestamp = DateTimeUtils.getStandardFormatted(timestamp))
  }

}
