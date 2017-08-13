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

import com.flipkart.connekt.commons.dao.{MessageMetaData, MessageQueueDao}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class MessageQueueService(dao: MessageQueueDao, private val defaultTTL: Long = 7.days.toMillis) extends TService with Instrumented {


  private val maxRecords = 100
  private val cleanupFactor = 0.1

  @Timed("enqueueMessage")
  def enqueueMessage(appName: String, contactIdentifier: String, messageId: String, expiryTs: Option[Long] = None, read: Option[Long] = None)(implicit ec: ExecutionContext): Future[Int] = {
    dao.enqueueMessage(appName.toLowerCase, contactIdentifier, messageId, expiryTs.getOrElse(System.currentTimeMillis() + defaultTTL), read).andThen {
      case Success(count) if count >= maxRecords =>
        ConnektLogger(LogFile.SERVICE).info(s"MessageQueueService.trimMessages triggered for $appName / $contactIdentifier, currentSize : $count")
        dao.trimMessages(appName.toLowerCase, contactIdentifier, maxRecords * cleanupFactor toInt)
      case Failure(ex) =>
        meter("enqueueMessage.failure").mark()
        ConnektLogger(LogFile.SERVICE).error(s"MessageQueueService.enqueueMessage failed for $appName / $contactIdentifier / $messageId", ex)
    }
  }

  @Timed("empty")
  def empty(appName: String, contactIdentifier: String ): Future[_] = dao.empty(appName.toLowerCase, contactIdentifier)

  @Timed("removeMessage")
  def removeMessage(appName: String, contactIdentifier: String, messageId: String): Future[_] = dao.removeMessage(appName.toLowerCase, contactIdentifier, messageId)

  @Timed("getMessages")
  def getMessages(appName: String, contactIdentifier: String, timestampRange: Option[(Long, Long)])(implicit ec: ExecutionContext): Future[Seq[String]] =  dao.getMessages(appName.toLowerCase, contactIdentifier, timestampRange)

  @Timed("getMessagesWithDetails")
  def getMessagesWithDetails(appName: String, contactIdentifier: String, timestampRange: Option[(Long, Long)])(implicit ec: ExecutionContext): Future[Seq[(String, MessageMetaData)]] =  dao.getMessagesWithDetails(appName.toLowerCase, contactIdentifier, timestampRange)

}
