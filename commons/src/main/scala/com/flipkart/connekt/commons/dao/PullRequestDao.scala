package com.flipkart.connekt.commons.dao

import com.flipkart.connekt.commons.factories.THTableFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.commons.utils.StringUtils.JSONMarshallFunctions
import com.flipkart.connekt.commons.utils.StringUtils.JSONUnMarshallFunctions
import com.flipkart.connekt.commons.dao.HbaseDao._


/**
  * Created by saurabh.mimani on 24/07/17.
  */
class PullRequestDao (tableName: String, hTableFactory: THTableFactory) extends RequestDao(tableName: String, hTableFactory: THTableFactory) {
  override protected def channelRequestInfoMap(channelRequestInfo: ChannelRequestInfo): Map[String, Array[Byte]] = {
    val pullRequestInfo = channelRequestInfo.asInstanceOf[PullRequestInfo]

    val m = scala.collection.mutable.Map[String, Array[Byte]]()

    Option(pullRequestInfo.userIds).foreach(m += "userId" -> _.mkString(",").getUtf8Bytes)
    Option(pullRequestInfo.appName).foreach(m += "appName" -> _.toString.getUtf8Bytes)
    Option(pullRequestInfo.channelSettings).foreach(m += "channelSettings" -> _.getJson.getUtf8Bytes)
    Option(pullRequestInfo.platformSettings).foreach(m += "platformSettings" -> _.getJson.getUtf8Bytes)

    m.toMap
  }

  override protected def getChannelRequestInfo(reqInfoProps: Map[String, Array[Byte]]): ChannelRequestInfo = PullRequestInfo(
    appName = reqInfoProps.getS("appName"),
    platformSettings = reqInfoProps.getS("platformSettings").getObj[Map[String,String]],
    userIds = reqInfoProps.getS("userId").split(",").toSet,
    channelSettings = reqInfoProps.getS("channelSettings").getObj[Map[String, Boolean]]
  )

  override protected def channelRequestDataMap(channelRequestData: ChannelRequestData): Map[String, Array[Byte]] = {
    Option(channelRequestData).map(d => {
      println("d is here: " + d)
      println(d)
      val pullRequestData = d.asInstanceOf[PullRequestData]
      Option(pullRequestData.data).map(m => "data" -> m.toString.getUtf8Bytes).toMap
//      ++ Option(pullRequestData.pushType).map(m => "pushType" -> m.getUtf8Bytes).toMap
    }).orNull
  }

  override protected def getChannelRequestData(reqDataProps: Map[String, Array[Byte]]): PullRequestData = {
    val data = reqDataProps.getKV("data")
//    val pushType = reqDataProps.getS("pushType")

//    if(StringUtils.isNullOrEmpty(data) && StringUtils.isNullOrEmpty(pushType) )
//      null
//    else
    PullRequestData(data = data)
  }


}

object PullRequestDao {
  def apply(tableName: String = "fk-connekt-pull-info", hTableFactory: THTableFactory) =
    new PullRequestDao(tableName, hTableFactory)
}
