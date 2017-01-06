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
package com.flipkart.connekt.receptors.directives

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.{BasicDirectives, RouteDirectives}
import com.flipkart.connekt.commons.cache.{DistributedCacheManager, DistributedCacheType}
import com.flipkart.connekt.commons.core.Wrappers.Try_#
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed

import scala.util.{Success, Try}

case class IdempotentRequestFailedRejection(requestId: String) extends Rejection

trait IdempotentDirectives extends HeaderDirectives with Instrumented {

  val X_REQUEST_ID = "x-request-id"

  def idempotentRequest(appName: String): Directive0 = {
    BasicDirectives.extract[Seq[HttpHeader]](_.request.headers) flatMap { headers =>
      getHeader(X_REQUEST_ID, headers) match {
        case Some(reqId) if reqId.nonEmpty =>
          get(appName, reqId) match {
            case Success(isIdempotentReq) if !isIdempotentReq =>
              add(appName, reqId)
              BasicDirectives.pass
            case _ =>
              RouteDirectives.reject(IdempotentRequestFailedRejection(reqId))
          }
        case _ =>
          BasicDirectives.pass
      }
    }
  }

  @Timed("add")
  private def add(appName: String, requestId: String): Try[Boolean] = Try_#(message = "IdempotentDirectives.add Failed") {
    DistributedCacheManager.getCache(DistributedCacheType.IdempotentCheck).put[Boolean](cacheKey(appName, requestId), true)
  }

  @Timed("get")
  private def get(appName: String, requestId: String): Try[Boolean] = Try_#(message = "IdempotentDirectives.get Failed") {
    DistributedCacheManager.getCache(DistributedCacheType.IdempotentCheck).get[Boolean](cacheKey(appName, requestId)).getOrElse(false)
  }

  private def cacheKey(appName: String, requestId: String): String = appName.toLowerCase + "_" + requestId
}
