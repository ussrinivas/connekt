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

class SmsRequestDao(tableName: String, hTableFactory: THTableFactory) extends RequestDao(tableName: String, hTableFactory: THTableFactory) {

  override protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]] = {
    val smsRequestInfo = channelRequestInfo.asInstanceOf[SmsRequestInfo]

    val m = scala.collection.mutable.Map[String, Array[Byte]]()

    m += "sender" -> smsRequestInfo.sender.toString.getUtf8Bytes
    m += "receivers" -> smsRequestInfo.receivers.mkString(",").getUtf8Bytes
    m += "appName" -> smsRequestInfo.appName.toString.getUtf8Bytes

    m.toMap
  }

  override protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo = SmsRequestInfo(
    appName = reqInfoProps.getS("appName"),
    sender = reqInfoProps.getS("sender"),
    receivers = reqInfoProps.getKV("receivers").asInstanceOf[Set[String]]
  )

  override protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]] = {
    Option(channelRequestData).map(d => {
      val SMSRequestData = d.asInstanceOf[SmsRequestData]
      Option(SMSRequestData.body).map(m => "body" -> m.toString.getUtf8Bytes).toMap
    }).orNull
  }

  override protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): ChannelRequestData = {
    val body = reqDataProps.getKV("body").toString
    SmsRequestData(body = body)
  }

  def fetchSmsRequestInfo(id: String): Option[SmsRequestInfo] = {
    fetchRequestInfo(id).map(_.asInstanceOf[SmsRequestInfo])
  }
}

object SmsRequestDao {
  def apply(tableName: String = "fk-connekt-sms-info", hTableFactory: THTableFactory) =
    new SmsRequestDao(tableName, hTableFactory)
}
