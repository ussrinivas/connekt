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

import java.nio.charset.Charset

import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.mutable

case class SmsMeta(@JsonProperty(required = true) smsParts: Int, @JsonProperty(required = true) encoding: Charset)

case class SmsPayload(@JsonProperty(required = false) receivers: Set[String], messageBody: SmsRequestData, senderMask: String, ttl: String)

case class SmsPayloadEnvelope(messageId: String, clientId: String, stencilId: String, appName: String, contextId: String, payload: SmsPayload, meta: Map[String, Any], provider: mutable.Seq[String] = mutable.Seq.empty) extends ProviderEnvelope {
  override def destinations: Set[String] = payload.receivers
}

