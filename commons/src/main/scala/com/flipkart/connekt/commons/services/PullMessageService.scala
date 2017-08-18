package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.dao.TRequestDao
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PullRequestData, PullRequestInfo}
import com.flipkart.connekt.commons.utils.StringUtils.generateUUID
import com.roundeights.hasher.Implicits._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import java.util.concurrent.Executors

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Created by saurabh.mimani on 24/07/17.
  */
class PullMessageService(requestDao: TRequestDao) extends TService {
  private val messageDao: TRequestDao = requestDao

  def saveRequest(request: ConnektRequest): Try[Map[String,Set[String]]] = {
    try {
      val inAppInfo = request.channelInfo.asInstanceOf[PullRequestInfo]
      val channelData = request.channelData.asInstanceOf[PullRequestData].id match {
        case _:Any => request.channelData.asInstanceOf[PullRequestData]
        case _ => request.channelData.asInstanceOf[PullRequestData].copy(id = generateUUID)
      }
      var result = Map[String,Set[String]]()
      if (!request.isTestRequest)
      {
        inAppInfo.userIds.map{ userId =>
          val reqWithId = request.copy(id = s"${userId.sha256.hash.hex}:${channelData.eventType}:${channelData.id}")
          messageDao.saveRequest(reqWithId.id, reqWithId, true)
          ServiceFactory.getInAppMessageQueueService.enqueueMessage(reqWithId.appName, userId, reqWithId.id, reqWithId.expiryTs)(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10)))
          result += (reqWithId.id -> Set(userId))
        }
      }
      Success(result)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"Failed to save in app request ${e.getMessage}", e)
        Failure(e)
    }
  }

  def getRequest(appName: String, instanceId: String, startTs: Long, endTs: Long)(implicit ec: ExecutionContext): Future[Seq[ConnektRequest]] = {
    val pendingMessages = ServiceFactory.getInAppMessageQueueService.getMessages(appName, instanceId, Some(Tuple2(startTs + 1, endTs)))
    pendingMessages.map(_ids => {
      val distinctMessageIds = _ids.distinct
      val fetchedMessages: Try[List[ConnektRequest]] = getRequestbyIds(distinctMessageIds.toList)

      val sortedMessages: Try[Seq[ConnektRequest]] = fetchedMessages.map { _messages =>
        val mIdRequestMap = _messages.map(r => r.id -> r).toMap
        distinctMessageIds.flatMap(mId => mIdRequestMap.find(_._1 == mId).map(_._2))
      }
      sortedMessages.map(_.filter(_.expiryTs.forall(_ >= System.currentTimeMillis)).filterNot(_.isTestRequest)).getOrElse(List.empty[ConnektRequest])
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

  def markAsRead(appName: String, instanceId: String, startTs: Long, endTs: Long)(implicit ec: ExecutionContext) = {
    val messages = getRequest(appName, instanceId, startTs, endTs)
    messages.map(_messages => {
      _messages.map(_m => {
        if(!_m.channelData.asInstanceOf[PullRequestData].read){
          val updatedMessage = _m.copy(channelData = _m.channelData.asInstanceOf[PullRequestData].copy(read = true))
          messageDao.saveRequest(updatedMessage.id, updatedMessage, true)
        }
      })
    })
  }
}
