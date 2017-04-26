package com.flipkart.connekt.busybees.streams.flows.dispatchers

import akka.stream.contrib.Retry
import akka.stream.scaladsl.Flow
import com.flipkart.connekt.busybees.models.RequestTracker
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}

import scala.util.Try
import org.apache.logging.log4j.Level

object FlowRetry {

  sealed case class RetrySupportTracker[I, T <: RequestTracker](request: I, originalTracker: T, retryCount: Int = 0)

  implicit class RetrySupport[I, O, T <: RequestTracker, M](flow: Flow[(I, RetrySupportTracker[I, T]), (Try[O], RetrySupportTracker[I, T]), M]) {

    def withRetry(maxRetries: Int): Flow[(I, T), (Try[O], T), M] = {
      Flow[(I, T)].asInstanceOf[Flow[(I, T), (I, T), M]].map { case (request, tracker) =>
        request -> RetrySupportTracker(request, tracker)
      }
        .via(Retry(flow) { tracker =>
          val retry = {
            if (tracker.retryCount >= maxRetries)
              None
            else
              Some(tracker.request, tracker.copy(retryCount = tracker.retryCount + 1))
          }
          val logLevel = retry.map(_ => Level.WARN).getOrElse(Level.ERROR)
          ConnektLogger(LogFile.PROCESSORS).log(logLevel, s"RetrySupport/withRetry MessageId: ${tracker.originalTracker.messageId} failed will attempt Retry[${retry.isDefined}]")
          retry
        })
        .map { case (responseTry, supportTracker) => responseTry -> supportTracker.originalTracker }
    }

  }

}
