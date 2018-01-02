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
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig

object CallbackRecorder extends Instrumented {

  private lazy val CALLBACK_QUEUE_NAME = ConnektConfig.get("firefly.kafka.topic").getOrElse("ckt_callback_events_%s")

  implicit def callbackRecorder(event: CallbackEvent): ListCallbackRecorder = new ListCallbackRecorder(List(event))

  implicit class ListCallbackRecorder(val events: Iterable[CallbackEvent]) {

    def persist = Try_ {
      enqueue.get
      events.foreach(e => {
        meter(s"event.${e.eventType}").mark()
      })
      if (events.nonEmpty) {
        events.head match {
          case _:PNCallbackEvent =>
            ServiceFactory.getCallbackService.persistCallbackEvents(Channel.PUSH, events.toList).get
          case _:EmailCallbackEvent =>
            ServiceFactory.getCallbackService.persistCallbackEvents(Channel.EMAIL, events.toList).get
          case _:SmsCallbackEvent =>
            ServiceFactory.getCallbackService.persistCallbackEvents(Channel.SMS, events.toList).get
          case _:PullCallbackEvent =>
            ServiceFactory.getCallbackService.persistCallbackEvents(Channel.PULL, events.toList).get
          case _:InboundMessageCallbackEvent =>

        }
      }
    }

    def enqueue = Try_ {
      events.foreach(e => {
        meter(s"event.${e.eventType}.enqueue").mark()
      })
      if (events.nonEmpty) {
        events.head match {
          case _:PNCallbackEvent =>
            ServiceFactory.getCallbackService.enqueueCallbackEvents( events.toList, CALLBACK_QUEUE_NAME.format(Channel.PUSH.toString.toLowerCase)).get
          case _:EmailCallbackEvent =>
            ServiceFactory.getCallbackService.enqueueCallbackEvents( events.toList, CALLBACK_QUEUE_NAME.format(Channel.EMAIL.toString.toLowerCase)).get
          case _:SmsCallbackEvent =>
            ServiceFactory.getCallbackService.enqueueCallbackEvents( events.toList, CALLBACK_QUEUE_NAME.format(Channel.SMS.toString.toLowerCase)).get
          case _:PullCallbackEvent =>
            ServiceFactory.getCallbackService.enqueueCallbackEvents( events.toList, CALLBACK_QUEUE_NAME.format(Channel.PULL.toString.toLowerCase)).get
          case _:WACallbackEvent =>
            ServiceFactory.getCallbackService.enqueueCallbackEvents( events.toList, CALLBACK_QUEUE_NAME.format(Channel.WA.toString.toLowerCase)).get
          case _:InboundMessageCallbackEvent =>
            ServiceFactory.getCallbackService.enqueueCallbackEvents(events.toList, ConnektConfig.get("inbound.messages.topic").getOrElse("inbound_messages"))
        }

      }
    }
  }

}
