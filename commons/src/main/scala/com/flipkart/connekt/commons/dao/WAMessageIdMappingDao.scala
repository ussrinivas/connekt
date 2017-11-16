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

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.dao.HbaseDao.{longHandyFunctions, mapKVHandyFunctions}
import com.flipkart.connekt.commons.entities.{WACheckContactEntity, WAMessageIdMappingEntity}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, THTableFactory}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed
import com.roundeights.hasher.Implicits.stringToHasher
import org.apache.hadoop.hbase.client.BufferedMutator

import scala.collection.mutable
import scala.util.Try

class WAMessageIdMappingDao(tableName: String, hTableFactory: THTableFactory) extends Dao with HbaseDao with Instrumented {
  private val hTableConnFactory = hTableFactory
  private val hTableName = tableName

  private implicit lazy val hTableMutator: BufferedMutator = hTableFactory.getBufferedMutator(hTableName)

  private val executor = new ScheduledThreadPoolExecutor(1)
  private val columnFamily: String = "d"
  private val flusher = executor.scheduleAtFixedRate(new Runnable {
    override def run(): Unit = Try_ {
      Option(hTableMutator).foreach(_.flush())
      Option(hTableMutator).foreach(_.flush())
    }
  }, 60, 60, TimeUnit.SECONDS)

  override def close(): Unit = {
    flusher.cancel(true)
    Option(hTableMutator).foreach(_.close())
  }

  private def getRowKey(waMessageId: String) = waMessageId.sha256.hash.hex


  @Timed("add")
  def add(waMessageIdMappingEntity: WAMessageIdMappingEntity): Try[Unit] = Try_#(s"Adding waMessageIdMappingEntity failed for ${waMessageIdMappingEntity.waMessageId} -> ${waMessageIdMappingEntity.connektMessageId}") {
    val rowKey = getRowKey(waMessageIdMappingEntity.waMessageId)
    val entity = mutable.Map[String, Array[Byte]](
      "waMessageId" -> waMessageIdMappingEntity.waMessageId.getUtf8Bytes,
      "connektMessageId" -> waMessageIdMappingEntity.connektMessageId.getUtf8Bytes
    )
    val rD = Map[String, Map[String, Array[Byte]]](columnFamily -> entity.toMap)
    asyncAddRow(rowKey, rD)(hTableMutator)
    ConnektLogger(LogFile.DAO).info(s"WAEntry added for waMessageId ${waMessageIdMappingEntity.waMessageId} with connektMessageId ${waMessageIdMappingEntity.connektMessageId}")
  }

  @Timed("get")
  def get(waMessageId: String): Try[Option[WAMessageIdMappingEntity]] = Try_#(s"waMessageIdMappingEntity get failed for destination : $waMessageId") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val rowKeys = getRowKey(waMessageId)
    val rawData = fetchRow(rowKeys, List(columnFamily))
    val reqProps: Option[HbaseDao.ColumnData] = rawData.get(columnFamily)
    hTableConnFactory.releaseTableInterface(hTableInterface)
    val waMessageIdMappingEntity = reqProps.map(fields => {
      WAMessageIdMappingEntity(
        fields.get("waMessageId").map(v => v.getString).orNull,
        fields.get("connektMessageId").map(v => v.getString).orNull
      )
    })
    waMessageIdMappingEntity
  }

}

object WAMessageIdMappingDao {
  def apply(tableName: String, hTableFactory: THTableFactory) =
    new WACheckContactDao(tableName, hTableFactory)
}
