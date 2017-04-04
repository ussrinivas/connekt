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
import com.flipkart.connekt.commons.entities.bigfoot.PublishSupport
import com.flipkart.connekt.commons.utils.DateTimeUtils
import org.apache.commons.lang.StringUtils

sealed case class ExclusionEntity(channel: String, appName: String, destination: String, exclusionDetails: ExclusionDetails, active:Boolean = true)  extends  PublishSupport{

  override def namespace: String = "fkint/mp/connekt/Suppressions"

  override def toPublishFormat: fkint.mp.connekt.Suppressions = {
    fkint.mp.connekt.Suppressions(
      destination =  destination, appName = appName, channel = channel,
      suppressionType = exclusionDetails.exclusionType.toString, ttl = {
        if (exclusionDetails.ttl.isFinite()) exclusionDetails.ttl.toSeconds else 0
      }, cargo = exclusionDetails.metaInfo,
      createdTime = DateTimeUtils.getStandardFormatted(), active = active
    )
  }
}

case class ExclusionDetails(@JsonScalaEnumeration(classOf[ExclusionTypeSeDeserialize]) exclusionType: ExclusionType.ExclusionType,
                            metaInfo: String = StringUtils.EMPTY) {
  @JsonIgnore
  @transient lazy val ttl = ExclusionType.ttl(exclusionType)
}
