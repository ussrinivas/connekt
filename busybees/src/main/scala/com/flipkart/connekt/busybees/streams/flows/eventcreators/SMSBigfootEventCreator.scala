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
package com.flipkart.connekt.busybees.streams.flows.eventcreators

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.SMSCallbackEvent

class SMSBigfootEventCreator extends GraphStage[FlowShape[SMSCallbackEvent, fkint.mp.connekt.SMSCallbackEvent]] {

  val in = Inlet[SMSCallbackEvent]("SMSBigfootEventCreator.In")
  val out = Outlet[fkint.mp.connekt.SMSCallbackEvent]("SMSBigfootEventCreator.Out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val event = grab(in)
        ConnektLogger(LogFile.PROCESSORS).info(s"SMSBigfootEventCreator received event: ${event.messageId}")
        try {
          if (isAvailable(out))
//            push(out, event.toBigfootFormat)
            null
        } catch {
          case e: Throwable =>
            ConnektLogger(LogFile.PROCESSORS).error(s"SMSBigfootEventCreator error", e)
            if (!hasBeenPulled(in)) {
              pull(in)
              ConnektLogger(LogFile.PROCESSORS).trace(s"SMSBigfootEventCreator pulled upstream for: ${event.messageId}")
            }
        }
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (!hasBeenPulled(in)) {
          pull(in)
          ConnektLogger(LogFile.PROCESSORS).trace(s"SMSBigfootEventCreator:: PULLED upstream on downstream pull.")
        }
      }
    })

  }

  override def shape = FlowShape.of(in, out)
}
