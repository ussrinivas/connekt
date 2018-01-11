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

import com.flipkart.connekt.commons.dao.HbaseSinkSupport
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.entities.bigfoot.PublishSupport
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.utils.DateTimeUtils
import com.flipkart.connekt.commons.utils.StringUtils.{StringHandyFunctions, _}
import org.apache.commons.lang.RandomStringUtils

case class SmsCallbackEvent(
                             messageId: String,
                             eventType: String,
                             receiver: String,
                             clientId: String,
                             appName: String,
                             contextId: String,
                             cargo: String = null,
                             timestamp: Long = System.currentTimeMillis(),
                             eventId: String = RandomStringUtils.randomAlphabetic(10)
                           ) extends CallbackEvent with PublishSupport with HbaseSinkSupport {

  def this(messageId: String, eventType: String, receiver: String, clientId: String, appName: String, contextId: String, cargo: String) {
    this(messageId = messageId, clientId = clientId, receiver = receiver, eventType = eventType, appName = appName, contextId = contextId,
      cargo = cargo, timestamp = System.currentTimeMillis())
  }

  def validate() = {
    require(contextId == null || contextId.hasOnlyAllowedChars, s"`contextId` field can only contain [A-Za-z0-9_\\.\\-\\:\\|] allowed chars, `messageId`: $messageId, `contextId`: $contextId")
    require(contextId == null || contextId.length <= 20, s"`contextId` can be max 20 characters, `messageId`: $messageId, `contextId`: $contextId")
    require(eventType.isDefined, s"`eventType` field cannot be empty or null, `messageId`: $messageId")
  }

  override def contactId: String = s"${appName.toLowerCase}$receiver"

  override def toPublishFormat: fkint.mp.connekt.SmsCallbackEvent = {
    fkint.mp.connekt.SmsCallbackEvent(messageId = messageId, appName = appName, contextId = contextId, eventType = eventType,
      cargo = cargo, receiver = receiver.getJson, timestamp = DateTimeUtils.getStandardFormatted(timestamp))
  }

  override def namespace: String = "fkint/mp/connekt/SmsCallbackEvent"

  override def sinkId = messageId
}
