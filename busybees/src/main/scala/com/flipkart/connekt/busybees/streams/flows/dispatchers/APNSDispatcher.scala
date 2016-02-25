package com.flipkart.connekt.busybees.streams.flows.dispatchers

import java.util.concurrent.ExecutionException

import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.flipkart.connekt.commons.entities.{MobilePlatform, AppleCredential}
import com.flipkart.connekt.commons.factories.{ServiceFactory, ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{PNCallbackEvent, APSPayloadEnvelope, APSPayload, iOSPNPayload}
import com.flipkart.connekt.commons.services.{DeviceDetailsService, KeyChainManager}
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.commons.utils.StringUtils._
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification
import com.relayrides.pushy.apns.{ApnsClient, ClientNotConnectedException}
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions._

/**
 * Created by kinshuk.bairagi on 05/02/16.
 */
class APNSDispatcher(appNames: List[String] = List.empty) extends GraphStage[FlowShape[APSPayloadEnvelope, PNCallbackEvent]] {

  type AppName = String
  val in = Inlet[APSPayloadEnvelope]("APNSDispatcher.In")
  val out = Outlet[PNCallbackEvent]("APNSDispatcher.Out")

  override def shape = FlowShape.of(in, out)

  var clients = scala.collection.mutable.Map[AppName, ApnsClient[SimpleApnsPushNotification]]()

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    setHandler(in, new InHandler {
      override def onPush(): Unit = try {

        val envelope = grab(in)
        val message = envelope.apsPayload.asInstanceOf[iOSPNPayload]
        ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher:: onPush:: Received Message: $envelope")

        val resultId = StringUtils.generateRandomStr(12)

        ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher:: onPush:: Send Payload: " + message.data.asInstanceOf[AnyRef].getJson)
        val pushNotification = new SimpleApnsPushNotification(message.token, null, message.data.asInstanceOf[AnyRef].getJson)
        val client = clients.getOrElseUpdate(envelope.appName, getAPNSClient(envelope.appName))
        val events = ListBuffer[PNCallbackEvent]()

        try {

          val pushNotificationResponse = client.sendNotification(pushNotification).get()

          pushNotificationResponse.isAccepted match {
            case true =>
              events.addAll(envelope.deviceId.map(PNCallbackEvent(envelope.messageId, _, MobilePlatform.IOS.toString, "APNS_ACCEPTED", envelope.appName, "", "", System.currentTimeMillis())))
            case false =>
              ConnektLogger(LogFile.PROCESSORS).error("APNSDispatcher:: onPush :: Notification rejected by the APNs gateway: " + pushNotificationResponse.getRejectionReason)

              if (pushNotificationResponse.getTokenInvalidationTimestamp != null) {
                 //This device is now invalid remove device registration.
                envelope.deviceId.foreach(DeviceDetailsService.delete(envelope.appName, _))
                events.addAll(envelope.deviceId.map(PNCallbackEvent(envelope.messageId, _, MobilePlatform.IOS.toString, "APNS_REJECTED_TOKEN_EXPIRED", envelope.appName, "", "", System.currentTimeMillis())))
                ConnektLogger(LogFile.PROCESSORS).error(s"APNSDispatcher:: Token Invalid [${message.token}] since " + pushNotificationResponse.getTokenInvalidationTimestamp)
              } else {
                events.addAll(envelope.deviceId.map(PNCallbackEvent(envelope.messageId, _, MobilePlatform.IOS.toString, "APNS_REJECTED", envelope.appName, "", "", System.currentTimeMillis())))
              }
          }
        } catch {
          case e: ExecutionException =>
            ConnektLogger(LogFile.PROCESSORS).error(s"APNSDispatcher:: onPush :: Failed to send push notification: ${envelope.messageId}, ${e.getMessage}", e)
            events.addAll(envelope.deviceId.map(PNCallbackEvent(envelope.messageId, _, MobilePlatform.IOS.toString, "APNS_SEND_FAILURE", envelope.appName, "", "", System.currentTimeMillis())))

            if (e.getCause.isInstanceOf[ClientNotConnectedException]) {
              ConnektLogger(LogFile.PROCESSORS).debug("APNSDispatcher:: onPush :: Waiting for APNSClient to reconnect.")
              client.getReconnectionFuture.await()
              ConnektLogger(LogFile.PROCESSORS).debug("APNSDispatcher:: onPush :: APNSClient Reconnected.")
            }
        }

        push(out, events.head)

      } catch {
        case e: Throwable =>
          ConnektLogger(LogFile.PROCESSORS).error(s"APNSDispatcher:: onPush :: ${e.getMessage}", e)
      }

      private def getAPNSClient(appName: String) = {
        val credential = KeyChainManager.getAppleCredentials(appName).get
        val client = new ApnsClient[SimpleApnsPushNotification](credential.getCertificateFile, credential.passkey)
        client.connect(ApnsClient.PRODUCTION_APNS_HOST).await()
        client
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher:: onPull")

        pull(in)
      }
    })

    override def preStart(): Unit = {
      ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher:: preStart")
      /* Create APNSClients for all apps */
      appNames.foreach(app => {
        ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher:: Starting $app APNSClient.")
        val credential = KeyChainManager.getAppleCredentials(app).get
        val client = new ApnsClient[SimpleApnsPushNotification](credential.getCertificateFile, credential.passkey)
        client.connect(ApnsClient.PRODUCTION_APNS_HOST).await(5000)
        clients += app -> client
      })

      super.preStart()
    }

    override def postStop(): Unit = {
      ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher:: postStop")

      // Disconnect all the APNSClients prior to stop
      clients.foreach(kv => {
        ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher:: Stopping ${kv._1} APNSClient.")
        kv._2.disconnect().await(5000)
      })
      super.postStop()
    }
  }
}
