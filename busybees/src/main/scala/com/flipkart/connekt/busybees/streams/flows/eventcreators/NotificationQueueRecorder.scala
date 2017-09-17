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

import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.busybees.streams.flows.MapAsyncFlowStage
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PNRequestInfo}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.{ExecutionContext, Future, Promise}

class NotificationQueueRecorder(parallelism: Int)(implicit ec: ExecutionContext) extends MapAsyncFlowStage[ConnektRequest, ConnektRequest](parallelism) with Instrumented {

  private lazy val shouldAwait = ConnektConfig.getBoolean("topology.push.queue.await").getOrElse(false)

  override val map: (ConnektRequest) => Future[List[ConnektRequest]] = message => {
    val profiler = timer("map").time()
    try {
      ConnektLogger(LogFile.PROCESSORS).trace(s"NotificationQueueRecorder received message: ${message.id}")

      val pnInfo = message.channelInfo.asInstanceOf[PNRequestInfo]
      val promise = Promise[List[ConnektRequest]]()

      if (message.isTestRequest)
        promise.success(List(message))
      else {
        val enqueueFutures = pnInfo.deviceIds.map(
          ServiceFactory.getMessageQueueService.enqueueMessage(message.appName, _, message.id)
        )
        if (shouldAwait) //TODO: Remove this when cross-dc calls are taken care of.
          Future.sequence(enqueueFutures).onComplete(_ => promise.success(List(message)))
        else
          promise.success(List(message))
      }
      promise.future.onComplete(_ => profiler.stop())
      promise.future
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"NotificationQueueRecorder error for ${message.id}", e)
        throw ConnektPNStageException(message.id, message.clientId, message.destinations, InternalStatus.StageError, message.appName, message.platform, message.contextId.orEmpty, message.meta, "NotificationQueueRecorder::".concat(e.getMessage), e)
    }

  }
}
