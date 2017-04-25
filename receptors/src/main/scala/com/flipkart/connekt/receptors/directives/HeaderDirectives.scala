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
import akka.http.scaladsl.server.directives.BasicDirectives
import com.flipkart.connekt.commons.utils.StringUtils.StringOptionHandyFunctions
import scala.util.Try

trait HeaderDirectives {

  def getHeader(key: String, h: Seq[HttpHeader]): Option[String] = h.find(_.is(key.toLowerCase)).flatMap(w => Option(w.value()))

  def sniffHeaders: Directive1[Seq[HttpHeader]] = BasicDirectives.extract[Seq[HttpHeader]](_.request.headers)

  def extractUserAgent: Directive1[String] = BasicDirectives.extract[String](_.request.headers.find(_.lowercaseName().equalsIgnoreCase("user-agent")).map(_.value()).orEmpty)

  def sniffXHeaders = BasicDirectives.extract[Seq[HttpHeader]](_.request.headers.filter(_.lowercaseName().startsWith("x-")))

  def getXHeaders = sniffXHeaders.map(httpHeaders => httpHeaders.map(h => h.lowercaseName() -> h.value()).toMap.filterKeys(!List("x-api-key").contains(_)))

  def extractTestRequestContext: Directive1[Boolean] = {
    Directives.optionalHeaderValueByName("x-perf-test").map { header ⇒
      header.exists(h => Try(h.trim.toBoolean).getOrElse(false))
    }
  }
}
