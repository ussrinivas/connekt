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
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


class EmailProviderResponseFormatter(parallelism: Int)(implicit m: Materializer, ec: ExecutionContext) extends MapAsyncFlowStage[(Try[HttpResponse], EmailRequestTracker), (Try[EmailResponse], EmailRequestTracker)](parallelism) {

  private lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: ((Try[HttpResponse], EmailRequestTracker)) => Future[List[(Try[EmailResponse], EmailRequestTracker)]] = responseTrackerPair => Future(profile("map") {

    val providerResponseHandlerStencil = stencilService.getStencilsByName(s"ckt-email-${responseTrackerPair._2.provider}").find(_.component.equalsIgnoreCase("parse")).get
    ConnektLogger(LogFile.PROCESSORS).trace(s"EmailProviderResponseFormatter received message: ${responseTrackerPair._2.messageId}")

    val emailResponse = responseTrackerPair._1.flatMap(httpResponse => Try_ {
      val result = stencilService.materialize(providerResponseHandlerStencil, Map("statusCode" -> httpResponse.status.intValue(), "headers" -> httpResponse.headers, "body" -> httpResponse.entity.getString).getJsonNode).asInstanceOf[EmailResponse]
      assert(result != null, "Provider Parser Failed, NULL Returned")
      result
    })

    List(emailResponse -> responseTrackerPair._2)
  })(ec)

}
