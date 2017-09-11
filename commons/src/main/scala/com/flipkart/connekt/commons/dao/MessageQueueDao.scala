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
package com.flipkart.connekt.commons.dao

import akka.http.scaladsl.util.FastFuture
import com.aerospike.client.Key
import com.aerospike.client.async.AsyncClient
import com.flipkart.metrics.Timed

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

sealed case class MessageMetaData(createTs: Long, expiryTs: Long, read: Option[Boolean] = None) {
  def encoded: String = {
    s"$createTs|$expiryTs" + read.map(r=> if(r) "|1" else "|0").getOrElse("")
  }
}

private object MessageMetaData {
  def apply(encoded:String): MessageMetaData = {
    val parts = encoded.split('|').map(_.toLong)
    MessageMetaData(parts(0), parts(1), parts.lift(2).map(_ == 1L))
  }
}

class MessageQueueDao(private val setName: String, private implicit val client: AsyncClient, private val rowTTL: Option[Long] = Some(15.days.toMillis)) extends Dao with AeroSpikeDao {

  private val namespace: String = "connekt"
  private val binName: String = "queue"

  @Timed("enqueueMessage")
  def enqueueMessage(appName: String, contactIdentifier: String, messageId: String, expiryTs: Long)(implicit ec: ExecutionContext): Future[Int] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    val data = Map(messageId -> MessageMetaData(System.currentTimeMillis(), expiryTs, Some(false)).encoded)
    addMapRow(key, binName, data, rowTTL).map { _record =>
      _record.getInt(binName)
    }
  }

  /**
    * markQueueMessagesAsRead
    * @return
    */
  @Timed("markAsRead")
  def markAsRead(appName: String, contactIdentifier: String, messageIds: Seq[String])(implicit ec: ExecutionContext): Future[List[String]] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    getQueue(appName, contactIdentifier).map {
      _messages => {
        val mapToUpdate = _messages.filter { case (messageId, _) => messageIds.contains(messageId) }
                                   .map { case (messageId, messageMetaData) => messageId -> messageMetaData.copy(read = Some(true)).encoded }
        addMapRow(key, binName, mapToUpdate)
        mapToUpdate.keys.toList
      }
    }
  }

  @Timed("removeMessage")
  def removeMessage(appName: String, contactIdentifier: String, messageId: String): Future[_] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    deleteMapRowItems(key, binName, List(messageId))
  }

  @Timed("empty")
  def empty(appName: String, contactIdentifier: String): Future[_] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    deleteRow(key)
  }

  @Timed("trimMessages")
  def trimMessages(appName: String, contactIdentifier: String, numToRemove: Int)(implicit ec: ExecutionContext): Future[Int] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    getQueue(appName, contactIdentifier).flatMap {
      case m if m.isEmpty => FastFuture.successful(0)
      case less if less.size <= numToRemove =>
        deleteRow(key).map(_ => 0)
      case normal =>
        val sortedMessageIds = normal.toSeq.sortBy { case (_, metadata) => metadata.createTs }
        val mIds = sortedMessageIds.take(numToRemove).map { case (messageId, _) => messageId }
        deleteMapRowItems(key, binName, mIds.toList).map(_ => normal.size - numToRemove)
    }
  }

  @Timed("getQueue")
  private def getQueue(appName: String, contactIdentifier: String)(implicit ec: ExecutionContext): Future[Map[String, MessageMetaData]] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    getRow(key).map { _record =>
      Option(_record).map { record =>
        val (valid, expired) = record.getMap(binName).asScala.toMap.asInstanceOf[Map[String, String]].map {
          case (messageId, encodedMetaData) => messageId -> MessageMetaData(encodedMetaData)
        }.partition { case (_, metadata) => metadata.expiryTs >= System.currentTimeMillis() }
        if (expired.nonEmpty)
          deleteMapRowItems(key, binName, expired.keys.toList)
        valid
      } getOrElse Map.empty[String, MessageMetaData]
    }
  }

  /**
    * getMessageIds
    * @return Ordered MessageIds sorted by Most Recency
    */
  @Timed("getMessageIds")
  def getMessageIds(appName: String, contactIdentifier: String, timestampRange: Option[(Long, Long)])(implicit ec: ExecutionContext): Future[Seq[String]] = {
    getMessages(appName, contactIdentifier, timestampRange).map(_.map(_._1))
  }

  /**
    * getMessages
    * @return Ordered Messages sorted by Most Recency
    */
  @Timed("getMessages")
  def getMessages(appName: String, contactIdentifier: String, timestampRange: Option[(Long, Long)])(implicit ec: ExecutionContext): Future[Seq[(String, MessageMetaData)]] = {
    getQueue(appName, contactIdentifier).map { _messages =>
      val messages = timestampRange match {
        case Some((fromTs, endTs)) =>
          _messages.filter { case (_, metadata) =>
            metadata.createTs >= fromTs && metadata.createTs <= endTs
          }
        case None => _messages
      }
      messages.toSeq.sortWith(_._2.createTs > _._2.createTs)
    }
  }
}
