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

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MessageQueueDao(private val setName: String, private implicit val client: AsyncClient) extends Dao with AeroSpikeDao {

  private val namespace: String = "connekt"
  private val binName: String = "queue"
  private val rowTTL = Some(15.days.toMillis)

  def enqueueMessage(appName: String, contactIdentifier: String, messageId: String, expiryTs: Long)(implicit ec: ExecutionContext): Future[Int] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    val data = Map(messageId -> s"${System.currentTimeMillis()}|$expiryTs")
    addMapRow(key, binName, data, rowTTL).map { _record =>
      _record.getInt(binName)
    }
  }

  def removeMessage(appName: String, contactIdentifier: String, messageId: String): Future[_] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    deleteMapRowItems(key, binName, List(messageId))
  }

  def empty(appName: String, contactIdentifier: String): Future[_] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    deleteRow(key)
  }

  def trimMessages(appName: String, contactIdentifier: String, numToRemove: Int)(implicit ec: ExecutionContext): Future[Int] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    getQueue(appName, contactIdentifier).flatMap {
      case m if m.isEmpty => FastFuture.successful(0)
      case less if less.size <= numToRemove =>
        deleteMapRowItems(key, binName, less.keys.toList).map(_ => 0)
      case normal =>
        val sortedMessageIds = normal.toSeq.sortBy { case (_, data) => data.split('|').head.toLong }
        val mIds = sortedMessageIds.take(numToRemove).map { case (messageId, _) => messageId }
        deleteMapRowItems(key, binName, mIds.toList).map(_ => normal.size - numToRemove)
    }
  }

  private def getQueue(appName: String, contactIdentifier: String)(implicit ec: ExecutionContext): Future[Map[String, String]] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    getRow(key).map { _record =>
      Option(_record).map { record =>
        val (valid, expired) = record.getMap(binName).asScala.toMap.asInstanceOf[Map[String,String]].partition { case (_, data) =>
          data.split('|').last.toLong >= System.currentTimeMillis()
        }
        if (expired.nonEmpty)
          deleteMapRowItems(key, binName, expired.keys.toList)
        valid
      } getOrElse Map.empty[String, String]
    }
  }

  /**
    * getMessages
    * @return Ordered MessageIds sorted by Most Recency
    */
  def getMessages(appName: String, contactIdentifier: String, timestampRange: Option[(Long, Long)])(implicit ec: ExecutionContext): Future[Seq[String]] = {
    getQueue(appName, contactIdentifier).map { _messages =>
      val messages = timestampRange match {
        case Some((fromTs, endTs)) =>
          _messages.filter { case (_, data) =>
            val createTs = data.split('|').head.toLong
            createTs >= fromTs && createTs <= endTs
          }
        case None => _messages
      }
      messages.toSeq.sortWith(_._2.split('|').head.toLong > _._2.split('|').head.toLong).map(_._1)
    }
  }

}
