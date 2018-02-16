package com.flipkart.connekt.busybees.streams.flows.formaters

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.entities.{DeviceDetails, MobilePlatform}
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels._

import scala.concurrent.duration._
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.ExecutionContextExecutor

class IOSFCMHttpFormatter (parallelism: Int)(implicit ec: ExecutionContextExecutor) extends FCMChannelFormatter(parallelism)(ec) {

  override def createPayload(message: ConnektRequest, devicesInfo: Seq[DeviceDetails]): List[GCMPayloadEnvelope] = {

    val pnInfo = message.channelInfo.asInstanceOf[PNRequestInfo]
    val timeToLive = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(6.hour.toSeconds)
    val tokens = devicesInfo.map(_.fcmToken)
    val validDeviceIds = devicesInfo.map(_.deviceId)
    val appDataWithId = getAppDataWithId(pnInfo, message)
    val notificationData = getNotificationData(pnInfo, message)

    val iosFcmParams = ServiceFactory.getUserProjectConfigService.getProjectConfiguration(pnInfo.appName, "fcm-params").get.get.value.getObj[Map[String, Boolean]]
    val contentAvailable = Option(iosFcmParams("content_available")).getOrElse(false)
    val mutableContent = Option(iosFcmParams("mutable_content")).getOrElse(false)

    val payload = GCMHttpPNPayload(registration_ids = tokens, priority = Option(pnInfo.priority).map(_.toString), data = appDataWithId, notification = notificationData,
      content_available = contentAvailable, mutable_content = mutableContent, time_to_live = Some(timeToLive), dry_run = Option(message.isTestRequest))

    List(GCMPayloadEnvelope(message.id, message.clientId, validDeviceIds, pnInfo.appName, pnInfo.platform, message.contextId.orEmpty, payload, message.meta))
  }

  override def getAppDataWithId(pnInfo: PNRequestInfo, message: ConnektRequest): ObjectNode = {
    val iosFcmStencil = stencilService.getStencilsByName(s"ckt-${pnInfo.appName.toLowerCase}-ios-fcm")
    val data = stencilService.materialize(iosFcmStencil.find(s => s.component.equals("data")).orNull, message.channelData.asInstanceOf[PNRequestData].data).asInstanceOf[String].getObj[ObjectNode]
      .put("messageId", message.id)
      .put("contextId", message.contextId.orEmpty)
    data
  }

  override def getNotificationData(pnInfo: PNRequestInfo, message:ConnektRequest):ObjectNode = {
    val iosFcmStencil = stencilService.getStencilsByName(s"ckt-${pnInfo.appName.toLowerCase}-ios-fcm")
    val data = stencilService.materialize(iosFcmStencil.find(s => s.component.equals("notification")).orNull, message.channelData.asInstanceOf[PNRequestData].data).asInstanceOf[String].getObj[ObjectNode]
    data
  }
}
