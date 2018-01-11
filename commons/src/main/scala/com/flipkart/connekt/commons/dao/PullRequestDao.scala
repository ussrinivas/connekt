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


class PullRequestDao(tableName: String, hTableFactory: THTableFactory) extends RequestDao(tableName: String, hTableFactory: THTableFactory) {
  override protected def persistDataProps(appName: String): Boolean = true

  override protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]] = {
    val pullRequestInfo = channelRequestInfo.asInstanceOf[PullRequestInfo]
    Map(
      "userId" -> pullRequestInfo.userIds.mkString(",").getUtf8Bytes,
      "appName" -> pullRequestInfo.appName.getUtf8Bytes
    )
  }

  override protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo = PullRequestInfo(
    appName = reqInfoProps.getS("appName"),
    userIds = reqInfoProps.getS("userId").split(",").toSet
  )

  override protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]] = {
    val pullRequestData = channelRequestData.asInstanceOf[PullRequestData]
    Map(
      "data" -> pullRequestData.data.toString.getUtf8Bytes
    )
  }

  override protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): PullRequestData = {
    Option(reqDataProps.getKV("data")).map(PullRequestData.apply).orNull
  }

}

object PullRequestDao {
  def apply(tableName: String = "fk-connekt-pull-info", hTableFactory: THTableFactory) =
    new PullRequestDao(tableName, hTableFactory)
}
