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

import com.flipkart.connekt.commons.core.Wrappers.Try_
import com.flipkart.connekt.commons.dao.HbaseSinkSupport
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels._

object HbaseBulkRecorder {
  implicit def bulkRecorder(event: HbaseSinkSupport): BulkRecorder = new BulkRecorder(Seq(event))
  implicit class BulkRecorder(val events: Iterable[HbaseSinkSupport]) {
    def bulkPersist = Try_ {
      if (events.nonEmpty) {
        events.head match {
          case _:PNCallbackEvent =>
            ServiceFactory.getCallbackService.syncPersistCallbackEvents(Channel.PUSH, events.toList.map(_.asInstanceOf[PNCallbackEvent])).get
          case _:EmailCallbackEvent =>
            ServiceFactory.getCallbackService.syncPersistCallbackEvents(Channel.EMAIL, events.toList.map(_.asInstanceOf[EmailCallbackEvent])).get
          case _:SmsCallbackEvent =>
            ServiceFactory.getCallbackService.syncPersistCallbackEvents(Channel.SMS, events.toList.map(_.asInstanceOf[SmsCallbackEvent])).get
          case _:PullCallbackEvent =>
            ServiceFactory.getCallbackService.syncPersistCallbackEvents(Channel.PULL, events.toList.map(_.asInstanceOf[PullCallbackEvent])).get
          case _:WACallbackEvent =>
            ServiceFactory.getCallbackService.syncPersistCallbackEvents(Channel.WA, events.toList.map(_.asInstanceOf[WACallbackEvent])).get
          case c:ConnektRequest =>
            ServiceFactory.getMessageService(Channel.withName(c.channel)).bulkPersist(events.toList.map(_.asInstanceOf[ConnektRequest]))
        }
      }
    }
  }
}
