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

import com.flipkart.connekt.commons.dao.MessageQueueDao
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.metrics.Instrumented

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class MessageQueueService(dao: MessageQueueDao) extends TService with Instrumented {

  private val defaultTTL = 7.days.toMillis
  private val maxRecords = 100
  private val cleanupFactor = 0.1

  def enqueueMessage(appName: String, contactIdentifier: String, messageId: String, expiryTs: Option[Long] = None)(implicit ec: ExecutionContext): Future[Int] = {
    dao.enqueueMessage(appName.toLowerCase, contactIdentifier, messageId, expiryTs.getOrElse(System.currentTimeMillis() + defaultTTL)).andThen {
      case Success(count) if count >= maxRecords =>  dao.trimMessages(appName.toLowerCase, contactIdentifier, maxRecords * cleanupFactor toInt)
      case Failure(ex) =>
        meter("enqueueMessage.failure").mark()
        ConnektLogger(LogFile.SERVICE).error(s"MessageQueueService.enqueueMessage failed for $appName / $contactIdentifier / $messageId", ex)
    }
  }

  def removeMessage(appName: String, contactIdentifier: String, messageId: String): Future[_] = dao.removeMessage(appName.toLowerCase, contactIdentifier, messageId)

  def getMessages(appName: String, contactIdentifier: String, timestampRange: Option[(Long, Long)])(implicit ec: ExecutionContext): Future[List[String]] = dao.getMessages(appName.toLowerCase, contactIdentifier, timestampRange)(ec)

}
