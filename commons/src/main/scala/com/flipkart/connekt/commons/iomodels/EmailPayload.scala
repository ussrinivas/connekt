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

case class EmailPayload(to: Set[EmailAddress], cc: Set[EmailAddress], bcc: Set[EmailAddress], data: EmailRequestData, from: EmailAddress, replyTo: EmailAddress)

abstract class ProviderEnvelope {
  def provider: Iterable[String]

  def clientId: String

  def messageId:String

  def appName:String

  def contextId:String

  def meta: Map[String, Any]

  def destinations:Set[String]

}

case class EmailPayloadEnvelope(messageId: String, appName: String, contextId: String, clientId: String, payload: EmailPayload, meta: Map[String, Any], provider: Seq[String] = Seq()) extends ProviderEnvelope {
  override def destinations: Set[String] = payload.to.map(_.address) ++ payload.cc.map(_.address) ++ payload.bcc.map(_.address)
}
