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
import com.flipkart.connekt.commons.helpers.HbaseBulkRecorder._
import com.flipkart.connekt.commons.dao.HbaseSinkSupport
import com.flipkart.connekt.commons.entities.{HbaseEventSink, Subscription, SubscriptionEvent}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.reflect.{ClassTag, _}

class HbaseSink(subscription: Subscription) {

  def sink = Sink.foreach[Seq[SubscriptionEvent]](e => {
    try {
      val hbaseEventSink = subscription.sink.asInstanceOf[HbaseEventSink]
      val tag = ClassTag(Class.forName(hbaseEventSink.entityType))
      val events = e.map(_.payload.toString.getObj(tag).asInstanceOf[HbaseSinkSupport])
      val eventKeys = events.bulkPersist
      ConnektLogger(LogFile.SERVICE).trace(s"HbaseSink event: $tag saved for events: {}", supplier(events))
      ConnektLogger(LogFile.SERVICE).info(s"HbaseSink event: $tag saved for events: ${eventKeys.get}")
    }
    catch {
      case err: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"HbaseSink failure for events: $e", err)
        throw err
    }
  })

}
