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

import com.flipkart.connekt.commons.entities.bigfoot.PublishSupport
import com.flipkart.connekt.commons.utils.DateTimeUtils
import org.apache.commons.lang.RandomStringUtils
import com.flipkart.connekt.commons.utils.StringUtils._

case class EmailCallbackEvent(messageId: String,
                              clientId: String,
                              address:String,
                              eventType: String,
                              appName: String,
                              contextId: String,
                              cargo: String = null,
                              timestamp: Long = System.currentTimeMillis(),
                              eventId: String = RandomStringUtils.randomAlphabetic(10)) extends CallbackEvent with PublishSupport {

  //java-constructor
  def this(messageId: String, clientId:String, address:String,eventType: String,contextId: String,cargo: String,timestamp: java.lang.Long){
    this(messageId = messageId, clientId = clientId, address = address, eventType = eventType , appName = null, contextId = contextId ,
      cargo = cargo, timestamp = Option(timestamp).map(_.toLong).getOrElse(System.currentTimeMillis()))
  }

  def validate() = {
    require(contextId == null || contextId.hasOnlyAllowedChars, s"`contextId` field can only contain [A-Za-z0-9_\\.\\-\\:\\|] allowed chars, `messageId`: $messageId, `contextId`: $contextId")
    require(contextId == null || contextId.length <= 20, s"`contextId` can be max 20 characters, `messageId`: $messageId, `contextId`: $contextId")
    require(eventType.isDefined, s"`eventType` field cannot be empty or null, `messageId`: $messageId")
  }

  override def contactId: String =  s"${appName.toLowerCase}$address"

  override def namespace: String = "fkint/mp/connekt/EmailCallbackEvent"

  override def toPublishFormat: fkint.mp.connekt.EmailCallbackEvent = {
    fkint.mp.connekt.EmailCallbackEvent(messageId = messageId, address = address,eventType = eventType, appName = appName, contextId = contextId, cargo = cargo, timestamp = DateTimeUtils.getStandardFormatted(timestamp))
  }

}
