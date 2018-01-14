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
import com.flipkart.connekt.commons.factories.THTableFactory
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed
import com.roundeights.hasher.Implicits.stringToHasher
import org.apache.hadoop.hbase.client.BufferedMutator

import scala.collection.mutable
import scala.util.Try

class ScheduleRequestDao(tableName: String, hTableFactory: THTableFactory) extends Dao with HbaseDao with Instrumented {

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
  def add(rowKey: String, request: String): Try[Unit] = Try_#(s"Adding ScheduleRequest failed for $rowKey") {
    val entity = mutable.Map[String, Array[Byte]]("data" -> request.getUtf8Bytes)
    val rD = Map[String, Map[String, Array[Byte]]](columnFamily -> entity.toMap)
    asyncAddRow(rowKey, rD)(hTableMutator)
  }

  @Timed("get")
  def get(rowKey: String): Try[Option[String]] = Try_#(s"ScheduleRequest get failed for rowKey : $rowKey") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val rawData = fetchRow(rowKey, List(columnFamily))
    val reqProps: Option[HbaseDao.ColumnData] = rawData.get(columnFamily)
    hTableConnFactory.releaseTableInterface(hTableInterface)
    reqProps.map(fields => {
      fields.get("data").map(_.getString).orNull
    })
  }

  @Timed("delete")
  def delete(rowKey: String) = {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    removeRow(rowKey)
    hTableConnFactory.releaseTableInterface(hTableInterface)
  }

}

object ScheduleRequestDao {
  def apply(tableName: String, hTableFactory: THTableFactory) = new ScheduleRequestDao(tableName, hTableFactory)
}
