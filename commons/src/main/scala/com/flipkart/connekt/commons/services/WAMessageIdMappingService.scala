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
import com.flipkart.connekt.commons.entities.WAMessageIdMappingEntity
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed

import scala.util.Try

object WAMessageIdMappingService extends Instrumented {

  private lazy val dao = DaoFactory.getWaMessageIdMappingDao

  @Timed("add")
  def add(waMessageIdMappingEntity: WAMessageIdMappingEntity): Try[Unit] = profile("add") {
    dao.add(waMessageIdMappingEntity)
  }

  @Timed("get")
  def get(appName: String, waMessageId: String): Try[Option[WAMessageIdMappingEntity]] = profile(s"get") {
    dao.get(appName, waMessageId)
  }
}
