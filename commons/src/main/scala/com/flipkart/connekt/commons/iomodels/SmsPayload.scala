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

import com.fasterxml.jackson.annotation.JsonProperty
import com.flipkart.connekt.commons.utils.StringUtils.JSONMarshallFunctions

case class Receiver(@JsonProperty(required = true) countryCode: String, @JsonProperty(required = true) number: String)

case class SmsPayload(@JsonProperty(required = false) receivers: Set[Receiver], messageBody: SmsRequestData, senderMask: String, ttl: String)

case class SmsPayloadEnvelope(messageId: String, clientId: String, stencilId: String, appName: String, contextId: String, payload: SmsPayload,
                              isUnicodeMessage: Boolean, isIntl: String, smsPart: String, encoding: String, smsLength: String, meta: Map[String, Any], provider: Seq[String] = Seq.empty) extends ProviderEnvelope {
  override def destinations: Set[String] = payload.receivers.map(r => r.getJson)
}
