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
package com.flipkart.connekt.busybees.streams.flows.formaters

import com.flipkart.connekt.commons.entities.DeviceDetails
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class AndroidHttpChannelFormatter (parallelism: Int)(implicit ec: ExecutionContextExecutor) extends AndroidChannelFormatter(parallelism)(ec) {

  override def createPayload(message:ConnektRequest,
                           devicesInfo:Seq[DeviceDetails],
                           appDataWithId:Any):List[GCMPayloadEnvelope] = {

    val pnInfo = message.channelInfo.asInstanceOf[PNRequestInfo]
    val timeToLive = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(6.hour.toSeconds)
    val tokens = devicesInfo.map(_.token)
    val validDeviceIds = devicesInfo.map(_.deviceId)
    val payload = GCMHttpPNPayload(registration_ids = tokens, priority = Option(pnInfo.priority.toString), appDataWithId, time_to_live = Some(timeToLive), dry_run = Option(message.isTestRequest))

    List(GCMPayloadEnvelope(message.id, message.clientId, validDeviceIds, pnInfo.appName, message.contextId.orEmpty, payload, message.meta))
  }
}
