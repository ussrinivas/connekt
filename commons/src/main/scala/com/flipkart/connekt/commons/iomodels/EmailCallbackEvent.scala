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

import com.flipkart.concord.publisher.TPublishRequest
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
  override def contactId: String = ???

  override def namespace: String = ???

  override def toPublishFormat: TPublishRequest = ???
}
