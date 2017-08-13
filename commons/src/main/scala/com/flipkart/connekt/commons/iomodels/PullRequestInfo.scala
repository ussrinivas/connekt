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
case class PullRequestInfo(
              @JsonProperty(required = false) appName: String,
              @JsonProperty(required = false) platformSettings: Map[String, String] = Map.empty[String, String],
              @JsonProperty(required = false) channelSettings: Map[String, Boolean] = Map.empty[String, Boolean],
              @JsonProperty(required = false) userIds: Set[String] = Set.empty[String]
            ) extends ChannelRequestInfo {

  def this() {
    this(null, Map.empty[String, String], Map.empty[String, Boolean], Set.empty[String])
  }
}
