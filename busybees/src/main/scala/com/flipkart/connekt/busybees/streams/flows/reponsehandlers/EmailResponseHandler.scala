package com.flipkart.connekt.busybees.streams.flows.reponsehandlers

import akka.stream.Materializer
import com.flipkart.connekt.busybees.models.{EmailRequestTracker, EmailResponse}
import com.flipkart.connekt.commons.iomodels.EmailCallbackEvent
import com.flipkart.connekt.commons.metrics.Instrumented

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class EmailResponseHandler (implicit m: Materializer, ec: ExecutionContext) extends EmailProviderResponseHandler[(Try[EmailResponse],EmailRequestTracker)](96) with Instrumented{
  override val map: ((Try[EmailResponse], EmailRequestTracker)) => Future[List[EmailCallbackEvent]] = reponseTrackerPair => Future({
    //TODO: Implement this

    List.empty
  })(m.executionContext)
}
