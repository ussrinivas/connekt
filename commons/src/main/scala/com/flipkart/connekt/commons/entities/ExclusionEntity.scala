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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import org.apache.commons.lang.StringUtils

sealed case class ExclusionEntity(channel: String, appName: String, destination: String, exclusionDetails: ExclusionDetails)

case class ExclusionDetails(@JsonScalaEnumeration(classOf[ExclusionTypeSeDeserialize]) exclusionType: ExclusionType.ExclusionType,
                            metaInfo: String = StringUtils.EMPTY) {
  @JsonIgnore @transient lazy val ttl = ExclusionType.ttl(exclusionType)
}
