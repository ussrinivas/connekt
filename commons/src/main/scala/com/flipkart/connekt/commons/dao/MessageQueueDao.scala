package com.flipkart.connekt.commons.dao

import com.aerospike.client.Key
import com.aerospike.client.async.AsyncClient

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class MessageQueueDao(private val setName: String, private implicit val client: AsyncClient) extends Dao with AeroSpikeDao {

  private val namespace: String = "connekt"
  private val binName:String = "queue"

  def enqueueMessage(appName: String, contactIdentifier: String, messageId: String, ttl: Long): Future[_] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    val data = Map(messageId -> s"${System.currentTimeMillis()}|$ttl" )
    addMapRow(key, binName, data )
  }

  def removeMessage(appName: String, contactIdentifier: String, messageId: String): Future[_] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    deleteMapRowItems(key, binName, List(messageId))
  }

  def getMessages(appName: String, contactIdentifier: String, timestampRange: Option[(Long, Long)])(implicit ec: ExecutionContext): Future[List[String]] = {
    val key = new Key(namespace, setName, s"$appName$contactIdentifier")
    getRow(key).map { _record =>
      Option(_record).map { record =>
        val (valid, expired) = record.getMap(binName).asScala.partition { case (_ , data) =>
          data.toString.split('|').last.toLong >= System.currentTimeMillis()
        }

        if(expired.nonEmpty)
          deleteMapRowItems(key,binName,expired.keys.map(_.toString).toList )

        if(timestampRange.isDefined){
          val timeFiltered = valid.filter { case (_ , data ) =>
            val ts =  data.toString.split('|').head.toLong
            ts >= timestampRange.get._1 && ts <= timestampRange.get._2
          }
          timeFiltered.keys.map(_.toString).toList
        } else
          valid.keys.map(_.toString).toList
      } getOrElse List.empty
    }
  }

}
