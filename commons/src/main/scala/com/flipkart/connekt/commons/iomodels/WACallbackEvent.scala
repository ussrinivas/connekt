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

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.entities.bigfoot.PublishSupport
import com.flipkart.connekt.commons.utils.DateTimeUtils
import com.flipkart.connekt.commons.utils.StringUtils.{JSONMarshallFunctions, StringHandyFunctions}
import org.apache.commons.lang.RandomStringUtils

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type"
)
@JsonSubTypes(Array(
  new Type(value = classOf[WAStatusCallback], name = "status"),
  new Type(value = classOf[WAReplyCallback], name = "reply"),
  new Type(value = classOf[WAConversationCallback], name = "conversation")
))
class WACallback

case class WAStatusCallback(to: String, messageStatus: String) extends WACallback

case class WAReplyCallback(from: String, message: ObjectNode) extends WACallback

case class WAConversationCallback(from: String, message: ObjectNode, context: ObjectNode) extends WACallback

case class WACallbackEvent(messageId: String,
                           waMessageId: String,
                           eventType: String = "",
                           clientId: String,
                           customerNumber: String,
                           appName: String,
                           contextId: String,
                           info: WACallback,
                           cargo: String,
                           timestamp: Long = System.currentTimeMillis(),
                           eventId: String = RandomStringUtils.randomAlphabetic(10)
                          ) extends CallbackEvent with PublishSupport {

  def this(messageId: String,
           waMessageId: String,
           eventType: String,
           clientId: String,
           customerNumber: String,
           appName: String,
           contextId: String,
           info: WACallback,
           cargo: String) {

    this(messageId = messageId,
      waMessageId = waMessageId,
      eventType = eventType,
      clientId = clientId,
      customerNumber = customerNumber,
      appName = appName,
      contextId = contextId,
      info = info,
      cargo = cargo,
      timestamp = System.currentTimeMillis())

  }

  def validate() = {
    require(contextId == null || contextId.hasOnlyAllowedChars, s"`contextId` field can only contain [A-Za-z0-9_\\.\\-\\:\\|] allowed chars, `messageId`: $messageId, `contextId`: $contextId")
    require(contextId == null || contextId.length <= 20, s"`contextId` can be max 20 characters, `messageId`: $messageId, `contextId`: $contextId")
  }

  override def contactId: String = s"${appName.toLowerCase}$customerNumber"

  override def toPublishFormat: fkint.mp.connekt.WACallbackEvent = {
    fkint.mp.connekt.WACallbackEvent(messageId = messageId, waMessageId = waMessageId, eventType = eventType,
      clientId = clientId, customerNumber = customerNumber, appName = appName, contextId = contextId,
      info = info.getJson, cargo = cargo, timestamp = DateTimeUtils.getStandardFormatted(timestamp))
  }

  override def namespace: String = "fkint/mp/connekt/WACallbackEvent"

}
