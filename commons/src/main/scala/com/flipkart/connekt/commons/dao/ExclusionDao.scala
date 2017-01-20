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

import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.dao.HbaseDao.durationFunctions
import com.flipkart.connekt.commons.entities.ExclusionType.ExclusionType
import com.flipkart.connekt.commons.entities.{ExclusionDetails, ExclusionEntity, ExclusionType}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, THTableFactory}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed
import com.roundeights.hasher.Implicits.stringToHasher

import scala.collection.mutable
import scala.util.Try

class ExclusionDao(tableName: String, hTableFactory: THTableFactory) extends Dao with HbaseDao with Instrumented {
  private val hTableConnFactory = hTableFactory
  private val hTableName = tableName
  val hIndexTableName = tableName + "-index"

  private def getRowKeyIndexed(channel: String, appName: String, destination: String) = destination.sha256.hash.hex + "_" + channel + "_" + appName.toLowerCase

  private def getRowKeyPrefix(channel: String, appName: String, exclusionType: ExclusionType.ExclusionType) = channel + "_" + appName.toLowerCase + "_" + exclusionType
  private def getRowKey(channel: String, appName: String, destination: String, exclusionType: ExclusionType.ExclusionType) = getRowKeyPrefix(channel,appName, exclusionType) + "_" + destination

  val columnFamily: String = "e"

  @Timed("add")
  def add(exclusionEntity: ExclusionEntity): Try[Unit] = Try_#(s"Adding ExclusionDao failed for ${exclusionEntity.destination}") {
    implicit val hIndexTableInterface = hTableConnFactory.getTableInterface(hIndexTableName)
    val hTableInterface = hTableConnFactory.getTableInterface(hTableName)

    val suppressionEntity = mutable.Map[String, Array[Byte]](
      "exclusionType" -> exclusionEntity.exclusionDetails.exclusionType.toString.getUtf8Bytes
    )
    val rawData = Map[String, Map[String, Array[Byte]]](columnFamily -> suppressionEntity.toMap)
    val indexedRowKey = getRowKeyIndexed(exclusionEntity.channel, exclusionEntity.appName, exclusionEntity.destination)
    val ttl = exclusionEntity.exclusionDetails.ttl.toTTL
    addRow(indexedRowKey, rawData,ttl)

    // Adding exclusionType in rowKey.
    val rowKey = getRowKey(exclusionEntity.channel, exclusionEntity.appName, exclusionEntity.destination, exclusionEntity.exclusionDetails.exclusionType)
    val suppressionEntityWithExType = mutable.Map[String, Array[Byte]](
      "channel" -> exclusionEntity.channel.getUtf8Bytes,
      "appName" -> exclusionEntity.appName.getUtf8Bytes,
      "exclusionType" -> exclusionEntity.exclusionDetails.exclusionType.toString.getUtf8Bytes,
      "destination" -> exclusionEntity.destination.getUtf8Bytes,
      "metaInfo" -> exclusionEntity.exclusionDetails.metaInfo.toString.getUtf8Bytes
    )

    val rD = Map[String, Map[String, Array[Byte]]](columnFamily -> suppressionEntityWithExType.toMap)
    addRow(rowKey, rD, ttl)(hTableInterface)

    hTableConnFactory.releaseTableInterface(hIndexTableInterface)
    hTableConnFactory.releaseTableInterface(hTableInterface)
    ConnektLogger(LogFile.DAO).info(s"Entry added for id ${exclusionEntity.destination} with exclusionType ${exclusionEntity.exclusionDetails.exclusionType}")
  }

  @Timed("delete")
  def delete(channel: String, appName: String, destination: String): Try[Unit] = Try_#(s"Deleting ExclusionDao failed for $destination") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    removeRow(getRowKeyIndexed(channel, appName, destination))
    ExclusionType.values.foreach { eT =>
      removeRow(getRowKey(channel, appName, destination, eT))
    }
    hTableConnFactory.releaseTableInterface(hTableInterface)
    ConnektLogger(LogFile.DAO).info(s"Entry deleted for id $destination")
  }

  @Timed("lookup")
  def lookup(channel: String, appName: String, destination: String): Try[Option[ExclusionDetails]] = Try_#(s"Fetching ExclusionEntity failed for $destination") {
    implicit val hIndexTableInterface = hTableFactory.getTableInterface(hIndexTableName)
    val id = getRowKeyIndexed(channel, appName, destination)
    val rawData = fetchRow(id, List(columnFamily))
    val reqProps: Option[HbaseDao.ColumnData] = rawData.get(columnFamily)
    hTableConnFactory.releaseTableInterface(hIndexTableInterface)
    val eD = reqProps.map(fields => {
      ExclusionDetails(
        exclusionType = fields.get("exclusionType").map(v => v.getString).map(ExclusionType.withName).orNull,
        metaInfo = fields.get("metaInfo").map(v => v.getString).orNull
      )
    })
    eD
  }

  @Timed("get")
  def get(channel: String, appName: String, destination: String): Try[List[ExclusionDetails]] = Try_#(s"ExclusionDao get failed for destination : $destination") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val rowKeys = ExclusionType.values.map(getRowKey(channel, appName, destination, _)).toList
    val rawDataList = fetchMultiRows(rowKeys, List(columnFamily))
    hTableConnFactory.releaseTableInterface(hTableInterface)
    rawDataList.values.flatMap(rowData => {
      val reqProps: Option[HbaseDao.ColumnData] = rowData.get(columnFamily)
      val eD = reqProps.map(fields => {
        ExclusionDetails(
          exclusionType = ExclusionType.withName(fields.get("exclusionType").map(v => v.getString).orNull),
          metaInfo = fields.get("metaInfo").map(v => v.getString).orNull
        )
      })
      eD
    }).toList
  }


  @Timed("getAll")
  def getAll(channel: String, appName: String, exclusionType: ExclusionType): Try[List[ExclusionEntity]] = Try_#(s"ExclusionDao getAll failed for exclusionType : $exclusionType") {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    val id = getRowKeyPrefix(channel,appName,exclusionType)
    val rawDataList = fetchRows(s"${id}_", s"${id}_{",  List(columnFamily))
    hTableConnFactory.releaseTableInterface(hTableInterface)
    rawDataList.values.flatMap(rowData => {
      val reqProps: Option[HbaseDao.ColumnData] = rowData.data.get(columnFamily)
      hTableConnFactory.releaseTableInterface(hTableInterface)
      val eD = reqProps.map(fields => {
        def get(name: String) = fields.get(name).map(v => v.getString).orNull
        ExclusionEntity(
          channel = get("channel"),
          appName = get("appName"),
          destination = get("destination"),
          exclusionDetails = ExclusionDetails(
            exclusionType = Option(get("exclusionType")).map(ExclusionType.withName).orNull,
            metaInfo = get("metaInfo")
          ))
      })
      eD
    }).toList
  }

}

object ExclusionDao {
  def apply(tableName: String, hTableFactory: THTableFactory) = new ExclusionDao(tableName, hTableFactory)
}
