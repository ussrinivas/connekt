package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.dao.TRequestDao
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PullRequestInfo}
import com.flipkart.connekt.commons.utils.StringUtils.generateUUID
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

import scala.util.{Failure, Success, Try}

/**
  * Created by saurabh.mimani on 24/07/17.
  */
class PullMessageService(requestDao: TRequestDao) extends TService {
  private val messageDao: TRequestDao = requestDao

  def saveRequest(request: ConnektRequest): Try[String] = {
    try {
      val reqWithId = request.copy(id = generateUUID)
      val inAppInfo = request.channelInfo.asInstanceOf[PullRequestInfo]
      if (!request.isTestRequest)
      {
        messageDao.saveRequest(reqWithId.id, reqWithId, true)
        inAppInfo.userIds.map(
          ServiceFactory.getInAppMessageQueueService.enqueueMessage(reqWithId.appName, _, reqWithId.id, reqWithId.expiryTs)(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10)))
        )
      }
      Success(reqWithId.id)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"Failed to save in app request ${e.getMessage}", e)
        Failure(e)
    }
  }

  def getRequestInfo(ids: List[String]): Try[List[ConnektRequest]] = {
    try {
      Success(requestDao.fetchRequest(ids))
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"Get request info failed ${e.getMessage}", e)
        Failure(e)
    }
  }

}
