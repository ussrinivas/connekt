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

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.dao.HbaseDao._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory, THTableFactory}
import com.flipkart.connekt.commons.iomodels.{ChannelRequestData, ChannelRequestInfo, ConnektRequest}
import com.flipkart.connekt.commons.serializers.KryoSerializer
import com.flipkart.connekt.commons.utils.StringUtils
import org.apache.hadoop.hbase.client.BufferedMutator
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.Channel

abstract class RequestDao(tableName: String, hTableFactory: THTableFactory) extends TRequestDao with HbaseDao {
  private val hTableConnFactory = hTableFactory
  private val hTableName = tableName
  private val hTable = hTableConnFactory.getTableInterface(hTableName)
  implicit lazy val hTableMutator: BufferedMutator = hTableFactory.getBufferedMutator(hTableName)

  private val executor = new ScheduledThreadPoolExecutor(1)
  private val flusher = executor.scheduleAtFixedRate(new Runnable {
    override def run(): Unit = Try_ {
      Option(hTableMutator).foreach(_.flush())
    }
  }, 60, 60, TimeUnit.SECONDS)

  override def close() = {
    flusher.cancel(true)
    Option(hTableMutator).foreach(_.close())
  }

  protected def persistDataProps(appName: String): Boolean

  protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]]

  protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo

  protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]]

  protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): ChannelRequestData

  private def getChannelRequestModel(reqDataProps: Map[String, Array[Byte]]) = reqDataProps.getKV("model")

  private def channelRequestModel(requestModel: ObjectNode) = Map[String, Array[Byte]]("model" -> requestModel.toString.getUtf8Bytes)

  override def  saveRequest(requestId: String, request: ConnektRequest) = {
    try {
      asyncAddRow(requestId, rawData(request))
      ConnektLogger(LogFile.DAO).debug(s"Request info persisted for $requestId")
    } catch {
      case e: IOException =>
        ConnektLogger(LogFile.DAO).error(s"Request info persistence failed for $requestId ${e.getMessage}", e)
        throw new IOException("Request info persistence failed for %s".format(requestId), e)
    }
  }

  override def saveBulkRequests(requests: List[ConnektRequest]) : List[String] = {
    try {
      val s = requests.map(req => req.id -> rawData(req))
      addRows(s)(hTable)
      requests.map(_.id)
    } catch {
      case e: IOException =>
        ConnektLogger(LogFile.DAO).error(s"Request info persistence failed for ${requests.map(_.id)} ${e.getMessage}", e)
        throw new IOException("Request info persistence failed for %s".format({requests.map(_.id)}), e)
    }
  }

  def rawData(request: ConnektRequest): Map[String, Map[String, Array[Byte]]] = {
    var requestProps = Map[String, Array[Byte]](
      "id" -> request.id.getUtf8Bytes,
      "channel" -> request.channel.getUtf8Bytes,
      "sla" -> request.sla.getUtf8Bytes,
      "clientId" -> request.clientId.getUtf8Bytes,
      "meta" -> KryoSerializer.serialize(request.meta)
    )

    request.stencilId.foreach(requestProps += "stencilId" -> _.getBytes)
    request.expiryTs.foreach(requestProps += "expiryTs" -> _.getBytes)
    request.scheduleTs.foreach(requestProps += "scheduleTs" -> _.getBytes)
    request.contextId.foreach(requestProps += "contextId" -> _.getBytes)

    val channelDataProps = if (persistDataProps(request.appName)) {
      val channelRequestDataProps = Option(request.channelData).map(channelRequestDataMap).getOrElse(Map.empty[String, Array[Byte]])
      val channelRequestModelProps = Option(request.channelDataModel).map(channelRequestModel).getOrElse(Map.empty[String, Array[Byte]])
      channelRequestDataProps ++ channelRequestModelProps
    } else
      Map.empty[String, Array[Byte]]

    Map[String, Map[String, Array[Byte]]]("r" -> requestProps, "c" -> channelRequestInfoMap(request.channelInfo), "t" -> channelDataProps)
  }

  override def fetchRequest(connektIds: List[String]): List[ConnektRequest] = {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    try {
      val colFamiliesReqd = List("r", "c", "t")
      val rawData: Map[String, RowData] = fetchMultiRows(connektIds, colFamiliesReqd)

      rawData.flatMap { case (rowKey: String, rowData: RowData) =>
        val reqProps = rowData.get("r")
        val reqChannelInfoProps = rowData.get("c")
        val reqChannelDataProps = rowData.get("t")

        val allProps = reqProps.flatMap[Map[String, Array[Byte]]](r => reqChannelInfoProps.map[Map[String, Array[Byte]]](m => m ++ r))

        allProps.map(fields => {
          val channelReqInfo = reqChannelInfoProps.map(getChannelRequestInfo).orNull
          val channelReqData = reqChannelDataProps.map(getChannelRequestData).orNull
          val channelReqModel = reqChannelDataProps.map(getChannelRequestModel).orNull

          ConnektRequest(
            id = rowKey,
            clientId = fields.getS("clientId"),
            contextId = Option(fields.getS("contextId")),
            channel = fields.getS("channel"),
            sla = fields.getS("sla"),
            stencilId = Option(fields.getS("stencilId")),
            scheduleTs = Option(fields.getL("scheduleTs")).map(_.asInstanceOf[Long]),
            expiryTs = Option(fields.getL("expiryTs")).map(_.asInstanceOf[Long]),
            channelInfo = channelReqInfo,
            channelData = channelReqData,
            channelDataModel = Option(channelReqModel).getOrElse(StringUtils.getObjectNode),
            meta = fields.get("meta").map(KryoSerializer.deserialize[Map[String, String]]).getOrElse(Map.empty[String, String])
          )
        })
      }.toList
    } catch {
      case e: IOException =>
        ConnektLogger(LogFile.DAO).error(s"Fetching Request info failed for $connektIds, ${e.getMessage}", e)
        throw new IOException(s"Fetching RequestInfo failed for $connektIds", e)
    } finally {
      hTableConnFactory.releaseTableInterface(hTableInterface)
    }
  }

  override def updateRequestStatus(id: String, status: ChannelRequestData) = {
    implicit val hTableInterface = hTableConnFactory.getTableInterface(hTableName)
    try {
      val statusPropsMap = channelRequestDataMap(status)
      val rawData = Map[String, Map[String, Array[Byte]]]("t" -> statusPropsMap)
      addRow(id, rawData)

      ConnektLogger(LogFile.DAO).info(s"Request status updated for $id")
    } catch {
      case e: IOException =>
        ConnektLogger(LogFile.DAO).error(s"Request status update failed for $id ${e.getMessage}", e)
        throw new IOException(s"Request status update failed for $id", e)
    } finally {
      hTableConnFactory.releaseTableInterface(hTableInterface)
    }
  }

  override def fetchRequestInfo(id: String): Option[ChannelRequestInfo] = {
    implicit val hTableInterface = hTableFactory.getTableInterface(tableName)
    try {
      val colFamiliesReqd = List("c")
      val rawData = fetchRow(id, colFamiliesReqd)

      val reqProps = rawData.get("c")
      reqProps.map(getChannelRequestInfo)

    } catch {
      case e: IOException =>
        ConnektLogger(LogFile.DAO).error(s"Fetching Request info failed for $id, ${e.getMessage}", e)
        throw new IOException(s"Fetching RequestInfo failed for $id", e)
    } finally {
      hTableConnFactory.releaseTableInterface(hTableInterface)
    }
  }
}
