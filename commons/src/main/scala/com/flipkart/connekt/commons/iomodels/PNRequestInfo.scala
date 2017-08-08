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
package com.flipkart.connekt.commons.iomodels

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.flipkart.connekt.commons.entities.EnumTypeHint

import scala.annotation.meta._

case class PNRequestInfo(@JsonProperty(required = false) platform: String,
                         @JsonProperty(required = false) appName: String,
                         @JsonProperty(required = false) deviceIds: Set[String] = Set.empty[String],
                         @JsonProperty(required = false) topic:Option[String] = None,
                         ackRequired: Boolean,
//                         @JsonSerialize(using = classOf[PriorityStringSerializer])
//                         @JsonDeserialize(using = classOf[PriorityStringDeserializer])
                         @JsonScalaEnumeration(classOf[PriorityType])
                         priority: Priority.Value) extends ChannelRequestInfo {
  def this() {
    this(null, null, Set.empty[String], None, false, null)
  }

}
