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

  private def getRowKey(appName: String, destination: String) = s"${appName.toLowerCase}$destination".sha256.hash.hex

  @Timed("add")
  def add(checkContactEntity: WAContactEntity): Try[Unit] = Try_#(s"Adding WAContactEntity failed for ${checkContactEntity.destination}") {
    val rowKey = getRowKey(checkContactEntity.appName, checkContactEntity.destination)
    val entity = mutable.Map[String, Array[Byte]](
      "destination" -> checkContactEntity.destination.getUtf8Bytes,
      "userName" -> checkContactEntity.userName.getUtf8Bytes,
      "appName" -> checkContactEntity.appName.getUtf8Bytes,
      "exists" -> checkContactEntity.exists.getUtf8Bytes,
      "lastCheckContactTS" -> checkContactEntity.lastCheckContactTS.getBytes
    )
    checkContactEntity.lastContacted.foreach(entity += "lastContacted" -> _.getBytes)

    val rD = Map[String, Map[String, Array[Byte]]](columnFamily -> entity.toMap)
    asyncAddRow(rowKey, rD)(hTableMutator)
    ConnektLogger(LogFile.DAO).debug(s"WAEntry added for destination : ${checkContactEntity.destination} with exists : ${checkContactEntity.exists} and with rowkey : $rowKey")
  }

  @Timed("get")
  def get(appName: String, destination: String): Try[Option[WAContactEntity]] = Try_#(s"WACheckContactEntity get failed for destination : $destination") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val rowKeys = getRowKey(appName, destination)
    val rawData = fetchRow(rowKeys, List(columnFamily))
    val reqProps: Option[HbaseDao.ColumnData] = rawData.get(columnFamily)
    hTableConnFactory.releaseTableInterface(hTableInterface)
    reqProps.map(fields => {
      WAContactEntity(
        fields.get("destination").map(_.getString).orNull,
        fields.get("userName").map(_.getString).orNull,
        fields.get("appName").map(_.getString).orNull,
        fields.get("exists").map(_.getString).orNull,
        Option(fields.getL("lastContacted")).map(_.asInstanceOf[Long]),
        fields.getL("lastCheckContactTS").asInstanceOf[Long]
      )
    })
  }

  @Timed("gets")
  def gets(appName: String, destinations: Set[String]): Try[List[WAContactEntity]] = Try_#(s"WAContactEntity gets failed for destinations : $destinations") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val rowKeys = destinations.map(getRowKey(appName, _)).toList
    val rawDataList = fetchMultiRows(rowKeys, List(columnFamily))
    hTableConnFactory.releaseTableInterface(hTableInterface)
    rawDataList.values.flatMap(rowData => {
      val reqProps: Option[HbaseDao.ColumnData] = rowData.get(columnFamily)
      reqProps.map(fields => {
        WAContactEntity(
          fields.get("destination").map(_.getString).orNull,
          fields.get("userName").map(_.getString).orNull,
          fields.get("appName").map(_.getString).orNull,
          fields.get("exists").map(_.getString).orNull,
          Option(fields.getL("lastContacted")).map(_.asInstanceOf[Long]),
          fields.getL("lastCheckContactTS").asInstanceOf[Long]
        )
      })
    }).toList
  }

  @Timed("getAll")
  def getAllContacts: Try[Iterator[ContactPayload]] = Try_#(s"WAContactEntity getAllContacts failed") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val scan = new Scan()
    val colDestTuple = (columnFamily.getBytes, "destination".getBytes)
    val colAppTuple = (columnFamily.getBytes, "appName".getBytes)
    scan.addColumn(colDestTuple._1, colDestTuple._2)
    scan.addColumn(colAppTuple._1, colAppTuple._2)
    hTableInterface.getScanner(scan).iterator().toIterator.map(r =>
      ContactPayload(
        Bytes.toString(r.getValue(colDestTuple._1, colDestTuple._2)),
        Bytes.toString(r.getValue(colAppTuple._1, colAppTuple._2))
      )
    )
  }
}

object WAContactDao {
  def apply(tableName: String, hTableFactory: THTableFactory) = new WAContactDao(tableName, hTableFactory)
}
