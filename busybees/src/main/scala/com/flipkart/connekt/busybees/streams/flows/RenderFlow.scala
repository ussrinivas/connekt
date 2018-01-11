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
package com.flipkart.connekt.busybees.streams.flows

import com.flipkart.connekt.busybees.streams.errors.ConnektStageException
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.ConnektRequest
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.StringUtils._

class RenderFlow extends MapFlowStage[ConnektRequest, ConnektRequest] with Instrumented {

  private lazy implicit val stencilService = ServiceFactory.getStencilService

  override val map: (ConnektRequest) => List[ConnektRequest] = input => profile("map"){
    try {
      ConnektLogger(LogFile.PROCESSORS).debug("RenderFlow received message: {}", supplier(input.id))
      ConnektLogger(LogFile.PROCESSORS).trace("RenderFlow received message: {}", supplier(input.getJson))

      val mRendered = input.copy(
        channelData = input.stencilId match {
          case Some(_) => input.getComputedChannelData
          case None => input.channelData
        },
        meta = input.meta ++ input.stencilId.map("stencilId" -> _).toMap
      )

      List(mRendered)
    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"RenderFlow error", e)
        throw ConnektStageException(input.id, input.clientId,input.destinations, InternalStatus.RenderFailure, input.appName, input.channel,  input.contextId.orEmpty,  input.meta ++ input.stencilId.map("stencilId" -> _).toMap, s"RenderFlow-${e.getMessage}", e)
    }
  }
}
