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
package com.flipkart.connekt.firefly.sinks.hbase

import akka.stream.scaladsl.Sink
import com.flipkart.connekt.commons.entities.{Channel, SubscriptionEvent}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils._

class HbaseSink {

  //TODO: Temp hack, clean this shit.
  def sink = Sink.foreach[SubscriptionEvent](e => {
    val event = e.payload.toString.getObj[CallbackEvent]
    event match {
      case _:PNCallbackEvent =>
        ServiceFactory.getCallbackService.persistCallbackEvents(Channel.PUSH, List(event)).get
      case _:EmailCallbackEvent =>
        ServiceFactory.getCallbackService.persistCallbackEvents(Channel.EMAIL, List(event)).get
      case _:SmsCallbackEvent =>
        ServiceFactory.getCallbackService.persistCallbackEvents(Channel.SMS, List(event)).get
      case _:PullCallbackEvent =>
        ServiceFactory.getCallbackService.persistCallbackEvents(Channel.PULL,List(event)).get
      case _ =>
    }
    ConnektLogger(LogFile.SERVICE).trace(s"HbaseSink event saved {}", supplier(event))
  })

}
