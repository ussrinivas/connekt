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
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed

import scala.util.Try

object WACheckContactService extends Instrumented {

  private lazy val dao = DaoFactory.getWACheckContactDao

  @Timed("add")
  def add(checkContactEntity: WACheckContactEntity): Try[Unit] = profile(s"add.${checkContactEntity.appName}") {
    dao.add(checkContactEntity)
  }

  @Timed("get")
  def get(appName: String, destination: String): Try[Option[WACheckContactEntity]] = profile(s"get.$appName") {
    dao.get(appName, destination)
  }

  @Timed("gets")
  def gets(appName: String, destinations: Set[String]): Try[List[WACheckContactEntity]] = profile(s"gets.$appName") {
    dao.gets(appName, destinations)
  }
}
