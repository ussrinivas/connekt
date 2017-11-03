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

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import com.flipkart.connekt.busybees.models.GCMRequestTracker
import com.flipkart.connekt.busybees.storm.models.HttpRequestAndTracker
import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.commons.entities.MobilePlatform
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{FCMXmppRequest, GCMPayloadEnvelope, GCMXmppPNPayload}
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.services.{ConnektConfig, KeyChainManager}
import com.flipkart.connekt.commons.utils.StringUtils._
import org.apache.storm.topology.base.BaseBasicBolt
import org.apache.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import org.apache.storm.tuple.{Fields, Tuple, TupleImpl, Values}

class GCMXmppDispatcherPrepareBolt extends BaseBasicBolt {

  private val sendUri = Uri(ConnektConfig.getString("fcm.send.uri").getOrElse("https://fcm.googleapis.com/fcm/send"))

  override def execute(input: Tuple, collector: BasicOutputCollector): Unit = {
    val message = input.asInstanceOf[TupleImpl].get("simplifyRequest").asInstanceOf[GCMPayloadEnvelope]
    try {
      ConnektLogger(LogFile.PROCESSORS).debug("GCMDispatcherPrepare received message: {}", supplier(message.messageId))
      ConnektLogger(LogFile.PROCESSORS).trace("GCMDispatcherPrepare received message: {}", supplier(message))

      KeyChainManager.getGoogleCredential(message.appName).map { credential =>
        val xmppRequest:FCMXmppRequest = FCMXmppRequest(message.gcmPayload.asInstanceOf[GCMXmppPNPayload], credential)
        val requestTracker = GCMRequestTracker(message.messageId, message.clientId, message.deviceId, message.appName, message.contextId, message.meta)
        collector.emit(new Values(xmppRequest, requestTracker))
      }
    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"GCMXmppDispatcherPrepare failed with ${e.getMessage}", e)
        throw new ConnektPNStageException(message.messageId, message.clientId, message.deviceId.toSet, InternalStatus.StageError, message.appName, MobilePlatform.ANDROID, message.contextId, message.meta, s"GCMXmppDispatcherPrepare-${e.getMessage}", e)
    }
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer): Unit = {
    declarer.declare(new Fields("gcmXmppRequest", "gcmXmppRequestTracker"))
  }

}
