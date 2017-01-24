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
package com.flipkart.connekt.busybees.streams.flows.transformers

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import com.flipkart.connekt.busybees.models.{EmailRequestTracker, EmailResponse}
import com.flipkart.connekt.busybees.streams.flows.MapAsyncFlowStage
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try


class EmailProviderResponseFormatter(implicit m: Materializer, ec: ExecutionContext) extends MapAsyncFlowStage[(Try[HttpResponse], EmailRequestTracker), (Try[EmailResponse], EmailRequestTracker)](96) with Instrumented {

  private lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: ((Try[HttpResponse], EmailRequestTracker)) => Future[List[(Try[EmailResponse], EmailRequestTracker)]] = responseTrackerPair => Future(profile("map") {

    val providerResponseHandlerStencil = stencilService.getStencilsByName(s"ckt-email-${responseTrackerPair._2.provider}").find(_.component.equalsIgnoreCase("parse")).get

    val emailResponse = responseTrackerPair._1.flatMap(hR => Try_{
      val httpResponse = Await.result(hR.toStrict(30.seconds), 5.seconds)

      val result = stencilService.materialize(providerResponseHandlerStencil,Map("statusCode" -> httpResponse._1.intValue(), "headers" -> httpResponse.headers, "body" -> httpResponse._3.getString).getJsonNode).asInstanceOf[EmailResponse]

      assert(result != null, "Provider Parser Failed, NULL Returned")

      result
    })

    List(emailResponse -> responseTrackerPair._2)
  })(ec)

}
