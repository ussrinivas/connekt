package com.flipkart.connekt.busybees.streams.flows

import akka.actor.ActorSystem
import akka.stream.FanOutShape2
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import com.flipkart.connekt.commons.entities.DeviceDetails
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PNRequestInfo}
import com.flipkart.connekt.commons.services.{ConnektConfig, DeviceDetailsService}
import com.flipkart.connekt.commons.utils.StringUtils

class APNorFCMLogic(val shape: FanOutShape2[ConnektRequest, ConnektRequest, ConnektRequest])(implicit actorSystem: ActorSystem) extends GraphStageLogic(shape) {

  val isFCMEnabled:Boolean = ConnektConfig.getBoolean("isFCMEnabledForIOS").getOrElse(false)
  var apnsDemand = false
  var fcmDemand = false

  override def preStart(): Unit = {
    pull(shape.in)
  }

  setHandler(shape.in, new InHandler {
    override def onPush(): Unit = {
      grab(shape.in) match {
        case request =>
          val (apnsRequest, fcmRequest) = partitionConnektRequest(request)
          if(apnsRequest !=null & isAvailable(shape.out0)) {
            push(shape.out0, apnsRequest)
            apnsDemand = false
          }

          if(fcmRequest !=null & isAvailable(shape.out1)) {
            push(shape.out1, fcmRequest)
            fcmDemand = false
          }
      }
    }
  })


  setHandler(shape.out0, new OutHandler {
    override def onPull(): Unit = {
      if (!apnsDemand) {
        apnsDemand = true
      }
      if (!hasBeenPulled(shape.in)) {
        pull(shape.in)
      }
    }
  })


  setHandler(shape.out1, new OutHandler {
    override def onPull(): Unit = {
      if (!fcmDemand) {
        fcmDemand = true
      }

      if (!hasBeenPulled(shape.in)) {
        pull(shape.in)
      }
    }
  })


  private def partitionConnektRequest(request: ConnektRequest): (ConnektRequest, ConnektRequest) = {
    val pnInfo = request.channelInfo.asInstanceOf[PNRequestInfo]
    val deviceInfo: List[DeviceDetails] = DeviceDetailsService.get(pnInfo.appName, pnInfo.deviceIds).get
    val (fcm, apns) = deviceInfo.partition(deviceDetail => shouldSendViaFCM(deviceDetail))
    val apnsRequest: ConnektRequest = if(apns.isEmpty) {
      null
    } else {
      request.copy(channelInfo = pnInfo.copy(deviceIds = apns.map(_.deviceId).toSet))
    }

    val fcmRequest:ConnektRequest = if (fcm.isEmpty) {
      null
    } else {
      request.copy(channelInfo = pnInfo.copy(deviceIds = fcm.map(_.deviceId).toSet))
    }

    (apnsRequest, fcmRequest)
  }

  private def shouldSendViaFCM(deviceDetail: DeviceDetails): Boolean = {
      isFCMEnabled & !StringUtils.isNullOrEmpty(deviceDetail.fcmToken)
  }
}
