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

case class SmsMeta(@JsonProperty(required = true) isUnicodeMessage: Boolean, @JsonProperty(required = true) smsParts: Int, @JsonProperty(required = true) encoding: String, @JsonProperty(required = true) smsLength: Int)

case class Receiver(@JsonProperty(required = true) countryCode: String, @JsonProperty(required = true) number: String)

case class SmsPayload(@JsonProperty(required = false) receivers: Set[Receiver], messageBody: SmsRequestData, senderMask: String, ttl: String)

case class SmsPayloadEnvelope(messageId: String, clientId: String, stencilId: String, appName: String, contextId: String, payload: SmsPayload, isIntl: String, meta: Map[String, Any], provider: Seq[String] = Seq.empty) extends ProviderEnvelope {
  override def destinations: Set[String] = payload.receivers.map(r => r.getJson)
}

