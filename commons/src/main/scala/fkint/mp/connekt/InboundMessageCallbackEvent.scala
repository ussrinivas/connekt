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

case class InboundMessageCallbackEvent(clientId: String,
                                       sender: String,
                                       eventType: String,
                                       appName: String,
                                       contextId: String,
                                       message: String,
                                       channel: String,
                                       providerMessageId: String,
                                       messageMeta: String,
                                       media: List[String],
                                       cargo: String,
                                       timestamp: String
                                      ) extends TPublishRequest {
  override def validate(): Boolean = true

  override def schemaVersion(): String = "1.1"
}
