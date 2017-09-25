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

import java.net.URL
import java.util.Base64

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.encryption.WebPushEncryptionUtils
import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.busybees.streams.flows.NIOFlow
import com.flipkart.connekt.commons.entities.MobilePlatform
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.{DeviceDetailsService, KeyChainManager}
import com.flipkart.connekt.commons.utils.NetworkUtils.URLFunctions
import com.flipkart.connekt.commons.utils.StringUtils._
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.jose4j.jws.{AlgorithmIdentifiers, JsonWebSignature}
import org.jose4j.jwt.JwtClaims

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class OpenWebChannelFormatter(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends NIOFlow[ConnektRequest, OpenWebStandardPayloadEnvelope](parallelism)(ec) {

  private lazy val stencilService = ServiceFactory.getStencilService

  override def map: ConnektRequest => List[OpenWebStandardPayloadEnvelope] = message => {

    try {
      ConnektLogger(LogFile.PROCESSORS).debug(s"OpenWebChannelFormatter received message: ${message.id}")

      val pnInfo = message.channelInfo.asInstanceOf[PNRequestInfo]

      val devicesInfo = DeviceDetailsService.get(pnInfo.appName, pnInfo.deviceIds).get
      val validDeviceIds = devicesInfo.map(_.deviceId)
      val invalidDeviceIds = pnInfo.deviceIds.diff(validDeviceIds.toSet)
      invalidDeviceIds.map(PNCallbackEvent(message.id, message.clientId, _, InternalStatus.MissingDeviceInfo, MobilePlatform.OPENWEB, pnInfo.appName, message.contextId.orEmpty)).persist
      ServiceFactory.getReportingService.recordPushStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Option(message.platform), message.appName, InternalStatus.MissingDeviceInfo, invalidDeviceIds.size)

      val ttl = message.expiryTs.map(expiry => (expiry - System.currentTimeMillis) / 1000).getOrElse(6.hour.toSeconds)
      val openWebStencil = stencilService.getStencilsByName(s"ckt-${pnInfo.appName.toLowerCase}-openweb").head

      if (ttl > 0) {
        if (!message.isTestRequest) {
          devicesInfo.flatMap(device => {
            //standard open-web (firefox, chrome, etc)
            if (device.token != null && device.token.nonEmpty && device.token.isValidUrl) {
              val token = device.token.replace("https://android.googleapis.com/gcm/send", "https://gcm-http.googleapis.com/gcm")
              val headers = scala.collection.mutable.Map("TTL" -> ttl.toString)
              val appDataWithId = stencilService.materialize(openWebStencil, message.channelData.asInstanceOf[PNRequestData].data).asInstanceOf[String].getObj[ObjectNode]
                .put("messageId", message.id)
                .put("contextId", message.contextId.orEmpty)
                .put("deviceId", device.deviceId).getJson

              val vapIdKeyPair = KeyChainManager.getKeyPairCredential(message.appName).get

              if (device.keys != null && device.keys.nonEmpty) {
                Try(WebPushEncryptionUtils.encrypt(device.keys("p256dh"), device.keys("auth"), appDataWithId)) match {
                  case Success(data) =>
                    headers += (
                      "Encryption" -> WebPushEncryptionUtils.createEncryptionHeader(data.salt),
                      "Content-Encoding" -> "aesgcm"
                    )

                    val claims = new JwtClaims()
                    claims.setAudience(new URL(token).origin)
                    claims.setExpirationTimeMinutesInTheFuture(12 * 60)
                    claims.setSubject("mailto:connekt-dev@flipkart.com")

                    val jws = new JsonWebSignature()
                    jws.setHeader("typ", "JWT")
                    jws.setPayload(claims.toJson)
                    jws.setKey(WebPushEncryptionUtils.loadPrivateKey(vapIdKeyPair.privateKey))
                    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256)
                    val compactJws = jws.getCompactSerialization.stripSuffix("=")

                    val publicKey = WebPushEncryptionUtils.loadPublicKey(vapIdKeyPair.publicKey).asInstanceOf[ECPublicKey].getQ.getEncoded(false)

                    headers +=(
                      "Crypto-Key" -> s"${WebPushEncryptionUtils.createCryptoKeyHeader(data.serverPublicKey)};p256ecdsa=${Base64.getUrlEncoder.encodeToString(publicKey).stripSuffix("=")}",
                      "Authorization" -> s"WebPush $compactJws"
                    )

                    if (token.startsWith("https://gcm-http.googleapis.com/gcm")) //pre-chrome-52.
                      headers += ("Authorization" -> s"key=${KeyChainManager.getGoogleCredential(message.appName).get.apiKey}")

                    List(OpenWebStandardPayloadEnvelope(message.id, message.clientId, device.deviceId, pnInfo.appName,
                      message.contextId.orEmpty, token, OpenWebStandardPayload(data.encodedData), headers.toMap, message.meta))
                  case Failure(e) =>
                    ConnektLogger(LogFile.PROCESSORS).error(s"OpenWebChannelFormatter error for ${message.id}", e)
                    PNCallbackEvent(message.id, message.clientId, device.deviceId, InternalStatus.EncryptionError, MobilePlatform.OPENWEB, device.appName, message.contextId.orEmpty, e.getMessage).enqueue
                    List.empty[OpenWebStandardPayloadEnvelope]
                }
              } else {
                List(OpenWebStandardPayloadEnvelope(message.id, message.clientId, device.deviceId, pnInfo.appName, message.contextId.orEmpty,
                  token, OpenWebStandardPayload(Array.empty), headers.toMap, message.meta))
              }
            } else {
              PNCallbackEvent(message.id, message.clientId, device.deviceId, InternalStatus.InvalidToken, MobilePlatform.OPENWEB, device.appName, message.contextId.orEmpty, "OpenWeb without or empty/invalid token not allowed").enqueue
              List.empty[OpenWebStandardPayloadEnvelope]
            }
          })
        } else {
          ConnektLogger(LogFile.PROCESSORS).info(s"OpenWebChannelFormatter dropping dry-run message: ${message.id}")
          List.empty[OpenWebStandardPayloadEnvelope]
        }

      } else {
        ConnektLogger(LogFile.PROCESSORS).warn(s"OpenWebChannelFormatter dropping ttl-expired message: ${message.id}")
        ServiceFactory.getReportingService.recordPushStatsDelta(message.clientId, message.contextId, message.meta.get("stencilId").map(_.toString), Option(message.platform), message.appName, InternalStatus.TTLExpired, devicesInfo.size)
        devicesInfo.map(d => PNCallbackEvent(message.id, message.clientId, d.deviceId, InternalStatus.TTLExpired, MobilePlatform.OPENWEB, d.appName, message.contextId.orEmpty)).enqueue
        List.empty[OpenWebStandardPayloadEnvelope]
      }
    }
    catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"OpenWebChannelFormatter error for ${message.id}", e)
        throw ConnektPNStageException(message.id, message.clientId, message.destinations, InternalStatus.StageError, message.appName, message.platform, message.contextId.orEmpty, message.meta, "OpenWebChannelFormatter::".concat(e.getMessage), e)
    }
  }

}
