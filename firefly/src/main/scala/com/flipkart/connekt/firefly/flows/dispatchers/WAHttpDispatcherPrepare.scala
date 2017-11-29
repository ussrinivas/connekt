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
package com.flipkart.connekt.firefly.flows.dispatchers

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import com.flipkart.connekt.busybees.models.WAContactTracker
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{ContactPayload, Payload, WAContactRequest}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.flows.MapFlowStage

class WAHttpDispatcherPrepare extends MapFlowStage[Seq[ContactPayload], (HttpRequest, WAContactTracker)] {

  override implicit val map: Seq[ContactPayload] => List[(HttpRequest, WAContactTracker)] = contacts => {
    try {
      ConnektLogger(LogFile.PROCESSORS).debug("WAHttpDispatcherPrepare received message: {}", supplier(contacts.getJson))

      val contactList = contacts.map(_.user_identifier).toSet
      val waPayload = WAContactRequest(Payload(users = contactList))

      val requestEntity = HttpEntity(ContentTypes.`application/json`, waPayload.getJson)
      val requestHeaders = scala.collection.immutable.Seq.empty[HttpHeader]
      val httpRequest = HttpRequest(HttpMethods.POST, sendUri, requestHeaders, requestEntity)
      val requestTrace = WAContactTracker(contactList, contacts.head.appName, contacts)
      List(httpRequest -> requestTrace)
    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"WAHttpDispatcherPrepare failed with ${e.getMessage}", e)
        List.empty
    }
  }

  private val sendUri = Uri(s"${ConnektConfig.getString("wa.base.uri").get}/api/check_contacts.php")
}

class DemoHttpDispatcherPrepare extends MapFlowStage[Int, (HttpRequest, Int)] {

  override implicit val map: Int => List[(HttpRequest, Int)] = input => {
    try {
      val httpRequest = HttpRequest(HttpMethods.GET, sendUri).withHeaders(RawHeader("x-api-key","connekt-insomnia"))
      List(httpRequest -> input)
    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"WAHttpDispatcherPrepare failed with ${e.getMessage}", e)
        List.empty
    }
  }

  private val sendUri = Uri("http://localhost:28000/v1/whatsapp/checkcontact/flipkart/123412")
}
