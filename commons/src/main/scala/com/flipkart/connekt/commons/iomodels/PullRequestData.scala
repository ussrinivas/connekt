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

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.commons.services.TStencilService

case class PullRequestData(data: ObjectNode) extends ChannelRequestData {

  def validate(appName: String)(implicit stencilService: TStencilService) = {
    stencilService.getStencilsByName(s"ckt-$appName-pull").find(_.component.equalsIgnoreCase("validate")) match {
      case Some(stencil) =>
        val errors = stencilService.materialize(stencil, Map("data" -> data).getJsonNode).toString
        require(errors.isEmpty, errors)
      case _ => None
    }
  }
}
