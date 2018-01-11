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

import java.util.Optional

import com.flipkart.connekt.commons.dao.HbaseSinkSupport
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.entities.bigfoot.PublishSupport
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.utils.DateTimeUtils
import com.flipkart.connekt.commons.utils.StringUtils.StringHandyFunctions
import org.apache.commons.lang.RandomStringUtils

case class WACallbackEvent(messageId: String,
                           providerMessageId: Option[String],
                           destination: String,
                           eventType: String,
                           clientId: String,
                           appName: String,
                           contextId: String,
                           cargo: String,
                           timestamp: Long = System.currentTimeMillis(),
                           eventId: String = RandomStringUtils.randomAlphabetic(10)
                          ) extends CallbackEvent with PublishSupport with WAGeneratedEvent with HbaseSinkSupport {

  def this(messageId: String,
           providerMessageId: Optional[String],
           destination: String,
           eventType: String,
           clientId: String,
           appName: String,
           contextId: String,
           cargo: String) {

    this(messageId = messageId,
      providerMessageId = Option(providerMessageId.orElse(null)),
      destination = destination,
      eventType = eventType,
      clientId = clientId,
      appName = appName,
      contextId = contextId,
      cargo = cargo,
      timestamp = System.currentTimeMillis())
  }

  def this(messageId: String,
           providerMessageId: Option[String],
           destination: String,
           eventType: String,
           clientId: String,
           appName: String,
           contextId: String,
           cargo: String) {

    this(messageId = messageId,
      providerMessageId = providerMessageId,
      destination = destination,
      eventType = eventType,
      clientId = clientId,
      appName = appName,
      contextId = contextId,
      cargo = cargo,
      timestamp = System.currentTimeMillis())
  }

  def validate() = {
    require(contextId == null || contextId.hasOnlyAllowedChars, s"`contextId` field can only contain [A-Za-z0-9_\\.\\-\\:\\|] allowed chars, `messageId`: $messageId, `contextId`: $contextId")
    require(contextId == null || contextId.length <= 20, s"`contextId` can be max 20 characters, `messageId`: $messageId, `contextId`: $contextId")
    require(eventType.isDefined, s"`eventType` field cannot be empty or null, `messageId`: $messageId")
  }

  override def contactId: String = s"${appName.toLowerCase}$destination"

  override def toPublishFormat: fkint.mp.connekt.WACallbackEvent = {
    fkint.mp.connekt.WACallbackEvent(
      messageId = messageId,
      providerMessageId = providerMessageId.getOrElse(""),
      eventType = eventType,
      clientId = clientId,
      destination = destination,
      appName = appName,
      contextId = contextId,
      cargo = cargo,
      timestamp = DateTimeUtils.getStandardFormatted(timestamp)
    )
  }

  override def namespace: String = "fkint/mp/connekt/WACallbackEvent"

  override def sinkId = messageId
}
