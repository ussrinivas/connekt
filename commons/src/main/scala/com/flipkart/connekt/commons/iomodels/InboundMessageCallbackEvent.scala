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

import com.flipkart.connekt.commons.entities.bigfoot.PublishSupport
import com.flipkart.connekt.commons.utils.DateTimeUtils
import com.flipkart.connekt.commons.utils.StringUtils._
import org.apache.commons.lang.RandomStringUtils

import scala.collection.JavaConverters._

case class Media(file: String, url: String, sha256: String, status: String, mimeType: String)

case class InboundMessageCallbackEvent(clientId: String,
                                       sender: String,
                                       eventType: String,
                                       appName: String,
                                       contextId: String,
                                       message: String,
                                       channel: String,
                                       providerMessageId: Option[String],
                                       messageMeta: Option[String],
                                       media: List[Media],
                                       cargo: String = null,
                                       timestamp: Long = System.currentTimeMillis(),
                                       eventId: String = RandomStringUtils.randomAlphabetic(10)
                                      ) extends CallbackEvent with PublishSupport {

  //java-constructor
  def this(eventType: String, contextId: String, message: String, providerMessageId: Optional[String], messageMeta: Optional[String], media: java.util.List[Media], cargo: String) {
    this(clientId = null, sender = null, eventType = eventType, appName = null, contextId = contextId, channel = null,
      message = message, providerMessageId = Option(providerMessageId.orElse(null)),
      messageMeta = Option(messageMeta.orElse(null)), media = media.asScala.toList, cargo = cargo)
  }

  //java-constructor
  def this(eventType: String, contextId: String, message: String, cargo: String) {
    this(clientId = null, sender = null, eventType = eventType, appName = null, contextId = contextId, channel = null,
      message = message, providerMessageId = None,
      messageMeta = None, media = List.empty, cargo = cargo)
  }

  def validate() = {
    require(contextId == null || contextId.hasOnlyAllowedChars, s"`contextId` field can only contain [A-Za-z0-9_\\.\\-\\:\\|] allowed chars, `contextId`: $contextId")
    require(contextId == null || contextId.length <= 20, s"`contextId` can be max 20 characters `contextId`: $contextId")
    require(eventType.isDefined, s"`eventType` field cannot be empty or null, `eventId`: $eventId")
  }

  override def contactId: String = s"${appName.toLowerCase}$sender"

  override def namespace: String = "fkint/mp/connekt/InboundMessageCallbackEvent"

  override def toPublishFormat: fkint.mp.connekt.InboundMessageCallbackEvent = {
    fkint.mp.connekt.InboundMessageCallbackEvent(clientId = clientId, sender = sender, eventType = eventType, appName = appName, channel = channel,
      contextId = contextId, message = message, providerMessageId = providerMessageId.getOrElse(""), messageMeta = messageMeta.getOrElse(""), media = List.empty, cargo = cargo, timestamp = DateTimeUtils.getStandardFormatted(timestamp))
  }

  override def messageId: String = throw new NotImplementedError(s"`messageId` undefined for InboundMessageCallbackEvent")
}
