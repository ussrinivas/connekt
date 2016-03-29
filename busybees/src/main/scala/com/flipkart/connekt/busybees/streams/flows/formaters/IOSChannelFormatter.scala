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

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.streams.flows.NIOFlow
import com.flipkart.connekt.commons.entities.MobilePlatform
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.helpers.CallbackRecorder
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.{DeviceDetailsService, PNStencilService, StencilService}
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class IOSChannelFormatter(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends NIOFlow[ConnektRequest, APSPayloadEnvelope](parallelism)(ec) with CallbackRecorder {


  override def map: (ConnektRequest) => List[APSPayloadEnvelope] = message => {

    try {

      ConnektLogger(LogFile.PROCESSORS).info(s"IOSChannelFormatter:: Received Message: ${message.getJson}")
      val pnInfo = message.channelInfo.asInstanceOf[PNRequestInfo]

      val devicesInfo = DeviceDetailsService.get(pnInfo.appName, pnInfo.deviceId).get
      val invalidDeviceIds = pnInfo.deviceId.diff(devicesInfo.map(_.deviceId))
      invalidDeviceIds.map(PNCallbackEvent(message.id, _, "INVALID_DEVICE_ID", MobilePlatform.IOS, pnInfo.appName, message.contextId.orEmptyString, "")).persist

      val listOfTokenDeviceId = devicesInfo.map(r => (r.token, r.deviceId))
      val iosStencil = StencilService.get(s"ckt-${pnInfo.appName.toLowerCase}-ios").get

      val ttlInMillis =  message.expiryTs.getOrElse(System.currentTimeMillis() + 6.hours.toMillis)
      val apnsEnvelopes = listOfTokenDeviceId.map(td => {
        val payloadData = PNStencilService.getPNData(iosStencil, message.channelData.asInstanceOf[PNRequestData].data).getObj[ObjectNode]
        val apnsPayload = iOSPNPayload(td._1, ttlInMillis, payloadData)
        APSPayloadEnvelope(message.id, td._2, pnInfo.appName, apnsPayload)
      })


      if (apnsEnvelopes.nonEmpty && ttlInMillis > System.currentTimeMillis()) {
        val dryRun = message.meta.get("x-perf-test").exists(_.trim.equalsIgnoreCase("true"))
        if (!dryRun) {
          ConnektLogger(LogFile.PROCESSORS).debug(s"IOSChannelFormatter:: PUSHED downstream for ${message.id}")
          apnsEnvelopes
        }
        else {
          ConnektLogger(LogFile.PROCESSORS).debug(s"IOSChannelFormatter:: Dry Run Dropping msgId: ${message.id}")
          List.empty[APSPayloadEnvelope]
        }
      } else {
        ConnektLogger(LogFile.PROCESSORS).warn(s"IOSChannelFormatter:: No Valid Output found for : ${pnInfo.deviceId}, msgId: ${message.id}")
        List.empty[APSPayloadEnvelope]
      }

    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"IOSChannelFormatter:: OnFormat error", e)
        List.empty[APSPayloadEnvelope]
    }
  }
}
