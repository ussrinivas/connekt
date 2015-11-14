package com.flipkart.connekt.receptors.directives

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.{BasicDirectives, RouteDirectives}
import com.flipkart.connekt.receptors.service.AuthenticationService

/**
 *
 *
 * @author durga.s
 * @version 11/22/15
 */
trait AuthenticationDirectives {

  def isAuthenticated(h: Option[Seq[HttpHeader]] = None): Directive0 =
  {
    val (cy, c, k): (String, String, String) = if(h.isDefined) {
      def getHeader(key: String) = h.get.find(_.name.equalsIgnoreCase(key))
        .flatMap(w => Some(w.value)).orNull

      (getHeader("x-company"), getHeader("x-clientId"), getHeader("x-api-key"))
    } else {
      (null, null, null)
    }

    isAuthenticated(cy, c, k)
  }

  def isAuthenticated(company: String, clientId: String, apiKey: String): Directive0 =
    AuthenticationService.isUserAuthenticated(company, clientId, apiKey) match {
      case true => BasicDirectives.pass
      case false => RouteDirectives.failWith(new Exception("authentication failure for company: [%s], client: [%s]"))
    }
}
