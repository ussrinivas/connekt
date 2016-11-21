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

import com.flipkart.connekt.commons.factories.THTableFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.dao.HbaseDao._
import com.flipkart.connekt.commons.utils.StringUtils.JSONMarshallFunctions
import com.flipkart.connekt.commons.utils.StringUtils.JSONUnMarshallFunctions

class EmailRequestDao(tableName: String, hTableFactory: THTableFactory) extends RequestDao(tableName: String, hTableFactory: THTableFactory) {

  override protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]] = {
    val requestInfo = channelRequestInfo.asInstanceOf[EmailRequestInfo]

    val m = scala.collection.mutable.Map[String, Array[Byte]](
      "appName" -> requestInfo.appName.getUtf8Bytes,
      "to" -> requestInfo.to.getJson.getUtf8Bytes
    )
    if(requestInfo.cc != null && requestInfo.cc.nonEmpty)
      m += "cc" -> requestInfo.cc.getJson.getUtf8Bytes
    if(requestInfo.bcc != null && requestInfo.bcc.nonEmpty)
      m += "bcc" -> requestInfo.to.getJson.getUtf8Bytes

    m.toMap
  }

  override protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo = EmailRequestInfo(
    appName = reqInfoProps.getS("appName"),
    cc = Option(reqInfoProps.getS("cc")).map(_.getObj[Set[EmailAddress]]).getOrElse(Set.empty),
    bcc = Option(reqInfoProps.getS("bcc")).map(_.getObj[Set[EmailAddress]]).getOrElse(Set.empty),
    to = reqInfoProps.getS("to").getObj[Set[EmailAddress]]
  )

  override protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]] = {
    Option(channelRequestData).map(d => {
      val requestData = d.asInstanceOf[EmailRequestData]
      Map("subject" -> requestData.subject.getUtf8Bytes, "html" -> requestData.html.getUtf8Bytes, "text" -> requestData.text.getUtf8Bytes)
    }).orNull
  }

  override protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): ChannelRequestData = {
    EmailRequestData(
      subject = reqDataProps.getS("subject"),
      html = reqDataProps.getS("html"),
      text = reqDataProps.getS("text")
    )
  }

}
