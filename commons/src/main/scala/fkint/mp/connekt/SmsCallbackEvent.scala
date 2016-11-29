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
package fkint.mp.connekt

import com.flipkart.concord.publisher.TPublishRequest
import com.flipkart.connekt.commons.iomodels.Receiver

case class SmsCallbackEvent(messageId: String, providerMessageId: String, smsParts: String, encoding: String, smsLength: String, clientId: String, appName: String, contextId: String, eventType: String, cargo: String, receiver: Receiver, provider: String, timestamp: String) extends TPublishRequest {
  override def validate(): Boolean = true

  override def schemaVersion(): String = "1.0"
}
