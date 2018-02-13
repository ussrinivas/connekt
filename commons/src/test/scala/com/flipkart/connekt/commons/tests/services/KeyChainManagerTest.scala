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
package com.flipkart.connekt.commons.tests.services

import java.io.{File, PrintWriter}

import com.flipkart.connekt.commons.entities.{AppleCredential, GoogleCredential, MobilePlatform, SimpleCredential}
import com.flipkart.connekt.commons.services.KeyChainManager
import com.flipkart.connekt.commons.tests.CommonsBaseTest
import com.flipkart.connekt.commons.utils.StringUtils

class KeyChainManagerTest extends CommonsBaseTest {

  val simpleName = "creds.simple." + StringUtils.generateRandomStr(6)
  val simpleCredentials = SimpleCredential("username", "password")

  val appleName = "creds.apple." + StringUtils.generateRandomStr(6)

  val fileName = StringUtils.generateRandomStr(7) + ".log"

  val file = new PrintWriter(new File("/tmp/" + fileName ))
  file.write(StringUtils.generateRandomStr(100))

  val appleCreds = AppleCredential(new File("/tmp/" + fileName), "passkey")

  val googleCredentials = GoogleCredential("449956124703","AAAAaMN5mB8:APA91bEOs_7pPVsrcxfR")

  "CredentialManagerTest" should "add/get simple " in {

    noException shouldBe thrownBy {
      KeyChainManager.addSimpleCredential(simpleName,simpleCredentials)
    }

    KeyChainManager.getSimpleCredential(simpleName).nonEmpty shouldEqual true
    KeyChainManager.getSimpleCredential(simpleName).get shouldEqual simpleCredentials

  }

  "CredentialManagerTest" should "add/get apple" in {
    noException shouldBe thrownBy {
      KeyChainManager.addAppleCredentials(appleName,appleCreds)
    }
    KeyChainManager.getAppleCredentials(appleName).nonEmpty shouldEqual true

    println(KeyChainManager.getAppleCredentials(appleName).get)

    KeyChainManager.getAppleCredentials(appleName).get.passkey shouldEqual appleCreds.passkey

    println("Certificate FILE ======> "+  KeyChainManager.getAppleCredentials(appleName).get.getCertificateFile)

    val x = KeyChainManager.getAppleCredentials(appleName).get.getCertificateFile
    x should not be appleCreds.getCertificateFile

  }

  "CredentialManagerTest" should "get GoogleCredential for android" in {
    noException shouldBe thrownBy {
      KeyChainManager.addGoogleCredential("retailapp",googleCredentials)
    }

    KeyChainManager.getGoogleCredential("retailapp",MobilePlatform.ANDROID)
    println(KeyChainManager.getGoogleCredential("retailapp",MobilePlatform.ANDROID).get)

    KeyChainManager.getGoogleCredential("retailapp",MobilePlatform.ANDROID).get.projectId shouldEqual "449956124703"
    KeyChainManager.getGoogleCredential("retailapp",MobilePlatform.ANDROID).get.apiKey shouldEqual "AAAAaMN5mB8:APA91bEOs_7pPVsrcxfR"
  }
}
