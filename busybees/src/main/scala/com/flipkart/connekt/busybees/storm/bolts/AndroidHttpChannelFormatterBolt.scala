package com.flipkart.connekt.busybees.storm.bolts

import java.util.Random

import com.flipkart.connekt.commons.entities.DeviceDetails
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, GCMHttpPNPayload, GCMPayloadEnvelope, PNRequestInfo}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._
import scala.concurrent.duration._

/**
  * Created by saurabh.mimani on 27/10/17.
  */
class AndroidHttpChannelFormatterBolt extends AndroidChannelFormatterBolt {
  private lazy val xmppClients = ConnektConfig.getList[String]("android.protocol.xmpp-clients")


  override def createPayload(connektRequest:ConnektRequest,
                             devicesInfo:Seq[DeviceDetails],
                             appDataWithId:Any):List[GCMPayloadEnvelope] = {

    val pnInfo = connektRequest.channelInfo.asInstanceOf[PNRequestInfo]
    val timeToLive = connektRequest.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(6.hour.toSeconds)
    val tokens = devicesInfo.map(_.token)
    val validDeviceIds = devicesInfo.map(_.deviceId)
    val payload = GCMHttpPNPayload(registration_ids = tokens, priority = Option(pnInfo.priority).map(_.toString), appDataWithId, time_to_live = Some(timeToLive), dry_run = Option(connektRequest.isTestRequest))

    List(GCMPayloadEnvelope(connektRequest.id, connektRequest.clientId, validDeviceIds, pnInfo.appName, connektRequest.contextId.orEmpty, payload, connektRequest.meta))
  }
}
