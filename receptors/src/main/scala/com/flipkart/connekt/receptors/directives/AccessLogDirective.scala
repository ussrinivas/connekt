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

import akka.http.scaladsl.model.{HttpRequest, RemoteAddress}
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.{BasicDirectives, MiscDirectives}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}

trait AccessLogDirective extends BasicDirectives with MiscDirectives {

  private val logFormat = "%s %s %s %s %s"

  // logs just the request method and response status at info level
  private def logRequestAndResponse(req: HttpRequest, remoteAddress: RemoteAddress): Any => Unit = {
    case (routeResult: Complete, time: Long) =>
      val remoteIp = remoteAddress.toOption.map(_.getHostAddress).getOrElse("0.0.0.0")
      ConnektLogger(LogFile.ACCESS).info(logFormat.format(remoteIp, req.method.value, req.uri, routeResult.response.status.intValue(), time))
    case _ =>
  }

  /**
    * Produces a log entry for every incoming request and [[RouteResult]].
    */
  def logTimedRequestResult: Directive0 =
    extractRequestContext.flatMap { ctx ⇒
      val startTs = System.currentTimeMillis
      extractClientIP.flatMap { address ⇒
        mapRouteResult { result ⇒
          val timeTaken = System.currentTimeMillis - startTs
          logRequestAndResponse(ctx.request, address)((result, timeTaken))
          result
        }
      }
    }


}
