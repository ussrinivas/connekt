package com.flipkart.connekt.commons.dao

import com.flipkart.connekt.commons.factories.THTableFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.commons.utils.StringUtils.JSONMarshallFunctions
import com.flipkart.connekt.commons.utils.StringUtils.JSONUnMarshallFunctions
import com.flipkart.connekt.commons.dao.HbaseDao._
import org.apache.commons.codec.CharEncoding


/**
  * Created by saurabh.mimani on 24/07/17.
  */
class PullRequestDao (tableName: String, hTableFactory: THTableFactory) extends RequestDao(tableName: String, hTableFactory: THTableFactory) {
  override protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]] = {
    val pullRequestInfo = channelRequestInfo.asInstanceOf[PullRequestInfo]

    val m = scala.collection.mutable.Map[String, Array[Byte]]()

    Option(pullRequestInfo.userIds).foreach(m += "userId" -> _.mkString(",").getUtf8Bytes)
    Option(pullRequestInfo.appName).foreach(m += "appName" -> _.toString.getUtf8Bytes)
    m.toMap
  }

  override protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo = PullRequestInfo(
    appName = reqInfoProps.getS("appName"),
    userIds = reqInfoProps.getS("userId").split(",").toSet
  )

  override protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]] = {
      val m = scala.collection.mutable.Map[String, Array[Byte]]()
      val pullRequestData = channelRequestData.asInstanceOf[PullRequestData]
      Option(pullRequestData.id).foreach(m += "id" -> _.toString.getUtf8Bytes)
      Option(pullRequestData.text).foreach(m += "text" -> _.toString.getUtf8Bytes)
      Option(pullRequestData.title).foreach(m += "title" -> _.toString.getUtf8Bytes)
      Option(pullRequestData.eventType).foreach(m += "eventType" -> _.toString.getUtf8Bytes)
      Option(pullRequestData.link).foreach(m += "link" -> _.toString.getUtf8Bytes)
      Option(pullRequestData.imageLink).foreach(m += "imageLink" -> _.toString.getUtf8Bytes)
      Option(pullRequestData.read).foreach(m += "read" -> _.getBytes)
      Option(pullRequestData.channelSettings).foreach(m += "channelSettings" -> _.getJson.getUtf8Bytes)
      Option(pullRequestData.platformSettings).foreach(m += "platformSettings" -> _.getJson.getUtf8Bytes)
      m.toMap
  }

  override protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): PullRequestData = {
    PullRequestData(
      id = reqDataProps.getS("id"),
      text = reqDataProps.getS("text"),
      title = reqDataProps.getS("title"),
      eventType = reqDataProps.getS("eventType"),
      link = reqDataProps.getS("link"),
      imageLink = reqDataProps.getS("imageLink"),
      read = reqDataProps.getB("read"),
      channelSettings = Option(reqDataProps.getS("channelSettings")).map(_.getObj[Map[String,Boolean]]).orNull,
      platformSettings = Option(reqDataProps.getS("platformSettings")).map(_.getObj[Map[String,String]]).orNull
    )
  }


}

object PullRequestDao {
  def apply(tableName: String = "fk-connekt-pull-info", hTableFactory: THTableFactory) =
    new PullRequestDao(tableName, hTableFactory)
}
