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
package com.flipkart.connekt.firefly.flows.responsehandlers

import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.flipkart.connekt.busybees.models.WAContactTracker
import com.flipkart.connekt.commons.entities.WAContactEntity
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.{BigfootService, WAContactService}
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WAContactResponseHandler(implicit m: Materializer, ec: ExecutionContext) extends WAProviderResponseHandler[(Try[HttpResponse], WAContactTracker)](96) with Instrumented {

  val waContactService = WAContactService()

  override implicit val map: ((Try[HttpResponse], WAContactTracker)) => Future[List[Nothing]] = responseTrackerPair => Future(profile("map") {

    val httpResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2

    httpResponse match {
      case Success(r) =>
        try {
          //          TODO: Added metering for errors
          val response = r.entity.getString.getObj[WAResponse]
          val results = response.payload.results
          ConnektLogger(LogFile.PROCESSORS).debug(s"WAResponseHandler received http response for: $results")
          r.status.intValue() match {
            case 200 if response.error.equalsIgnoreCase("false") =>
              results.map(result => {
                val waContactEntity = WAContactEntity(result.input_number, result.wa_username, requestTracker.appName, result.wa_exists, None)
                waContactService.add(waContactEntity)
                BigfootService.ingestEntity(result.wa_username, waContactEntity.toPublishFormat, waContactEntity.namespace).get
              })
              ConnektLogger(LogFile.PROCESSORS).trace(s"WAResponseHandler contacts updated in hbase : $results")
            case w =>
              ConnektLogger(LogFile.PROCESSORS).error(s"WAResponseHandler received http response : ${response.getJson} , with status code $w and tracker ${responseTrackerPair.getJson}")
          }
        } catch {
          case e: Exception =>
            ConnektLogger(LogFile.PROCESSORS).error(s"WAResponseHandler failed processing http response body for: $r", e)
        }
      case Failure(e2) =>
        ConnektLogger(LogFile.PROCESSORS).error(s"WAResponseHandler send failure for: $requestTracker", e2)
    }
    List.empty[Nothing]
  })(m.executionContext)
}
