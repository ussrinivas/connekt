package com.flipkart.connekt.receptors.directives

import akka.http.scaladsl.server.{Directive, Directive1}
import akka.http.scaladsl.util.FastFuture._

import scala.concurrent.Future
import scala.util.Try

/**
 *
 *
 * @author durga.s
 * @version 11/23/15
 */
trait AsyncDirectives {

  def async[T](taskBlock: => T): Directive1[Try[T]] = {
    Directive { inner ⇒ ctx ⇒
      import ctx.executionContext
      val f = Future[T](taskBlock)
      f.fast.transformWith(t ⇒ inner(Tuple1(t))(ctx))
    }
  }
}
