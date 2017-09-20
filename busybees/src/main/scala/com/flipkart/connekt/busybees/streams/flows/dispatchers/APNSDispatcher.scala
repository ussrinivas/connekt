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
package com.flipkart.connekt.busybees.streams.flows.dispatchers

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.Flow
import com.flipkart.connekt.busybees.models.APNSRequestTracker
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.{ConnektConfig, KeyChainManager}
import com.flipkart.connekt.commons.utils.FutureUtils._
import com.flipkart.connekt.commons.utils.StringUtils
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.turo.pushy.apns._
import com.turo.pushy.apns.metrics.dropwizard.DropwizardApnsClientMetricsListener
import com.turo.pushy.apns.util.SimpleApnsPushNotification
import io.netty.channel.nio.NioEventLoopGroup

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future, Promise, TimeoutException}
import scala.util.Try
import scala.util.control.NonFatal

object APNSDispatcher extends Instrumented {

  private val apnsHost: String = ConnektConfig.getOrElse("ios.apns.hostname", ApnsClientBuilder.PRODUCTION_APNS_HOST)
  private val apnsPort: Int = ConnektConfig.getInt("ios.apns.port").getOrElse(ApnsClientBuilder.DEFAULT_APNS_PORT)
  private val apnsConcurrency = ConnektConfig.getInt("ios.apns.concurrency").getOrElse(8)

  private val pingInterval = ConnektConfig.getInt("sys.ping.interval").getOrElse(30)
  private val responseTimeout = ConnektConfig.getInt("ios.apns.response.timeout").getOrElse(60)

  private [busybees] val clientGatewayCache = new ConcurrentHashMap[String, Future[ApnsClient]]()

  private[busybees] def removeClient(appName: String): Boolean = {
    ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher apns-client destroying $appName .")
    clientGatewayCache.get(appName).map(_.close())(scala.concurrent.ExecutionContext.Implicits.global)
    clientGatewayCache.remove(appName)
    registry.remove(getMetricName(appName))
  }

  private def createAPNSClient(appName: String): ApnsClient = {
    ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher starting $appName apns-client")
    val credential = KeyChainManager.getAppleCredentials(appName).get

    //TODO: shutdown this event-loop when client is closed.
    val eventLoop = new NioEventLoopGroup(16, new ThreadFactoryBuilder().setNameFormat(s"apns-nio-$appName-${StringUtils.generateRandomStr(4)}-%s").build())

    val metricsListener = new DropwizardApnsClientMetricsListener()
    Try_(registry.register(getMetricName(appName), metricsListener))

    val client = new ApnsClientBuilder()
      .setApnsServer(apnsHost,apnsPort)
      .setConcurrentConnections(apnsConcurrency)
        .setEventLoopGroup(eventLoop)
      .setClientCredentials(credential.getCertificateFile, credential.passkey)
      .setIdlePingInterval(pingInterval.toLong, TimeUnit.SECONDS)
      .setMetricsListener(metricsListener)
      .build()
    client
  }

  private[busybees] def cachedGateway(appName: String): Future[ApnsClient] = {
    val gatewayPromise = Promise[ApnsClient]()
    clientGatewayCache.putIfAbsent(appName, gatewayPromise.future) match {
      case null ⇒ // only one thread can get here at a time
        val gateway =
          try createAPNSClient(appName)
          catch {
            case NonFatal(e) =>
              removeClient(appName)
              gatewayPromise.failure(e)
              throw e
          }
        val fastFuture = FastFuture.successful(gateway)
        clientGatewayCache.put(appName, fastFuture) // optimize subsequent gateway accesses
        gatewayPromise.success(gateway) // satisfy everyone who got a hold of our promise while we were starting up
        fastFuture
      case future ⇒ future // return cached instance
    }
  }

  sys.addShutdownHook {
    // Disconnect all the APNSClients prior to stop
    clientGatewayCache.asScala.foreach(kv => {
      ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher stopping ${kv._1} apns-client")
      kv._2.map(_.close().awaitUninterruptibly(5000))(scala.concurrent.ExecutionContext.Implicits.global)
    })
  }


}

class APNSDispatcher(parallelism: Int)(implicit ec: ExecutionContextExecutor) {

  import com.flipkart.connekt.busybees.streams.flows.dispatchers.APNSDispatcher._


  def flow = {

    Flow[(SimpleApnsPushNotification, APNSRequestTracker)].mapAsyncUnordered(parallelism) {
      case (request, userContext) ⇒
        val result = Promise[(Try[PushNotificationResponse[SimpleApnsPushNotification]], APNSRequestTracker)]()

        val gatewayFuture = cachedGateway(userContext.appName)

        gatewayFuture
          .flatMap(client => client.sendNotification(request).asScala(responseTimeout.seconds).recoverWith {

            case timeout: TimeoutException =>
              ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher reponse didn't arrive within timeout period $responseTimeout seconds")

              /**
                * TODO: This is dangerous!
                * APNS Guidlines are stricly against this. Response Timeout should not be so small that Apple treats this as DOS
                * """
                * """ Rapid opening and closing of connections to the APNs will be deemed as a Denial-of-Service (DOS)
                * """ attack and may prevent your provider from sending push notifications to your applications.
                * """
                */
              //Disabling this now since internal pool of connections is maintained
              //APNSDispatcher.removeClient(userContext.appName)
              //ConnektLogger(LogFile.PROCESSORS).info(s"APNSDispatcher apns-client destroyed ${userContext.appName}, since response didn't arrive in $responseTimeout seconds.")
              FastFuture.failed(timeout)
          })(ec)
          .onComplete(responseTry ⇒ result.success(responseTry -> userContext))(ec)
        result.future
    }
  }

}
