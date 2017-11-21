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
import com.flipkart.connekt.commons.entities.WACheckContactEntity
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed

import scala.util.Try

object WACheckContactService extends Instrumented {

  private lazy val dao = DaoFactory.getWACheckContactDao
  private final val WA_CONTACT_BATCH = ConnektConfig.getInt("wa.check.contact.batch.size").getOrElse(1000)
  private lazy val contactService = ServiceFactory.getContactService

  @Timed("add")
  def add(checkContactEntity: WACheckContactEntity): Try[Unit] = profile("add") {
    dao.add(checkContactEntity)
  }

  @Timed("get")
  def get(destination: String): Try[Option[WACheckContactEntity]] = profile("get") {
    dao.get(destination)
  }

  @Timed("gets")
  def gets(destinations: Set[String]): Try[List[WACheckContactEntity]] = profile("gets") {
    dao.gets(destinations)
  }

  @Timed("refreshAll")
  def refreshWAContacts = profile("refreshAll") {
    val task = new Runnable {
      override def run() = {
        try {
          dao.getAllContacts
            .grouped(WA_CONTACT_BATCH)
            .foreach(_.foreach(contact => contactService.enqueueContactEvents(contact)))
        } catch {
          case e: Exception =>
            ConnektLogger(LogFile.SERVICE).error(s"Contact warm-up failure", e)
        }
      }
    }

    new Thread(task).start()
  }

}
