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
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.commons.utils.StringUtils.JSONMarshallFunctions
import com.flipkart.connekt.commons.utils.StringUtils.JSONUnMarshallFunctions
import com.flipkart.connekt.commons.dao.HbaseDao._
import jdk.nashorn.internal.ir.ObjectNode
import org.apache.commons.codec.CharEncoding


class PullRequestDao (tableName: String, hTableFactory: THTableFactory) extends RequestDao(tableName: String, hTableFactory: THTableFactory) {
  override protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]] = {
    val pullRequestInfo = channelRequestInfo.asInstanceOf[PullRequestInfo]

    val m = scala.collection.mutable.Map[String, Array[Byte]]()

    pullRequestInfo.userIds.foreach(m += "userId" -> _.mkString(",").getUtf8Bytes)
    pullRequestInfo.appName.foreach(m += "appName" -> _.toString.getUtf8Bytes)
    m.toMap
  }

  override protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo = PullRequestInfo(
    appName = reqInfoProps.getS("appName"),
    userIds = reqInfoProps.getS("userId").split(",").toSet
  )

  override protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]] = {
      val m = scala.collection.mutable.Map[String, Array[Byte]]()
      val pullRequestData = channelRequestData.asInstanceOf[PullRequestData]
      Option(pullRequestData.data).foreach(m += "data" -> _.toString.getUtf8Bytes)
      m.toMap
  }

  override protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): PullRequestData = {
      Option(reqDataProps.getKV("data")).map(PullRequestData.apply).orNull
  }

}

object PullRequestDao {
  def apply(tableName: String = "fk-connekt-pull-info", hTableFactory: THTableFactory) =
    new PullRequestDao(tableName, hTableFactory)
}
