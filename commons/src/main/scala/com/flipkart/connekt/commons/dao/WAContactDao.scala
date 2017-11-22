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
import com.flipkart.connekt.commons.dao.HbaseDao.{RowData, longHandyFunctions, mapKVHandyFunctions}
import com.flipkart.connekt.commons.entities.WAContactEntity
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, THTableFactory}
import com.flipkart.connekt.commons.iomodels.ContactPayload
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed
import com.roundeights.hasher.Implicits.stringToHasher
import org.apache.hadoop.hbase.client.{BufferedMutator, Scan}
import org.apache.hadoop.hbase.util.Bytes
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.Try

class WAContactDao(tableName: String, hTableFactory: THTableFactory) extends Dao with HbaseDao with Instrumented {

  private val hTableConnFactory = hTableFactory
  private val hTableName = tableName
  private implicit lazy val hTableMutator: BufferedMutator = hTableFactory.getBufferedMutator(hTableName)
  private val executor = new ScheduledThreadPoolExecutor(1)
  private val columnFamily: String = "d"

  private val flusher = executor.scheduleAtFixedRate(new Runnable {
    override def run(): Unit = Try_ {
      Option(hTableMutator).foreach(_.flush())
    }
  }, 60, 60, TimeUnit.SECONDS)

  override def close(): Unit = {
    flusher.cancel(true)
    Option(hTableMutator).foreach(_.close())
  }

  private def getRowKey(destination: String) = destination.sha256.hash.hex

  @Timed("add")
  def add(contactEntity: WAContactEntity): Try[Unit] = Try_#(s"Adding WAContactEntity failed for ${contactEntity.destination}") {
    val rowKey = getRowKey(contactEntity.destination)
    val entity = mutable.Map[String, Array[Byte]](
      "destination" -> contactEntity.destination.getUtf8Bytes,
      "userName" -> contactEntity.userName.getUtf8Bytes,
      "exists" -> contactEntity.exists.getUtf8Bytes,
      "lastCheckContactTS" -> contactEntity.lastCheckContactTS.getBytes,
      "lastContacted" -> Option(contactEntity.lastContactedApp).map(_.getJson.getUtf8Bytes).orNull
    )
    val rD = Map[String, Map[String, Array[Byte]]](columnFamily -> entity.toMap)
    asyncAddRow(rowKey, rD)(hTableMutator)
    ConnektLogger(LogFile.DAO).info(s"WAEntry added for destination ${contactEntity.destination} with exists ${contactEntity.exists}")
  }

  @Timed("get")
  def get(destination: String): Try[Option[WAContactEntity]] = Try_#(s"WAContactEntity get failed for destination : $destination") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val rowKeys = getRowKey(destination)
    val rawData = fetchRow(rowKeys, List(columnFamily))
    val reqProps: Option[HbaseDao.ColumnData] = rawData.get(columnFamily)
    hTableConnFactory.releaseTableInterface(hTableInterface)
    val wE = reqProps.map(fields => {
      WAContactEntity(
        fields.get("destination").map(v => v.getString).orNull,
        fields.get("userName").map(v => v.getString).orNull,
        fields.get("exists").map(v => v.getString).orNull,
        Option(fields.get("lastContacted")).map(_.asInstanceOf[Map[String, Long]]).getOrElse(Map.empty),
        fields.getL("lastCheckContactTS").asInstanceOf[Long]
      )
    })
    wE
  }

  @Timed("gets")
  def gets(destinations: Set[String]): Try[List[WAContactEntity]] = Try_#(s"WAContactEntity gets failed for destinations : $destinations") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val rowKeys = destinations.map(getRowKey).toList
    val rawDataList = fetchMultiRows(rowKeys, List(columnFamily))
    hTableConnFactory.releaseTableInterface(hTableInterface)
    rawDataList.values.flatMap(rowData => {
      val reqProps: Option[HbaseDao.ColumnData] = rowData.get(columnFamily)
      val wE = reqProps.map(fields => {
        WAContactEntity(
          fields.get("destination").map(v => v.getString).orNull,
          fields.get("userName").map(v => v.getString).orNull,
          fields.get("exists").map(v => v.getString).orNull,
          Option(fields.get("lastContacted")).map(_.asInstanceOf[Map[String, Long]]).getOrElse(Map.empty),
          fields.getL("lastCheckContactTS").asInstanceOf[Long]
        )
      })
      wE
    }).toList
  }

  @Timed("getAll")
  def getAllContacts: Iterator[ContactPayload] = {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    try {
      val scan = new Scan()
      scan.addColumn(columnFamily.getBytes, "destination".getBytes)
      val resultScanner = hTableInterface.getScanner(scan)
      resultScanner.iterator().toIterator.map(r => ContactPayload(Bytes.toString(r.value)))
    }
  }

}

object WAContactDao {
  def apply(tableName: String, hTableFactory: THTableFactory) = new WAContactDao(tableName, hTableFactory)
}
