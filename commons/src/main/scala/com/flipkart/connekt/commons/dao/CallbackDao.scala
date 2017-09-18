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

import java.io.IOException
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import com.flipkart.connekt.commons.core.Wrappers.Try_
import com.flipkart.connekt.commons.dao.HbaseDao.ColumnData
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, THTableFactory}
import com.flipkart.connekt.commons.iomodels.{CallbackEvent, ChannelRequestInfo}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils.JSONMarshallFunctions
import com.flipkart.metrics.Timed
import com.roundeights.hasher.Implicits._
import org.apache.hadoop.hbase.client.BufferedMutator
import com.flipkart.connekt.commons.utils.StringUtils._

abstract class CallbackDao(tableName: String, hTableFactory: THTableFactory) extends TCallbackDao with HbaseDao with Instrumented {
  private val hTableConnFactory = hTableFactory
  private val hTableName = tableName
  private implicit lazy val hTableMutator: BufferedMutator = hTableFactory.getBufferedMutator(tableName)

  val columnFamily: String = "e"

  def channelEventPropsMap(channelCallbackEvent: CallbackEvent): ColumnData

  def mapToChannelEvent(channelEventPropsMap: Map[String, Array[Byte]]): CallbackEvent


  private val executor = new ScheduledThreadPoolExecutor(1)
  private val flusher = executor.scheduleAtFixedRate(new Runnable {
    override def run(): Unit = Try_ {
      Option(hTableMutator).foreach(_.flush())
    }
  }, 60, 60, TimeUnit.SECONDS)


  override def close(): Unit = {
    flusher.cancel(true)
    Option(hTableMutator).foreach(_.close())
  }

  @Timed("asyncSaveCallbackEvents")
  override def asyncSaveCallbackEvents(events: List[CallbackEvent]): List[String] = {
    val rowKeys = events.map(e => {
      val rowKey = s"${e.contactId.sha256.hash.hex}:${e.messageId}:${e.eventId}"
      try {
        val channelEventProps = channelEventPropsMap(e)
        val rawData = Map[String, ColumnData](columnFamily -> channelEventProps)
        asyncAddRow(rowKey, rawData)
      } catch {
        case e: IOException =>
          ConnektLogger(LogFile.DAO).error(s"Events details async-persistence failed for ${e.getJson} ${e.getMessage}", e)
      }
      rowKey
    })
    ConnektLogger(LogFile.DAO).debug(s"Events details async-persisted with rowkeys {}", supplier(rowKeys.mkString(",")))
    rowKeys
  }

  @Timed("saveCallbackEvents")
  override def saveCallbackEvents(events: List[CallbackEvent]): List[String] = {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    try {
      val rowKeys = events.map(e => {
        val channelEventProps = channelEventPropsMap(e)
        val rawData = Map[String, ColumnData](columnFamily -> channelEventProps)
        val rowKey = s"${e.contactId.sha256.hash.hex}:${e.messageId}:${e.eventId}"
        addRow(rowKey, rawData)
        e.messageId
      })
      ConnektLogger(LogFile.DAO).debug(s"Events details persisted with rowkeys {}", supplier(rowKeys.mkString(",")))
      rowKeys
    } catch {
      case e: IOException =>
        ConnektLogger(LogFile.DAO).error(s"Events details persistence failed for ${events.getJson} ${e.getMessage}", e)
        throw new IOException("Events details persistence failed for %s".format(events.getJson), e)
    } finally {
      hTableConnFactory.releaseTableInterface(hTableInterface)
    }
  }

  @Timed("fetchCallbackEvents")
  def fetchCallbackEvents(requestId: String, contactId: String, timestampRange: Option[(Long, Long)], maxRowsLimit: Option[Int]): List[(CallbackEvent, Long)] = {
    val colFamiliesReqd = List(columnFamily)
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    try {
      val rawDataList = fetchRows(s"${contactId.sha256.hash.hex}:$requestId", s"${contactId.sha256.hash.hex}:$requestId{", colFamiliesReqd, timestampRange, maxRowsLimit)
      rawDataList.values.flatMap(rowData => {
        val eventProps = rowData.data.get(columnFamily)
        val event = eventProps.map(mapToChannelEvent)
        event.map((_, rowData.timestamp))
      }).toList

    } catch {
      case e: IOException =>
        ConnektLogger(LogFile.DAO).error(s"Fetching events trail failed for $requestId _ $contactId, ${e.getMessage}", e)
        throw new IOException(s"Fetching events trail failed for $requestId _ $contactId", e)
    } finally {
      hTableConnFactory.releaseTableInterface(hTableInterface)
    }
  }

  @Timed("deleteCallbackEvents")
  def deleteCallbackEvents(requestId: String, forContact: String): List[CallbackEvent] = {
    val colFamiliesReqd = List(columnFamily)
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    try {
      val rows = fetchRows(s"${forContact.sha256.hash.hex}:$requestId", s"${forContact.sha256.hash.hex}:$requestId{", colFamiliesReqd)
      ConnektLogger(LogFile.DAO).info(s"Deleting events for $forContact:$requestId")
      rows.keySet.foreach(removeRow)
      rows.values.flatMap(rowData => {
        val eventProps = rowData.data.get(columnFamily)
        eventProps.map(mapToChannelEvent)
      }).toList
    } catch {
      case e: IOException =>
        ConnektLogger(LogFile.DAO).error(s"Delete events failed for $requestId _ $forContact, ${e.getMessage}", e)
        throw new IOException(s"Delete events failed for $requestId _ $forContact", e)
    } finally {
      hTableConnFactory.releaseTableInterface(hTableInterface)
    }
  }

  def fetchCallbackEvents(requestId: String, event: ChannelRequestInfo, fetchRange: Option[(Long, Long)]): Map[String, List[CallbackEvent]]

  def fetchEventMapFromList(event: List[CallbackEvent]): Map[String, List[CallbackEvent]]

}
