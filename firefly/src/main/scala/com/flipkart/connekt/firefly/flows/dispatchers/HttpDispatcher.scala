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
package com.flipkart.connekt.firefly.flows.dispatchers

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import com.flipkart.connekt.busybees.models.WAContactTracker
import com.flipkart.connekt.commons.services.{ConnektConfig, KeyChainManager}
import com.flipkart.connekt.firefly.sinks.http.HttpRequestTracker
import com.typesafe.config.Config
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.ssl.{TrustManagerConfig, TrustStoreConfig}

import scala.concurrent.ExecutionContextExecutor

class HttpDispatcher(actorSystemConf: Config) {

  implicit val httpSystem: ActorSystem = ActorSystem("firefly-http-out", actorSystemConf)
  implicit val httpMat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = httpSystem.dispatcher

  private val insecureHttpFlow = {
    // TODO :: Appname
    val certificate = KeyChainManager.getWhatsAppCredentials("flipkart").get.getCertificateStr
    val trustStoreConfig = TrustStoreConfig(Some(certificate), None).withStoreType("PEM")
    val pipelineLimit = ConnektConfig.getInt("wa.contact.check.pipeline.limit").get
    val maxConnections = ConnektConfig.getInt("wa.contact.check.max.parallel.connections").get
    val trustManagerConfig = TrustManagerConfig().withTrustStoreConfigs(List(trustStoreConfig))
    val badSslConfig = AkkaSSLConfig().mapSettings(s => s.withLoose(s.loose
      .withAcceptAnyCertificate(true)
      .withDisableHostnameVerification(true)
    ).withTrustManagerConfig(trustManagerConfig))
    val badCtx = Http().createClientHttpsContext(badSslConfig)
    Http().superPool[WAContactTracker](badCtx, ConnectionPoolSettings(httpSystem).withPipeliningLimit(pipelineLimit).withMaxConnections(maxConnections))(httpMat)
  }

  val httpPoolFlow = Http().superPool[HttpRequestTracker]()(httpMat)

}

object HttpDispatcher {

  var dispatcher: Option[HttpDispatcher] = None

  def apply(config: Config) = {
    if (dispatcher.isEmpty) {
      dispatcher = Some(new HttpDispatcher(config))
    }
  }

  def insecureHttpFlow = dispatcher.map(_.insecureHttpFlow).get

  def httpFlow = dispatcher.map(_.httpPoolFlow).get

}
