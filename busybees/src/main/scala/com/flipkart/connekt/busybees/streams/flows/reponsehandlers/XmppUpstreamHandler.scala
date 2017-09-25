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
package com.flipkart.connekt.busybees.streams.flows.reponsehandlers

import akka.stream.Materializer
import com.flipkart.connekt.commons.entities.MobilePlatform
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels.{PNCallbackEvent, XmppUpstreamResponse}
import com.flipkart.connekt.commons.metrics.Instrumented

import scala.concurrent.{ExecutionContext, Future}
import com.flipkart.connekt.commons.utils.StringUtils._

class XmppUpstreamHandler(implicit m: Materializer, ec: ExecutionContext) extends PNProviderResponseHandler[XmppUpstreamResponse](4) with Instrumented {

  override val map: (XmppUpstreamResponse) => Future[List[PNCallbackEvent]] = upstreamResponse => Future(profile("map") {
    ConnektLogger(LogFile.PROCESSORS).trace(s"XmppUpstreamHandler received xmpp upstream message : {}" , supplier(upstreamResponse))
    val events = upstreamResponse.getPnCallbackEvent().toList
    events.foreach { event =>
      ServiceFactory.getReportingService.recordPushStatsDelta(event.clientId,
        Option(event.contextId),
        None,
        Option(MobilePlatform.ANDROID.toString),
        event.appName,
        event.eventType)
    }
    events.enqueue
    events
  })(ec)
}
