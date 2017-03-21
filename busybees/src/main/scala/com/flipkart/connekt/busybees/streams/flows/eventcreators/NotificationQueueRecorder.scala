package com.flipkart.connekt.busybees.streams.flows.eventcreators

import com.flipkart.connekt.busybees.streams.errors.ConnektPNStageException
import com.flipkart.connekt.busybees.streams.flows.MapAsyncFlowStage
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PNRequestInfo}
import com.flipkart.connekt.commons.services.MessageQueueService
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class NotificationQueueRecorder(parallelism: Int)(ec: ExecutionContext) extends MapAsyncFlowStage[ConnektRequest, ConnektRequest](parallelism) {
  override val map: (ConnektRequest) => Future[List[ConnektRequest]] = message => {
    try {
      ConnektLogger(LogFile.PROCESSORS).info(s"NotificationQueueRecorder received message: ${message.id}")
      ConnektLogger(LogFile.PROCESSORS).trace(s"NotificationQueueRecorder received message: {}", supplier(message))

      val pnInfo = message.channelInfo.asInstanceOf[PNRequestInfo]
      val enqueueFutures = pnInfo.deviceIds.map(MessageQueueService.enqueueMessage(message.appName, _, message.id))

      val promise = Promise[List[ConnektRequest]]()

      Future.sequence(enqueueFutures).andThen {
        case Success(_) => promise.success(List(message))
        case Failure(ex) =>
          ConnektLogger(LogFile.PROCESSORS).error(s"NotificationQueueRecorder MessageQueueService.enqueueMessage failed for ${message.id}", ex)
          promise.success(List(message))
      }

      promise.future
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"NotificationQueueRecorder error for ${message.id}", e)
        throw ConnektPNStageException(message.id, message.clientId, message.destinations, InternalStatus.StageError, message.appName, message.platform, message.contextId.orEmpty, message.meta, "NotificationQueueRecorder::".concat(e.getMessage), e)
    }

  }
}
