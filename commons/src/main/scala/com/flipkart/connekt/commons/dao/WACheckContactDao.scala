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
import com.flipkart.connekt.commons.entities.WACheckContactEntity
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, THTableFactory}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed
import com.roundeights.hasher.Implicits.stringToHasher
import org.apache.hadoop.hbase.client.BufferedMutator

import scala.collection.mutable
import scala.util.Try

class WACheckContactDao(tableName: String, hTableFactory: THTableFactory) extends Dao with HbaseDao with Instrumented {
  private val hTableConnFactory = hTableFactory
  private val hTableName = tableName

  private implicit lazy val hTableMutator: BufferedMutator = hTableFactory.getBufferedMutator(hTableName)

  private val executor = new ScheduledThreadPoolExecutor(1)
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

  private def getRowKey(destination: String) = destination.sha256.hash.hex

  val columnFamily: String = "d"

  @Timed("add")
  def add(checkContactEntity: WACheckContactEntity): Try[Unit] = Try_#(s"Adding WACheckContactEntity failed for ${checkContactEntity.destination}") {
    val rowKey = getRowKey(checkContactEntity.destination)
    val entity = mutable.Map[String, Array[Byte]](
      "destination" -> checkContactEntity.destination.getUtf8Bytes,
      "waUserName" -> checkContactEntity.waUserName.getUtf8Bytes,
      "waExists" -> checkContactEntity.waExists.getUtf8Bytes,
      "waLastCheckContactTS" -> checkContactEntity.waLastCheckContactTS.getBytes
    )
    checkContactEntity.lastContacted.foreach(entity += "lastContacted" -> _.getBytes)
    val rD = Map[String, Map[String, Array[Byte]]](columnFamily -> entity.toMap)
    asyncAddRow(rowKey, rD)(hTableMutator)
    ConnektLogger(LogFile.DAO).info(s"WAEntry added for destination ${checkContactEntity.destination} with waExists ${checkContactEntity.waExists}")
  }

  @Timed("get")
  def get(destination: String): Try[Option[WACheckContactEntity]] = Try_#(s"WACheckContactEntity get failed for destination : $destination") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val rowKeys = getRowKey(destination)
    val rawData = fetchRow(rowKeys, List(columnFamily))
    val reqProps: Option[HbaseDao.ColumnData] = rawData.get(columnFamily)
    hTableConnFactory.releaseTableInterface(hTableInterface)
    val wE = reqProps.map(fields => {
      WACheckContactEntity(
        fields.get("destination").map(v => v.getString).orNull,
        fields.get("waUserName").map(v => v.getString).orNull,
        fields.get("waExists").map(v => v.getString).orNull,
        Option(fields.getL("lastContacted")).map(_.asInstanceOf[Long]),
        fields.getL("waLastCheckContactTS").asInstanceOf[Long]
      )
    })
    wE
  }

  @Timed("gets")
  def gets(destinations: Set[String]): Try[List[WACheckContactEntity]] = Try_#(s"WACheckContactEntity gets failed for destinations : $destinations") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val rowKeys = destinations.map(getRowKey).toList
    val rawDataList = fetchMultiRows(rowKeys, List(columnFamily))
    hTableConnFactory.releaseTableInterface(hTableInterface)
    rawDataList.values.flatMap(rowData => {
      val reqProps: Option[HbaseDao.ColumnData] = rowData.get(columnFamily)
      val wE = reqProps.map(fields => {
        WACheckContactEntity(
          fields.get("destination").map(v => v.getString).orNull,
          fields.get("waUserName").map(v => v.getString).orNull,
          fields.get("waExists").map(v => v.getString).orNull,
          Option(fields.getL("lastContacted")).map(_.asInstanceOf[Long]),
          fields.getL("waLastCheckContactTS").asInstanceOf[Long]
        )
      })
      wE
    }).toList
  }
}

object WACheckContactDao {
  def apply(tableName: String, hTableFactory: THTableFactory) = new WACheckContactDao(tableName, hTableFactory)
}
