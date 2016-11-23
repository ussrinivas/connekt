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

case class EmailRequestInfo(@JsonProperty(required = false) appName: String,
                            @JsonProperty(required = false) to: Set[EmailAddress] = Set.empty[EmailAddress],
                            @JsonProperty(required = false) cc: Set[EmailAddress] = Set.empty[EmailAddress],
                            @JsonProperty(required = false) bcc: Set[EmailAddress] = Set.empty[EmailAddress],
                            @JsonProperty(required = false) from: EmailAddress,
                            @JsonProperty(required = false) replyTo: EmailAddress

                           ) extends ChannelRequestInfo {
  def toStrict:EmailRequestInfo = {
    this.copy(appName = appName.toLowerCase, cc = Option(cc).getOrElse(Set.empty), bcc = Option(bcc).getOrElse(Set.empty) )
  }
}

case class EmailAddress(name: String, address: String){
  def toInternetAddress:InternetAddress = {
    new InternetAddress(address,name)
  }
}
