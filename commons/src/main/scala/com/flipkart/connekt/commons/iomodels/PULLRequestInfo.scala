package com.flipkart.connekt.commons.iomodels

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
import com.fasterxml.jackson.annotation.JsonProperty

/**
  * Created by saurabh.mimani on 24/07/17.
  */
case class PULLRequestInfo(
          @JsonProperty(required = false) appName: String,
          @JsonProperty(required = false) eventType: String,
          @JsonProperty(required = false) userIds: Set[String] = Set.empty[String],
          ackRequired: Boolean,
          delayWhileIdle: Boolean) extends ChannelRequestInfo {
  def this() {
    this(null, null, Set.empty[String], false, false)
  }

}
