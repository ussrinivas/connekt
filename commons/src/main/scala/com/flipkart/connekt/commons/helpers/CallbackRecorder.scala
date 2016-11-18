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
package com.flipkart.connekt.commons.helpers

import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels.{CallbackEvent, EmailCallbackEvent, PNCallbackEvent}
import com.flipkart.connekt.commons.metrics.Instrumented

object CallbackRecorder extends Instrumented {

  implicit def callbackRecorder(event: CallbackEvent): ListCallbackRecorder = new ListCallbackRecorder(List(event))

  implicit class ListCallbackRecorder(val events: Iterable[CallbackEvent]) {

    def persist = Try_ {

      events.foreach(e => {
        meter(s"event.${e.eventType}").mark()
      })
      if (events.nonEmpty) {
        events.head match {
          case _:PNCallbackEvent =>
            ServiceFactory.getCallbackService.persistCallbackEvents(Channel.PUSH, events.toList).get
          case _:EmailCallbackEvent =>
            ServiceFactory.getCallbackService.persistCallbackEvents(Channel.EMAIL, events.toList).get

        }

      }
    }
  }

}
