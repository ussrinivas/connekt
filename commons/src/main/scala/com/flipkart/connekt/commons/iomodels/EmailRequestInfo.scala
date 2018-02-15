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

import akka.http.javadsl.model.StatusCodes
import com.fasterxml.jackson.annotation.JsonProperty
import com.flipkart.connekt.commons.entities.exception.{ExceptionType, ServiceException}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
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

  def validate(): Unit = {
    if (to == null && to.isEmpty) {
      ConnektLogger(LogFile.SERVICE).error(s"Request Validation Failed, $this ")
      require(requirement = true, "Request Validation Failed. Please ensure mandatory field values.")
    }
  }
}

case class EmailAddress(name: String, address: String) {

  def toStrict = {
    validate()
    EmailAddress(name, address.toLowerCase)
  }

  def validate(): Unit = {
    if (!EmailValidator.getInstance().isValid(address)) {
      throw ServiceException(StatusCodes.BAD_REQUEST, ExceptionType.BAD_REQUEST_INVALID_EMAIL, s"Bad Request. Invalid email address - $address")
    }
    val invalidCharacters = ConnektConfig.getList[String]("email.name.invalid.characters").toSet //fetched by config
    invalidCharacters.foreach(i => {
      if (name != null && name.contains(i)) { // null check to make sure 'null's are passed as current bau
        throw ServiceException(StatusCodes.BAD_REQUEST, ExceptionType.BAD_REQUEST_INVALID_NAME, s"Bad Request. Invalid email name - $name")
      }
    })
  }

  def toInternetAddress: InternetAddress = {
    new InternetAddress(address, name)
  }
}
