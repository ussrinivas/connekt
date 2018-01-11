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

import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.entities.WAContactEntity
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.helpers.KafkaProducerHelper
import com.flipkart.connekt.commons.iomodels.ContactPayload
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed

import scala.util.{Failure, Success, Try}

class WAContactService extends TService with Instrumented {

  var queueProducerHelper: KafkaProducerHelper = null
  private lazy val dao = DaoFactory.getWAContactDao
  private final val WA_CONTACT_BATCH = ConnektConfig.getInt("wa.check.contact.batch.size").getOrElse(1000)
  private final val WA_CONTACT_QUEUE = ConnektConfig.getString("wa.contact.topic.name").get

  def this(queueProducerHelper: KafkaProducerHelper) {
    this()
    this.queueProducerHelper = queueProducerHelper
  }

  @Timed("add")
  def add(contactEntity: WAContactEntity): Try[Unit] = profile("add") {
    dao.add(contactEntity)
  }

  @Timed("get")
  def get(appName: String, destination: String): Try[Option[WAContactEntity]] = profile("get") {
    dao.get(appName, destination)
  }

  @Timed("gets")
  def gets(appName: String, destinations: Set[String]): Try[List[WAContactEntity]] = profile("gets") {
    dao.gets(appName, destinations)
  }

  def refreshWAContacts() = {
    val task = new Runnable {
      override def run() = {
        profile("refreshAll") {
          dao.getAllContacts match {
            case Success(c) => c.grouped(WA_CONTACT_BATCH).foreach(_.foreach(contact => enqueueContactEvents(contact)))
            case Failure(f) => ConnektLogger(LogFile.SERVICE).error(s"Contact warm-up failure", f)
          }
        }
      }
    }
    new Thread(task).start()
  }

  @Timed("enqueueContactPayload")
  def enqueueContactEvents(contact: ContactPayload): Unit = {
    val reqId = generateUUID
    queueProducerHelper.writeMessages(WA_CONTACT_QUEUE, (reqId, contact.getJson))
  }

}

object WAContactService {
  var instance: WAContactService = _

  def apply(queueProducerHelper: KafkaProducerHelper): WAContactService = {
    if (null == instance)
      this.synchronized {
        instance = new WAContactService(queueProducerHelper)
      }
    instance
  }

  def apply(): WAContactService = {
    if (null == instance)
      this.synchronized {
        instance = new WAContactService()
      }
    instance
  }
}
