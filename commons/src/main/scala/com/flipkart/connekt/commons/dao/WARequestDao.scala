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

import com.flipkart.connekt.commons.dao.HbaseDao._
import com.flipkart.connekt.commons.factories.THTableFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils.JSONMarshallFunctions

class WARequestDao(tableName: String, hTableFactory: THTableFactory) extends RequestDao(tableName: String, hTableFactory: THTableFactory) {

  override protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]] = {
    val waRequestInfo = channelRequestInfo.asInstanceOf[WARequestInfo]

    scala.collection.mutable.Map[String, Array[Byte]](
      "destinations" -> waRequestInfo.destinations.mkString(",").getUtf8Bytes,
      "appName" -> waRequestInfo.appName.getUtf8Bytes
    ).toMap
  }

  override protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo = WARequestInfo(
    appName = reqInfoProps.getS("appName"),
    destinations = reqInfoProps.getS("destinations").split(",").toSet
  )

  override protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]] = {
    Option(channelRequestData).map(d => {
      val waRequestData = d.asInstanceOf[WARequestData]
      val m = scala.collection.mutable.Map[String, Array[Byte]]()
      waRequestData.message.foreach(m += "message" -> _.getUtf8Bytes)
      waRequestData.attachment.foreach(m += "attachment" -> _.getJson.getUtf8Bytes)
      m += "waType" -> waRequestData.waType.toString.getUtf8Bytes
      m.toMap
    }).orNull
  }

  override protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): ChannelRequestData = {
    WARequestData(
      WAType.valueOf(reqDataProps.getS("waType")),
      reqDataProps.get("message").map(new String(_)),
      reqDataProps.get("attachment").map(_.asInstanceOf[Attachment])
    )
  }
}

object WARequestDao {
  def apply(tableName: String = "fk-connekt-wa-info", hTableFactory: THTableFactory) =
    new WARequestDao(tableName, hTableFactory)
}
