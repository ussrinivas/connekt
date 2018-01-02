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
package com.flipkart.connekt.receptors.service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.services.KeyChainManager
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.ssl.{TrustManagerConfig, TrustStoreConfig}

import scala.concurrent.{ExecutionContextExecutor, Future}

object WebClientHelper {
  private val system: ActorSystem = ActorSystem("webClient")
  private val ec: ExecutionContextExecutor = system.dispatcher
  private val materializer: ActorMaterializer = ActorMaterializer()(system)
  ConnektLogger(LogFile.SERVICE).info(s"WebClient starting akka system : " + Thread.currentThread().getName)

  def getSystem: ActorSystem = system

  def getEc: ExecutionContextExecutor = ec

  def getMaterializer: ActorMaterializer = materializer

}


class WebClient {

  def callHttpService(httpRequest: HttpRequest, channel: Channel, appName: String): Future[HttpResponse] = {

    implicit val materializer = WebClientHelper.getMaterializer
    implicit val ec = WebClientHelper.getEc
    implicit val system = WebClientHelper.getSystem

    channel match {
      case Channel.WA =>
        val certificate = KeyChainManager.getWhatsAppCredentials(appName).get.getCertificateStr
        val trustStoreConfig = TrustStoreConfig(Some(certificate), None).withStoreType("PEM")
        val trustManagerConfig = TrustManagerConfig().withTrustStoreConfigs(List(trustStoreConfig))
        val badSslConfig = AkkaSSLConfig().mapSettings(s => s.withLoose(s.loose
          .withAcceptAnyCertificate(true)
          .withDisableHostnameVerification(true)
        ).withTrustManagerConfig(trustManagerConfig))
        val badCtx = Http().createClientHttpsContext(badSslConfig)
        Http().singleRequest(httpRequest, badCtx)
      case _ =>
        Http().singleRequest(httpRequest)
    }
  }
}

object WebClient {
  var instance: WebClient = _
  if (null == instance)
    this.synchronized {
      instance = new WebClient()
    }
  instance
}
