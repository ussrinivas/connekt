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

import com.flipkart.connekt.commons.dao.TRequestDao
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PullCallbackEvent, PullRequestData, PullRequestInfo}
import com.flipkart.connekt.commons.utils.StringUtils.generateUUID
import com.roundeights.hasher.Implicits._

import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import java.util.concurrent.Executors

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import com.flipkart.connekt.commons.utils.StringUtils._
import org.apache.commons.lang.RandomStringUtils

/**
  * Created by saurabh.mimani on 24/07/17.
  */
class PullMessageService(requestDao: TRequestDao) extends TService {
  private val messageDao: TRequestDao = requestDao

  def saveRequest(request: ConnektRequest): Try[String] = {
    try {
      val reqWithId = request.copy(id = generateUUID)
      val inAppInfo = request.channelInfo.asInstanceOf[PullRequestInfo]
      val inAppData = request.channelData.asInstanceOf[PullRequestData]
      println("inAppData " + inAppData)
      val read = if(inAppData.data.get("read") != null && inAppData.data.get("read").asBoolean()) 1L else 0L
      if (!request.isTestRequest)
      {
        messageDao.saveRequest(reqWithId.id, reqWithId, true)
        inAppInfo.userIds.map(
          ServiceFactory.getInAppMessageQueueService.enqueueMessage(reqWithId.appName, _, reqWithId.id, reqWithId.expiryTs, Some(read))(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10)))
        )
      }
      Success(reqWithId.id)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"Failed to save in app request ${e.getMessage}", e)
        Failure(e)
    }
  }

  def getRequest(appName: String, instanceId: String, startTs: Long, endTs: Long, filter: Map[String, Any])(implicit ec: ExecutionContext): Future[(Seq[Map[String, Any]], Int)] = {
    val pendingMessages = ServiceFactory.getInAppMessageQueueService.getMessagesWithDetails(appName, instanceId, Some(Tuple2(startTs + 1, endTs)))
    pendingMessages.map(queueMessages => {
      val messageMap = queueMessages.toMap
      val distinctMessageIds = queueMessages.map(_._1).distinct
      val fetchedMessages: Try[List[ConnektRequest]] = getRequestbyIds(distinctMessageIds.toList)

      val sortedMessages: Try[Seq[ConnektRequest]] = fetchedMessages.map { _messages =>
        val mIdRequestMap = _messages.map(r => r.id -> r).toMap
        distinctMessageIds.flatMap(mId => mIdRequestMap.find(_._1 == mId).map(_._2))
      }
      val validMessages = sortedMessages.map(_.filter(_.expiryTs.forall(_ >= System.currentTimeMillis)).filterNot(_.isTestRequest)).getOrElse(List.empty[ConnektRequest])

      val stencilService = ServiceFactory.getStencilService
      val filteredMessages = stencilService.getStencilsByName(s"pull-${appName.toLowerCase}-fetch-filter").headOption match {
        case Some(stencil) =>
          validMessages.filter(c => stencilService.materialize(stencil, Map("data" -> c.channelData.asInstanceOf[PullRequestData], "filter" -> filter).getJsonNode).asInstanceOf[Boolean])
        case None => validMessages
      }

      val unreadCount = filteredMessages.count(m => messageMap(m.id).read.get == 0L)
      val pullRequesData = filteredMessages.map { prd =>
        Map("messageId" -> prd.id, "read" -> (messageMap(prd.id).read.get == 1L)) ++ prd.channelData.asInstanceOf[PullRequestData].ccToMap
      }
      (pullRequesData, unreadCount)
    })
  }

  def getRequestbyIds(ids: List[String]): Try[List[ConnektRequest]] = {
    try {
      Success(requestDao.fetchRequest(ids))
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"Get request info failed ${e.getMessage}", e)
        Failure(e)
    }
  }

  def markAsRead(appName: String, instanceId: String, startTs: Long, endTs: Long, filter: Map[String, Any])(implicit ec: ExecutionContext) = {

    getRequest(appName, instanceId, startTs, endTs, filter).map(_messages => {
      val unReadMsgIds =  _messages._1
                                   .filter(!_("read").asInstanceOf[Boolean])
                                   .map(_("messageId").toString)
      ServiceFactory.getInAppMessageQueueService.markQueueMessagesAsRead(appName, instanceId, unReadMsgIds)
      unReadMsgIds.foreach(msgId => {
        val event = PullCallbackEvent(
          messageId = msgId,
          eventId = RandomStringUtils.randomAlphabetic(10),
          clientId = filter.get("client").getOrElse("").toString,
          contextId = "",
          appName = appName,
          eventType = "markAsRead")
        event.validate()
        event.persist
      })
    })


  }
}
