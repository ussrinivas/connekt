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

import javax.mail.internet.InternetAddress

import com.fasterxml.jackson.annotation.JsonProperty
import com.flipkart.connekt.commons.services.ConnektConfig
import org.apache.commons.validator.routines.EmailValidator

case class EmailRequestInfo(@JsonProperty(required = false) appName: String,
                            @JsonProperty(required = false) to: Set[EmailAddress] = Set.empty[EmailAddress],
                            @JsonProperty(required = false) cc: Set[EmailAddress] = Set.empty[EmailAddress],
                            @JsonProperty(required = false) bcc: Set[EmailAddress] = Set.empty[EmailAddress],
                            @JsonProperty(required = false) from: EmailAddress,
                            @JsonProperty(required = false) replyTo: EmailAddress
                           ) extends ChannelRequestInfo {
  def toStrict: EmailRequestInfo = {
    this.copy(
      appName = appName.toLowerCase,
      to = Option(to).map(_.map(_.toStrict)).orNull,
      cc = Option(cc).map(_.map(_.toStrict)).getOrElse(Set.empty),
      bcc = Option(bcc).map(_.map(_.toStrict)).getOrElse(Set.empty)
    )
  }

  def validateEmailRequestInfo(): Unit = {
    val emailAddresses = (to ++ cc ++ bcc).+(from).+(replyTo).filter(e => null != e.address && e.address.nonEmpty)
    val invalidCharacters = ConnektConfig.getList[String]("invalid.email.name.characters").toSet
    emailAddresses.foreach(e => {
      require(!EmailValidator.getInstance().isValid(e.address), s"Bad Request. Invalid email address - ${e.address}")
    })
    invalidCharacters.foreach(i => {
      emailAddresses.foreach(e => {
        require(e.name.contains(i), s"Bad Request. Invalid email name - ${e.name}")
      })
    })
  }
}

case class EmailAddress(name: String, address: String) {

  def toStrict = EmailAddress(name, address.toLowerCase)

  def toInternetAddress: InternetAddress = {
    new InternetAddress(address, name)
  }
}
