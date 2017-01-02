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
package com.flipkart.connekt.busybees.streams.flows.dispatchers

import com.flipkart.connekt.busybees.models.EmailRequestTracker
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.EmailPayloadEnvelope
import com.flipkart.connekt.commons.utils.StringUtils._

class SMTPDispatcherPrepare extends MapFlowStage[EmailPayloadEnvelope, (EmailPayloadEnvelope, EmailRequestTracker)] {

  override implicit val map: EmailPayloadEnvelope => List[(EmailPayloadEnvelope, EmailRequestTracker)] = message => {
    ConnektLogger(LogFile.PROCESSORS).debug("SMTPDispatcherPrepare received message: {}", supplier(message.messageId))
    ConnektLogger(LogFile.PROCESSORS).trace("SMTPDispatcherPrepare received message: {}", supplier(message))

    val requestTrace = EmailRequestTracker(messageId = message.messageId, clientId = message.clientId, to = message.payload.to.map(_.address), cc = message.payload.cc.map(_.address), appName = message.appName, contextId = message.contextId, provider = "smtp", request = message, meta = message.meta)

    List(message -> requestTrace)

  }
}
