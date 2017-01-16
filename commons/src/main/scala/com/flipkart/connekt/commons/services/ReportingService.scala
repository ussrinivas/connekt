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
package com.flipkart.connekt.commons.services

import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, ScheduledThreadPoolExecutor, TimeUnit}

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.dao.StatsReportingDao
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.commons.utils.{DateTimeUtils, StringUtils}
import com.flipkart.metrics.Timed

import scala.collection.JavaConverters._
import scala.util.Try

abstract class ReportingService(reportManagerDao: StatsReportingDao) extends TService with Instrumented {

  def init() = {
    val executor = new ScheduledThreadPoolExecutor(1)
    executor.scheduleAtFixedRate(statsUpdateTask, 1, 60, TimeUnit.SECONDS)
    val instanceId = StringUtils.generateRandomStr(5)
    sys.addShutdownHook{
      ConnektLogger(LogFile.SERVICE).info(s"Shutting ReportingService #$instanceId")
      statsUpdateTask.run()
    }
    ConnektLogger(LogFile.SERVICE).info(s"Initialized ReportingService #$instanceId")
  }

  def statsUpdateTask = new Runnable {
    @Timed("update")
    override def run(): Unit = Try {
      val tagStats = mapCounter.collect({
        case (tag, tagCount) =>
          (tag, tagCount.getAndSet(0))
      }).filter(_._2 > 0).toList
      reportManagerDao.counter(tagStats)
      mapCounter.retain((key, counterValue) => counterValue.get() > 0L)
    }
  }

  protected def datePrefix:String = DateTimeUtils.calenderDate.print(Calendar.getInstance().getTimeInMillis) + "."

  private val mapCounter = new ConcurrentHashMap[String, AtomicLong]().asScala

  protected def updateTagCounters(clientId: String, count: Long, datePrefix: String, primaryTags: String*)(secondaryTags: String*): Unit = {
    val combinations = makeKey(clientId, primaryTags: _*)(secondaryTags: _*)
    combinations.foreach(key => incrementCounter(datePrefix + key, count))
  }

  private def incrementCounter(key: String, delta: Long): Unit = {
    mapCounter.putIfAbsent(key, new AtomicLong(delta)).map(_.getAndAdd(delta))
  }

  private def getAllCombinations(list: List[String]): List[String] = {
    list.toSet[String].subsets().map(_.mkString(".")).toList.drop(1)
  }

  /**
   * key is generated in the order of <date>_<clientId>_<campaignId>_<channel>_<stencilId>_<platform>_<appName>_<event>
   * Ordering of each value is important, because we want to retrieve result on same order
   * Keep in mind : don't change the order
   * * primaryTags : This represents variables which are related to clients : clientId, channel, campaignId etc.,
   * * channelTags : This represents variables which are specific to payload : appName, platform, event etc.,
   * @return
   */
  private def makeKey(clientId: String, primaryTags: String*)(channelTags: String*): List[String] = {

    assert(clientId != null, "`clientId` cannot be null")
    val primaryPrefixes = getAllCombinations(primaryTags.toList.filter(_ != null))
    val channelSuffixes = getAllCombinations(channelTags.toList.filter(_ != null))

    for (x <- primaryPrefixes; y <- channelSuffixes)
      yield clientId + "." + x + "." + y
  }
}

class ClientReportingService(reportManagerDao: StatsReportingDao) extends ReportingService(reportManagerDao) {

  def getAllDetails(date: String, clientId: String, campaignId: Option[String], appName: Option[String], platform: Option[String], channel: Option[String]): Map[String, Long] = {
    val prefixString = List(date, clientId, campaignId.orNull, appName.orNull, platform.orNull, channel.orNull).filter(_ != null).mkString(".")
    val allKeys: List[String] = reportManagerDao.prefix(prefixString)
    val resultMap = reportManagerDao.get(allKeys)
    resultMap.map {
      case (keyName, count) =>
        keyName.split('.').drop(2).mkString(".") -> count
    }
  }

  @Timed("pushStatsUpdate")
  def recordPushStatsDelta(clientId: String, contextId: Option[String], stencilId: Option[String], platform: Option[String], appName: String, event: String, count: Int = 1): Unit = {
    updateTagCounters(clientId, count.toLong, datePrefix, contextId.orNull, Channel.PUSH, stencilId.orNull)(platform.orNull, appName.toLowerCase, event)
  }

  @Timed("channelStatsUpdate")
  def recordChannelStatsDelta(clientId: String, contextId: Option[String], stencilId: Option[String], channel: Channel, appName: String, event: String, count: Int = 1): Unit = {
    updateTagCounters(clientId, count.toLong, datePrefix, contextId.orNull, channel, stencilId.orNull)(appName.toLowerCase, event)
  }
}

class ExpenseTrackingService(reportManagerDao: StatsReportingDao) extends ReportingService(reportManagerDao) {

  private lazy val stencilService = ServiceFactory.getStencilService

  /**
    * Given an expense, classify it to an expenseType using the appropriate classifier
    * @param channel Channel
    * @param expenseHead String
    * @param expense ObjectNode
    * @return String
    */
  def classifyExpense(channel: Channel, expenseHead:String, expense:ObjectNode):String = {
    val classifier = stencilService.getStencilsByName(s"ckt-spend-$channel-$expenseHead-classifier").head
    stencilService.materialize(classifier, expense).asInstanceOf[String]
  }

  def addExpense(clientId: String, appName: String, channel: Channel, expenseHead:String, expenseType:String, count: Int = 1 ): Unit ={
    updateTagCounters(clientId, count.toLong, datePrefix, appName.toLowerCase, channel.toString)(expenseHead, expenseType)
  }

  def getAllExpenses(date: String, clientId: String, appName: String, channel: String, expenseHead:String): Map[String, Long] = {
    val prefixString = List(date, clientId, appName, channel, expenseHead).filter(_ != null).mkString(".")
    val allKeys: List[String] = reportManagerDao.prefix(prefixString)
    val resultMap = reportManagerDao.get(allKeys)
    resultMap.map {
      case (keyName, count) =>
        keyName.split('.').drop(2).mkString(".") -> count
    }
  }

}
