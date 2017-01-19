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

import akka.http.scaladsl.model.{ContentTypes, HttpResponse}
import akka.stream.Materializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.busybees.models.{SmsRequestTracker, SmsResponse}
import com.flipkart.connekt.busybees.streams.flows.MapAsyncFlowStage
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


class SmsProviderResponseFormatter(implicit m: Materializer, ec: ExecutionContext) extends MapAsyncFlowStage[(Try[HttpResponse], SmsRequestTracker), (Try[SmsResponse], SmsRequestTracker)](96) with Instrumented {

  lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: ((Try[HttpResponse], SmsRequestTracker)) => Future[List[(Try[SmsResponse], SmsRequestTracker)]] = responseTrackerPair => Future(profile("map") {

    val providerResponseHandlerStencil = stencilService.getStencilsByName(s"ckt-sms-${responseTrackerPair._2.provider}").find(_.component.equalsIgnoreCase("parse")).get

    val smsResponse = responseTrackerPair._1.flatMap(hR => Try_ {

      val stringBody = hR.entity.getString
      val body = if (hR.entity.contentType == ContentTypes.`application/json`) {
        stringBody.getObj[ObjectNode]
      } else {
        stringBody
      }

      val result = stencilService.materialize(providerResponseHandlerStencil, Map("statusCode" -> hR._1.intValue(),
        "tracker" -> responseTrackerPair._2,
        "body" -> body).getJsonNode).asInstanceOf[SmsResponse]
      assert(result != null, "Provider Parser Failed, NULL Returned")
      result
    })

    List(smsResponse -> responseTrackerPair._2)
  })(m.executionContext)

}
