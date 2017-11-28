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
package com.flipkart.connekt.commons.entities

import com.flipkart.connekt.commons.entities.bigfoot.PublishSupport
import com.flipkart.connekt.commons.utils.DateTimeUtils

case class WAContactEntity(destination: String,
                           userName: String,
                           appName: String,
                           exists: String,
                           lastContacted: Option[Long],
                           lastCheckContactTS: Long = System.currentTimeMillis) extends PublishSupport {

  override def namespace: String = "fkint/mp/connekt/WAContactEntity"

  override def toPublishFormat: fkint.mp.connekt.WAContactEntity = {
    fkint.mp.connekt.WAContactEntity(destination = destination, userName = userName, appName = appName, exists = exists, lastCheckContactTS = DateTimeUtils.getStandardFormatted(lastCheckContactTS))
  }

}
