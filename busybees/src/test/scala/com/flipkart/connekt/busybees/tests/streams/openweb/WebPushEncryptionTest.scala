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
package com.flipkart.connekt.busybees.tests.streams.openweb

import java.util.Date

import com.flipkart.connekt.busybees.encryption.WebPushEncryptionUtils
import com.flipkart.connekt.commons.tests.ConnektUTSpec
import io.jsonwebtoken.{Jwts, SignatureAlgorithm, SignatureException}
import org.jose4j.jws.{AlgorithmIdentifiers, JsonWebSignature}
import org.jose4j.jwt.JwtClaims


/**
  * Created by kinshuk.bairagi on 22/02/17.
  */
class WebPushEncryptionTest extends ConnektUTSpec {

  val privateKey = "vQ7OPEz9s2KogdXyJ3Y47nLS2oE7QSjHm7NFEEoV8X0"
  val publicKey = "BGFhUDXbv6bx3cZI0LintxwMroAD7VSzlRASzjLC3iU7bMIEsj0Kn1RJTbbNbGo7DzMZ8XUEKPemB5qN_6rNc_U"


  "WebPushEncryption" should "do generate jwt properly" in {
    val key = WebPushEncryptionUtils.loadPrivateKey(privateKey)

    val claims = new JwtClaims()
    claims.setAudience("https://fcm.googleapis.com")
    claims.setExpirationTimeMinutesInTheFuture(12 * 60)
    claims.setSubject("mailto:connekt-dev@flipkart.com")

    val jws = new JsonWebSignature()
    jws.setHeader("typ", "JWT")
    jws.setPayload(claims.toJson)

    jws.setKey(key)
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256)
    val compactJws = jws.getCompactSerialization.stripSuffix("=")

    println("WebPush1 " + compactJws)
    assert(verifyToken(compactJws))

  }

  "Webpush" should "using jjwt" in {
    val key = WebPushEncryptionUtils.loadPrivateKey(privateKey)

    val compactJws = Jwts.builder()
      .setHeaderParam("typ", "JWT")
      .setSubject("mailto:connekt-dev@flipkart.com")
      .setAudience("https://fcm.googleapis.com")
      .setExpiration(new Date(System.currentTimeMillis() + 3600000))
      .signWith(SignatureAlgorithm.ES256, key)
      .compact()

    println("WebPush2 " + compactJws)
    assert(verifyToken(compactJws))

  }


  def verifyToken(token: String): Boolean = {
    val pKey = WebPushEncryptionUtils.loadPublicKey(publicKey)
    try {
      Jwts.parser().setSigningKey(pKey).parseClaimsJws(token)
      //OK, we can trust this JWT
      true
    } catch {
      case e: SignatureException => e.printStackTrace()
        false
      //don't trust the JWT!
    }
  }

}
