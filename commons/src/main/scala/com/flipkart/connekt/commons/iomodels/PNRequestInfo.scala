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
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.commons.factories.ServiceFactory

case class PNRequestInfo(@JsonProperty(required = false) platform: String,
                         @JsonProperty(required = false) appName: String,
                         @JsonProperty(required = false) deviceIds: Set[String] = Set.empty[String],
                         @JsonProperty(required = false) topic:Option[String] = None,
                         @JsonProperty(required = false) channelId:Option[String] = None,
                         ackRequired: Boolean,
                         priority: Priority = Priority.HIGH) extends ChannelRequestInfo {
  def this() {
    this(null, null, Set.empty[String], None, None, false, Priority.HIGH)
  }

  def validateChannel(clientId : String) = {
    val cId = channelId.getOrElse("")

    require(ServiceFactory.getUserProjectConfigService.getProjectConfiguration(appName, "pn-allowed-channels").get.forall(userProjectConfig => {
      userProjectConfig.value.getObj[Map[String, List[String]]].getOrElse(clientId, List.empty).contains(cId)
    }), s"Channel $cId not whitelisted")
  }

}
