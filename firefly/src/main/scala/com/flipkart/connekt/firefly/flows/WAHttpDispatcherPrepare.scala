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
import akka.stream.scaladsl.Flow
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{ContactPayload, Payload, WAContactRequest}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.firefly.sinks.http.HttpRequestTracker

class WAHttpDispatcherPrepare {

  val flow: Flow[Seq[ContactPayload], (HttpRequest, HttpRequestTracker), NotUsed] = Flow[Seq[ContactPayload]].map { contacts =>

    ConnektLogger(LogFile.PROCESSORS).debug("WAHttpDispatcherPrepare received message: {}", supplier(contacts.getJson))

    val contactList = contacts.map(_.user_identifier).toList
    val waPayload = WAContactRequest(Payload(users = contactList))

    val requestEntity = HttpEntity(ContentTypes.`application/json`, waPayload.getJson)
    val requestHeaders = scala.collection.immutable.Seq.empty[HttpHeader]
    val httpRequest = HttpRequest(HttpMethods.POST, sendUri, requestHeaders, requestEntity)
    val requestTrace = HttpRequestTracker(httpRequest)
    httpRequest -> requestTrace
  }

  private val sendUri = Uri(s"${ConnektConfig.getString("wa.contact.send.uri.host").get}/api/check_contacts.php")
}
