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
package com.flipkart.connekt.busybees.storm.bolts

import com.flipkart.connekt.commons.entities.DeviceDetails
import com.flipkart.connekt.commons.helpers.XmppMessageIdHelper
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, GCMPayloadEnvelope, GCMXmppPNPayload, PNRequestInfo}
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.duration._

/**
  * Created by saurabh.mimani on 27/10/17.
  */
class AndroidXmppChannelFormatterBolt extends AndroidChannelFormatterBolt {
  private val deliveryReceiptRequired = Some(true)

  override def createPayload(message: ConnektRequest,
                             devicesInfo: Seq[DeviceDetails],
                             appDataWithId: Any): List[GCMPayloadEnvelope] = {

    val pnInfo = message.channelInfo.asInstanceOf[PNRequestInfo]
    val timeToLive = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(6.hour.toSeconds)

    devicesInfo.map { device =>
      val messageId = XmppMessageIdHelper.generateMessageId(message, device.deviceId)
      val payload = GCMXmppPNPayload(device.token, messageId, Option(pnInfo.priority).map(_.toString), appDataWithId, Some(timeToLive), deliveryReceiptRequired, Option(message.isTestRequest))
      GCMPayloadEnvelope(message.id, message.clientId, Seq(device.deviceId), pnInfo.appName, message.contextId.orEmpty, payload, message.meta)
    }.toList
  }

}
