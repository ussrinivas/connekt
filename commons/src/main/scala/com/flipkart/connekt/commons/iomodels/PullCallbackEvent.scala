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

import com.flipkart.connekt.commons.utils.StringUtils.{StringHandyFunctions, _}
import org.apache.commons.lang.RandomStringUtils

case class PullCallbackEvent(messageId: String,
                             contactId: String,
                             eventType: String,
                             clientId: String,
                             contextId: String,
                             appName: String,
                             timestamp: Long = System.currentTimeMillis(),
                             eventId: String = RandomStringUtils.randomAlphabetic(10)) extends CallbackEvent {

  def validate() = {
    require(eventType.isDefined, s"`eventType` field cannot be empty or null, `messageId`: $messageId")
    require(contactId.isDefined, s"`contactId` field cannot be empty or null, `messageId`: $messageId")
  }
}
