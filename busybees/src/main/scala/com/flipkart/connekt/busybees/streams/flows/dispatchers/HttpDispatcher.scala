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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream._
import com.flipkart.connekt.busybees.models._
import com.flipkart.connekt.busybees.streams.flows.dispatchers.FlowRetry.{RetrySupportTracker, _}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.typesafe.config.Config

import scala.concurrent.ExecutionContextExecutor

class HttpDispatcher(actorSystemConf: Config) {

  implicit val httpSystem: ActorSystem = ActorSystem("http-out", actorSystemConf)
  implicit val httpMat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = httpSystem.dispatcher
  private val maxRetryCount = ConnektConfig.getInt("sys.http.retry.max").getOrElse(2)

  private val gcmPoolClientFlow = Http().cachedHostConnectionPoolHttps[RetrySupportTracker[HttpRequest,GCMRequestTracker]]("fcm.googleapis.com",443)(httpMat).withRetry(maxRetryCount)

  private val wnsPoolClientFlow = Http().superPool[RetrySupportTracker[HttpRequest,WNSRequestTracker]]()(httpMat).withRetry(maxRetryCount)

  private val openWebPoolClientFlow = Http().superPool[RetrySupportTracker[HttpRequest,OpenWebRequestTracker]]()(httpMat).withRetry(maxRetryCount)

  private val smsPoolClientFlow = Http().superPool[RetrySupportTracker[HttpRequest,SmsRequestTracker]]()(httpMat).withRetry(maxRetryCount)

  private val emailPoolClientFlow = Http().superPool[RetrySupportTracker[HttpRequest,EmailRequestTracker]]()(httpMat).withRetry(maxRetryCount)

}

object HttpDispatcher {

  private var instance: Option[HttpDispatcher] = None

  def init(actorSystemConf: Config) = {
    if(instance.isEmpty) {
      ConnektLogger(LogFile.SERVICE).info(s"Creating HttpDispatcher actor-system with conf: ${actorSystemConf.toString}")
      instance = Some(new HttpDispatcher(actorSystemConf))
    }
  }

  def gcmPoolClientFlow = instance.map(_.gcmPoolClientFlow).get

  def smsPoolClientFlow = instance.map(_.smsPoolClientFlow).get

  def wnsPoolClientFlow = instance.map(_.wnsPoolClientFlow).get

  def openWebPoolClientFlow =  instance.map(_.openWebPoolClientFlow).get

  def emailPoolClientFlow =  instance.map(_.emailPoolClientFlow).get


}
