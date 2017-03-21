package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.metrics.Instrumented

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object MessageQueueService extends Instrumented {

  private lazy val pullDao = DaoFactory.getMessageQueueDao
  private val defaultTTL =  7.days.toMillis

  def enqueueMessage(appName: String, contactIdentifier: String, messageId: String, ttl: Option[Long] = None): Future[_] = pullDao.enqueueMessage(appName.toLowerCase, contactIdentifier, messageId, ttl.getOrElse(System.currentTimeMillis() + defaultTTL))

  def removeMessage(appName: String, contactIdentifier: String, messageId: String): Future[_] = pullDao.removeMessage(appName, contactIdentifier, messageId)

  def getMessages(appName: String, contactIdentifier: String, timestampRange: Option[(Long, Long)])(implicit ec: ExecutionContext): Future[List[String]] = pullDao.getMessages(appName,contactIdentifier, timestampRange)(ec)

}
