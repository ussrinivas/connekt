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
package com.flipkart.connekt.busybees.encryption

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security._
import java.util
import java.util.Base64
import javax.crypto._
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}

import com.google.common.primitives.Bytes
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.{ECPrivateKeySpec, ECPublicKeySpec}

sealed case class WebPushEncryptionResult(salt: Array[Byte], serverPublicKey: Array[Byte], encodedData: Array[Byte])

object WebPushEncryptionUtils {

  private val hmacSHA256 = "HmacSHA256"
  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  def encrypt(userPublicKey: String, userAuth: String, payload: String): WebPushEncryptionResult = {
    val salt = generateSalt()
    val serverKeys = EllipticCurveKeyUtils.generateServerKeyPair()
    val serverPublicKeyBytes = EllipticCurveKeyUtils.publicKeyToBytes(serverKeys.getPublic.asInstanceOf[java.security.interfaces.ECPublicKey])
    val publicKey = EllipticCurveKeyUtils.loadP256Dh(userPublicKey)
    val auth = Base64.getUrlDecoder.decode(userAuth)
    val sharedSecret = EllipticCurveKeyUtils.generateSharedSecret(serverKeys, publicKey)
    val clientPublicKeyBytes = EllipticCurveKeyUtils.publicKeyToBytes(publicKey)
    val nonceInfo = generateInfo(serverPublicKeyBytes, clientPublicKeyBytes, "nonce".getBytes(StandardCharsets.UTF_8))
    val contentEncryptionKeyInfo = generateInfo(serverPublicKeyBytes, clientPublicKeyBytes, "aesgcm".getBytes(StandardCharsets.UTF_8))
    val encryptedBytes = encryptPayload(payload, sharedSecret, salt, contentEncryptionKeyInfo, nonceInfo, auth)

    WebPushEncryptionResult(salt, serverPublicKeyBytes, encryptedBytes)
  }

  private def generateInfo(serverPublicKey: Array[Byte], clientPublicKey: Array[Byte], `type`: Array[Byte]): Array[Byte] = {
    val outputStream = new ByteArrayOutputStream()
    outputStream.write(Bytes.concat(
      "Content-Encoding: ".getBytes(StandardCharsets.UTF_8),
      `type`, Array[Byte](0.toByte),
      "P-256".getBytes(StandardCharsets.UTF_8),
      Array[Byte](0.toByte, 0.toByte, 65.toByte),
      clientPublicKey,
      Array[Byte](0.toByte, 65.toByte),
      serverPublicKey
    ))
    outputStream.toByteArray
  }

  def createEncryptionHeader(salt: Array[Byte]) = s"salt=${Base64.getUrlEncoder.encodeToString(salt)}"

  def createCryptoKeyHeader(serverPublic: Array[Byte]) = s"dh=${Base64.getUrlEncoder.encodeToString(serverPublic)}"

  private def hkdfExtract(secretKey: Array[Byte], salt: Array[Byte], messageToExtract: Array[Byte], lengthToExtract: Int): Array[Byte] = {
    val outerMac = Mac.getInstance(hmacSHA256)
    outerMac.init(new SecretKeySpec(salt, hmacSHA256))
    val outerResult = outerMac.doFinal(secretKey)

    val innerMac = Mac.getInstance(hmacSHA256)
    innerMac.init(new SecretKeySpec(outerResult, hmacSHA256))

    val innerResult = innerMac.doFinal(messageToExtract :+ 1.toByte)
    util.Arrays.copyOf(innerResult, lengthToExtract)
  }

  private def encryptPayload(message: String, shared_secret: Array[Byte], salt: Array[Byte], content_encryption_key_info: Array[Byte], nonce_info: Array[Byte], client_auth: Array[Byte]) = {
    val prk = hkdfExtract(shared_secret, client_auth, "Content-Encoding: auth\u0000".getBytes(StandardCharsets.UTF_8), 32)
    val content_encryption_key = hkdfExtract(prk, salt, content_encryption_key_info, 16)
    val nonce = hkdfExtract(prk, salt, nonce_info, 12)
    val record = ("\u0000\u0000" + message).getBytes(StandardCharsets.UTF_8)
    encryptWithAESGCM128(nonce, content_encryption_key, record)
  }

  private def encryptWithAESGCM128(nonce: Array[Byte], content_encryption_key: Array[Byte], record: Array[Byte]): Array[Byte] = {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding", "SunJCE")
    val key = new SecretKeySpec(content_encryption_key, "AES")
    val spec = new GCMParameterSpec(128, nonce)
    cipher.init(Cipher.ENCRYPT_MODE, key, spec)
    cipher.doFinal(record)
  }

  private def generateSalt(): Array[Byte] = {
    val salt = Array.ofDim[Byte](16)
    SecureRandom.getInstance("SHA1PRNG").nextBytes(salt)
    salt
  }

  def loadPublicKey(encodedPublicKey: String): PublicKey = {
    val decodedPublicKey = base64Decode(encodedPublicKey)
    val kf = KeyFactory.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME)
    val ecSpec = ECNamedCurveTable.getParameterSpec("prime256v1") // prime256v1 is NIST P-256
    val point = ecSpec.getCurve.decodePoint(decodedPublicKey)
    val pubSpec = new ECPublicKeySpec(point, ecSpec)
    kf.generatePublic(pubSpec)
  }

  def loadPrivateKey(encodedPrivateKey: String): PrivateKey = {
    val decodedPrivateKey = base64Decode(encodedPrivateKey)
    val params = ECNamedCurveTable.getParameterSpec("prime256v1") // prime256v1 is NIST P-256
    val prvkey = new ECPrivateKeySpec(new BigInteger(1, decodedPrivateKey), params)
    val kf = KeyFactory.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME)
    kf.generatePrivate(prvkey)
  }

  private def base64Decode(base64Encoded: String): Array[Byte] = {
    if (base64Encoded.contains("+") || base64Encoded.contains("/")) {
      Base64.getDecoder.decode(base64Encoded)
    } else {
      Base64.getUrlDecoder.decode(base64Encoded)
    }
  }
}
