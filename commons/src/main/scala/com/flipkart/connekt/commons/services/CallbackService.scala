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
package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.dao._
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.helpers.KafkaProducerHelper
import com.flipkart.connekt.commons.iomodels.CallbackEvent
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed

import scala.util.Try

sealed case class EventsDaoContainer(pnEventsDao: PNCallbackDao, emailEventsDao: EmailCallbackDao, smsEventsDao: SmsCallbackDao, pullEventsDao: PullCallbackDao, waEventsDao: WACallbackDao) {
  def apply(channel: Channel.Value): CallbackDao = channel match {
    case Channel.PUSH => pnEventsDao
    case Channel.EMAIL => emailEventsDao
    case Channel.SMS => smsEventsDao
    case Channel.PULL => pullEventsDao
    case Channel.WA => waEventsDao
  }
}

sealed case class RequestDaoContainer(smsRequestDao: SmsRequestDao, pnRequestDao: PNRequestDao, emailRequestDao: EmailRequestDao, pullRequestDao: PullRequestDao, waRequestDao: WARequestDao) {
  def apply(channel: Channel.Value): RequestDao = channel match {
    case Channel.PUSH => pnRequestDao
    case Channel.EMAIL => emailRequestDao
    case Channel.SMS => smsRequestDao
    case Channel.PULL => pullRequestDao
    case Channel.WA => waRequestDao
  }
}

class CallbackService(eventsDao: EventsDaoContainer, requestDao: RequestDaoContainer, queueProducerHelper: KafkaProducerHelper) extends TCallbackService with Instrumented {

  private lazy val MAX_FETCH_EVENTS = ConnektConfig.get("receptors.callback.events.max-results").orElse(Some(100))
  private lazy val CALLBACK_QUEUE_NAME = ConnektConfig.get("firefly.kafka.topic").getOrElse("ckt_callback_events_%s")

  @Timed("persistCallbackEvent")
  override def persistCallbackEvents(channel: Channel.Value, events: List[CallbackEvent]): Try[List[String]] = {
    Try {
      eventsDao(channel).asyncSaveCallbackEvents(events)
    }
  }

  override def syncPersistCallbackEvents(channel: Channel.Value, events: List[CallbackEvent]): Try[List[String]] = {
    Try {
      eventsDao(channel).saveCallbackEvents(events)
    }
  }

  @Timed("enqueueCallbackEvent")
  override def enqueueCallbackEvents(events: List[CallbackEvent], queueName: String): Try[Unit] = Try_ {
    queueProducerHelper.writeMessages(queueName, events.map(event => Tuple2(event.eventId, event.getJson)): _*)
  }

  @Timed("fetchCallbackEvent")
  override def fetchCallbackEvent(requestId: String, contactId: String, channel: Channel.Value): Try[List[(CallbackEvent, Long)]] = {
    Try {
      eventsDao(channel).fetchCallbackEvents(requestId, contactId, None, MAX_FETCH_EVENTS)
    }
  }

  @Timed("fetchCallbackEventByContactId")
  def fetchCallbackEventByContactId(contactId: String, channel: Channel.Value, minTimestamp: Long, maxTimestamp: Long): Try[List[(CallbackEvent, Long)]] = {
    Try {
      eventsDao(channel).fetchCallbackEvents("", contactId, Some(Tuple2(minTimestamp, maxTimestamp)), MAX_FETCH_EVENTS)
    }
  }

  /**
    *
    * @param messageId
    * @param channel
    * @return Map ( DeviceId -> List[Events] )
    */
  @Timed("fetchCallbackEventByMId")
  def fetchCallbackEventByMId(messageId: String, channel: Channel.Value, timeStampRange: Option[(Long, Long)] = None): Try[Map[String, List[CallbackEvent]]] = {
    Try {
      requestDao(channel).fetchRequestInfo(messageId) match {
        case Some(events) =>
          eventsDao(channel).fetchCallbackEvents(messageId, events, timeStampRange)
        case None =>
          Map()
      }
    }
  }

  @Timed("deleteCallBackEvent")
  def deleteCallBackEvent(requestId: String, forContact: String, channel: Channel.Value): Try[List[CallbackEvent]] = {
    Try {
      eventsDao(channel).deleteCallbackEvents(requestId, forContact)
    }
  }

  @Timed("fetchEventsMapForContactId")
  override def fetchEventsMapForContactId(contactId: String, channel: Channel.Value, minTimestamp: Long, maxTimestamp: Long): Try[Map[String, List[CallbackEvent]]] = {
    Try {
      val eventList = fetchCallbackEventByContactId(contactId, channel, minTimestamp, maxTimestamp)
      eventsDao(channel).fetchEventMapFromList(eventList.get.map(_._1))
    }
  }

}
