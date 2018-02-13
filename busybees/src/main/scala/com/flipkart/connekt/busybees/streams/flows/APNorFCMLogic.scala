package com.flipkart.connekt.busybees.streams.flows

import akka.actor.ActorSystem
import akka.stream.FanOutShape2
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import com.flipkart.connekt.commons.entities.DeviceDetails
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PNRequestInfo}
import com.flipkart.connekt.commons.services.DeviceDetailsService

class APNorFCMLogic(val shape: FanOutShape2[ConnektRequest, ConnektRequest, ConnektRequest])(implicit actorSystem: ActorSystem) extends GraphStageLogic(shape) {

  var self: StageActor = _

  val apnsRequestQueue: scala.collection.mutable.Queue[ConnektRequest] = collection.mutable.Queue[ConnektRequest]()
  val fcmRequestQueue: scala.collection.mutable.Queue[ConnektRequest] = collection.mutable.Queue[ConnektRequest]()

  //make this configurable
  val apnsMaxBufferCount = 10
  val fcmMaxBufferCount = 10
  val isFCMEnabled = true

  override def preStart(): Unit = {
    pull(shape.in)
  }

  setHandler(shape.in, new InHandler {
    override def onPush(): Unit = {
      grab(shape.in) match {
        case request =>
          val pnInfo = request.channelInfo.asInstanceOf[PNRequestInfo]
          val (fcmRequest, apnsRequest) = partitionConnektRequest(request)
          if(isAvailable(shape.out0) & apnsRequestQueue.nonEmpty) {
            push(shape.out0, apnsRequestQueue.dequeue())
          } else if (apnsMaxBufferCount < apnsRequestQueue.size)
            apnsRequestQueue.enqueue(apnsRequest)

          if(isAvailable(shape.out1) & fcmRequestQueue.nonEmpty) {
            push(shape.out1, fcmRequestQueue.dequeue())
          } else if (fcmMaxBufferCount < fcmRequestQueue.size)
            fcmRequestQueue.enqueue(fcmRequest)
      }
    }

    override def onUpstreamFinish(): Unit = completeStage()
  })


  setHandler(shape.out0, new OutHandler {
    override def onPull(): Unit = {
      if (apnsRequestQueue.nonEmpty)
        push(shape.out0, apnsRequestQueue.dequeue())
      if (!hasBeenPulled(shape.in)) {
        pull(shape.in)
      }
    }
  })


  setHandler(shape.out1, new OutHandler {
    override def onPull(): Unit = {
      if (fcmRequestQueue.nonEmpty)
        push(shape.out1, fcmRequestQueue.dequeue())

      if (!hasBeenPulled(shape.in)) {
        pull(shape.in)
      }
    }
  })


  private def partitionConnektRequest(request: ConnektRequest): (ConnektRequest, ConnektRequest) = {
    val pnInfo = request.channelInfo.asInstanceOf[PNRequestInfo]
    val deviceInfo: List[DeviceDetails] = DeviceDetailsService.get(pnInfo.appName, pnInfo.deviceIds).get
    val (fcm, apns) = deviceInfo.partition(deviceDetail => shouldSendViaAPN(deviceDetail))

    val apnChannelInfo = pnInfo.copy(deviceIds = apns.map(_.deviceId).toSet)
    val fcmChannelInfo = pnInfo.copy(deviceIds = fcm.map(_.deviceId).toSet)

    (request.copy(channelInfo = apnChannelInfo), request.copy(channelInfo = fcmChannelInfo))
  }

  private def shouldSendViaAPN(deviceDetail: DeviceDetails): Boolean = {
      isFCMEnabled & !deviceDetail.fcmToken.isEmpty
  }
}
