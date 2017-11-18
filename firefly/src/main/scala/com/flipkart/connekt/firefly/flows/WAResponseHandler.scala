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
package com.flipkart.connekt.firefly.flows

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.entities.WACheckContactEntity
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.models.WACheckContactResponse
import com.flipkart.connekt.firefly.sinks.http.HttpRequestTracker

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class WAResponseHandler(implicit m: Materializer, ec: ExecutionContext) {

  private lazy val dao = DaoFactory.getWACheckContactDao

  val flow: Flow[(Try[HttpResponse], HttpRequestTracker), NotUsed, NotUsed] = Flow[(Try[HttpResponse], HttpRequestTracker)].map { responseTrackerPair =>

    val httpResponse = responseTrackerPair._1
    val requestTracker = responseTrackerPair._2

    httpResponse match {
      case Success(r) =>
        try {
          val response = r.entity.getString(m).getObj[WACheckContactResponse]
          val results = response.payload.results
          ConnektLogger(LogFile.PROCESSORS).debug(s"WAResponseHandler received http response for: $results")
          ConnektLogger(LogFile.PROCESSORS).trace(s"WAResponseHandler received http response for: $results")
          r.status.intValue() match {
            case 200 if response.error.equalsIgnoreCase("false") =>
              results.map(result => {
                dao.add(WACheckContactEntity(result.input_number, result.wa_username, result.wa_exists, None))
              })
              ConnektLogger(LogFile.PROCESSORS).trace(s"WAResponseHandler contacts updated in hbase : $results")
            case w =>
              ConnektLogger(LogFile.PROCESSORS).error(s"WAResponseHandler received http response for: $results , with status code $w")
          }
        } catch {
          case e: Exception =>
            ConnektLogger(LogFile.PROCESSORS).error(s"WAResponseHandler failed processing http response body for: $r", e)
        }
      case Failure(e2) =>
        ConnektLogger(LogFile.PROCESSORS).error(s"GCMResponseHandler gcm send failure for: ${requestTracker.httpRequest}", e2)
    }
    NotUsed
  }
}
