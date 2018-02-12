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
import com.flipkart.connekt.commons.factories.ServiceFactory
import scala.reflect.runtime.universe._


class EmailRequestDao(tableName: String, hTableFactory: THTableFactory) extends RequestDao(tableName: String, hTableFactory: THTableFactory) {

  val SET_EMAIL_TYPETAG =  typeTag[Set[EmailAddress]]
  val SET_ATTACHMENTS_TYPETAG =  typeTag[List[Attachment]]

  override protected def persistDataProps(appName: String): Boolean =
    ServiceFactory.getUserProjectConfigService.getProjectConfiguration(appName, "email-store-enabled").get.forall(_.value.toBoolean)

  override protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]] = {
    val requestInfo = channelRequestInfo.asInstanceOf[EmailRequestInfo]

    val m = scala.collection.mutable.Map[String, Array[Byte]](
      "appName" -> requestInfo.appName.getUtf8Bytes,
      "to" -> requestInfo.to.getJson.getUtf8Bytes
    )
    if(requestInfo.cc != null && requestInfo.cc.nonEmpty)
      m += "cc" -> requestInfo.cc.getJson.getUtf8Bytes
    if(requestInfo.bcc != null && requestInfo.bcc.nonEmpty)
      m += "bcc" -> requestInfo.bcc.getJson.getUtf8Bytes
    Option(requestInfo.from).foreach(m += "from" -> _.getJson.getUtf8Bytes )
    Option(requestInfo.replyTo).foreach(m += "replyTo" -> _.getJson.getUtf8Bytes )

    m.toMap
  }

  override protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo = EmailRequestInfo(
    appName = reqInfoProps.getS("appName"),
    cc = Option(reqInfoProps.getS("cc")).map(_.getObj(SET_EMAIL_TYPETAG)).getOrElse(Set.empty),
    bcc = Option(reqInfoProps.getS("bcc")).map(_.getObj(SET_EMAIL_TYPETAG)).getOrElse(Set.empty),
    to = reqInfoProps.getS("to").getObj(SET_EMAIL_TYPETAG),
    from = Option(reqInfoProps.getS("from")).map(_.getObj[EmailAddress]).orNull,
    replyTo = Option(reqInfoProps.getS("replyTo")).map(_.getObj[EmailAddress]).orNull
  )

  override protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]] = {
    Option(channelRequestData).map(d => {
      val requestData = d.asInstanceOf[EmailRequestData]

      val m = scala.collection.mutable.Map[String, Array[Byte]]()
      Option(requestData.subject).foreach(m += "subject" -> _.getUtf8Bytes )
      Option(requestData.html).foreach(m += "html" -> _.getUtf8Bytes )
      Option(requestData.text).foreach(m += "text" -> _.getUtf8Bytes )
      Option(requestData.attachments).foreach(m += "attachments" -> _.getJson.getUtf8Bytes )
      m.toMap
    }).orNull
  }

  override protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): ChannelRequestData = {
    EmailRequestData(
      subject = reqDataProps.getS("subject"),
      html = reqDataProps.getS("html"),
      text = reqDataProps.getS("text"),
      attachments = Option(reqDataProps.getS("attachments")).map(_.getObj(SET_ATTACHMENTS_TYPETAG)).orNull
    )
  }

}
