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

import com.flipkart.connekt.commons.dao.{MessageMetaData, TRequestDao}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PullCallbackEvent, PullRequestData, PullRequestInfo}
import com.flipkart.connekt.commons.utils.StringUtils.generateUUID
import com.roundeights.hasher.Implicits._
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.fasterxml.jackson.databind.node.ObjectNode

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import com.flipkart.connekt.commons.utils.StringUtils._
import org.apache.commons.lang.RandomStringUtils

class PullMessageService(requestDao: TRequestDao) extends TService {
  private val messageDao: TRequestDao = requestDao

  def saveRequest(request: ConnektRequest)(implicit ec: ExecutionContext): Try[String] = {
    Try_#(message = "PullMessageService.saveRequest: Failed to save pull request ") {
      val reqWithId = request.copy(id = generateUUID)
      val pullInfo = request.channelInfo.asInstanceOf[PullRequestInfo]
      val pullData = request.channelData.asInstanceOf[PullRequestData]

      // read and createTS will be removed after migration Completes
      val read = if(pullData.data.get("read") != null && pullData.data.get("read").asBoolean()) 1L else 0L
      val createTS = Option(pullData.data.get("generationTime")).map(_.asLong).getOrElse(System.currentTimeMillis())
      if (!request.isTestRequest)
      {
        messageDao.saveRequest(reqWithId.id, reqWithId, true)
        pullInfo.userIds.map(
          ServiceFactory.getPullMessageQueueService.enqueueMessage(reqWithId.appName, _, reqWithId.id, reqWithId.expiryTs, Some(read), Some(createTS))
        )
      }
      reqWithId.id
    }
  }

  def getRequest(appName: String, contactIdentifier: String, timeStampRange: Option[(Long, Long)], filter: Map[String, Any])(implicit ec: ExecutionContext): Future[(Seq[ConnektRequest], Map[String, MessageMetaData])] = {
    val pendingMessages = ServiceFactory.getPullMessageQueueService.getMessages(appName, contactIdentifier, timeStampRange)
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
      (filteredMessages, messageMap)
    })
  }

  def getRequestbyIds(ids: List[String]): Try[List[ConnektRequest]] = {
    Try_#(message = "PullMessageService.getRequestbyIds: Failed to get pull requests") {
      requestDao.fetchRequest(ids)
    }
  }

  def markAsRead(appName: String, contactIdentifier: String, filter: Map[String, Any])(implicit ec: ExecutionContext) = {
    getRequest(appName, contactIdentifier, None, filter).map(_request => {
      val unReadMsgs = _request match {
        case (requests, messageMetaDataMap) => requests.filter(request => messageMetaDataMap(request.id).read.get == 0L)
      }
      if (unReadMsgs.nonEmpty) {
        ServiceFactory.getPullMessageQueueService.markAsRead(appName, contactIdentifier, unReadMsgs.map(_.id))
        unReadMsgs.map(msg => {
          PullCallbackEvent(
            messageId = msg.id,
            contactId = contactIdentifier,
            eventId = RandomStringUtils.randomAlphabetic(10),
            clientId = filter.get("client").toString,
            contextId = msg.contextId.get,
            appName = appName,
            eventType = "READ")
        }).persist
      }
      unReadMsgs.map(_.id)
    })
  }
}
