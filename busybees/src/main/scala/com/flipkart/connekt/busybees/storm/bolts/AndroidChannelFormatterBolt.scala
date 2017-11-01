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

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.commons.entities.{DeviceDetails, MobilePlatform}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.services.DeviceDetailsService
import org.apache.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import org.apache.storm.topology.base.BaseBasicBolt
import org.apache.storm.tuple.{Fields, Tuple, TupleImpl, Values}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._

import scala.concurrent.duration._

/**
  * Created by saurabh.mimani on 29/10/17.
  */
abstract class AndroidChannelFormatterBolt  extends BaseBasicBolt {
  lazy val stencilService = ServiceFactory.getStencilService

  override def execute(input: Tuple, collector: BasicOutputCollector): Unit = {
    val connektRequest = input.asInstanceOf[TupleImpl].get("connektRequest").asInstanceOf[ConnektRequest]
    try {
      val pnInfo = connektRequest.channelInfo.asInstanceOf[PNRequestInfo]

      val devicesInfo = DeviceDetailsService.get(pnInfo.appName, pnInfo.deviceIds).get.toSeq
      val validDeviceIds = devicesInfo.map(_.deviceId)
      val invalidDeviceIds = pnInfo.deviceIds.diff(validDeviceIds.toSet)

      invalidDeviceIds.map(
        PNCallbackEvent(
          connektRequest.id,
          connektRequest.clientId,
          _,
          InternalStatus.MissingDeviceInfo,
          MobilePlatform.ANDROID,
          pnInfo.appName,
          connektRequest.contextId.orEmpty
        )
      ).enqueue
      ServiceFactory.getReportingService.recordPushStatsDelta(
        connektRequest.clientId,
        connektRequest.contextId,
        connektRequest.meta.get("stencilId").map(_.toString),
        Option(connektRequest.platform),
        connektRequest.appName,
        InternalStatus.MissingDeviceInfo,
        invalidDeviceIds.size
      )

      val androidStencil = stencilService.getStencilsByName(s"ckt-${pnInfo.appName.toLowerCase}-android").head
      val appDataWithId = stencilService.materialize(androidStencil, connektRequest.channelData.asInstanceOf[PNRequestData].data).asInstanceOf[String].getObj[ObjectNode]
        .put("connektRequestId", connektRequest.id)
        .put("contextId", connektRequest.contextId.orEmpty)

      val ttl = connektRequest.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(6.hour.toSeconds)

      if (devicesInfo.nonEmpty && ttl > 0) {
        val payload = createPayload(connektRequest, devicesInfo, appDataWithId)
        collector.emit(new Values(payload))
      } else if (devicesInfo.nonEmpty) {
        ConnektLogger(LogFile.PROCESSORS).warn(s"AndroidChannelFormatter dropping ttl-expired connektRequest: ${connektRequest.id}")
        devicesInfo.map(
          d => PNCallbackEvent(
            connektRequest.id,
            connektRequest.clientId,
            d.deviceId,
            InternalStatus.TTLExpired,
            MobilePlatform.ANDROID,
            d.appName,
            connektRequest.contextId.orEmpty
          )
        ).enqueue
        ServiceFactory.getReportingService.recordPushStatsDelta(
          connektRequest.clientId,
          connektRequest.contextId,
          connektRequest.meta.get("stencilId").map(_.toString),
          Option(connektRequest.platform),
          connektRequest.appName,
          InternalStatus.TTLExpired,
          devicesInfo.size
        )
        List.empty[GCMPayloadEnvelope]
      } else
        List.empty[GCMPayloadEnvelope]
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"AndroidChannelFormatter error for ${connektRequest.id}", e)
        throw new ConnektPNStageException(
          connektRequest.id,
          connektRequest.clientId,
          connektRequest.destinations,
          InternalStatus.StageError,
          connektRequest.appName,
          connektRequest.platform,
          connektRequest.contextId.orEmpty,
          connektRequest.meta,
          "AndroidChannelFormatterBolt::".concat(e.getMessage),
          e
        )
    }
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer): Unit = {
    declarer.declare(new Fields("formattedRequest"))
  }

  def createPayload(connektRequest:ConnektRequest,
                    devices:Seq[DeviceDetails],
                    appDataWithId:Any) : List[GCMPayloadEnvelope]
}
