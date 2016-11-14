package com.flipkart.connekt.busybees.streams.flows.transformers

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import com.flipkart.connekt.busybees.models.{EmailRequestTracker, EmailResponse}
import com.flipkart.connekt.busybees.streams.flows.MapAsyncFlowStage
import com.flipkart.connekt.commons.iomodels.EmailCallbackEvent
import com.flipkart.connekt.commons.metrics.Instrumented

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


class EmailProviderResponseFormatter(implicit m: Materializer, ec: ExecutionContext) extends MapAsyncFlowStage[(Try[HttpResponse], EmailRequestTracker), (Try[EmailResponse], EmailRequestTracker)](96) with Instrumented {

  override val map: ((Try[HttpResponse], EmailRequestTracker)) => Future[List[(Try[EmailResponse], EmailRequestTracker)]] = responseTrackerPair => Future(profile("map") {
    //TODO: Implement this.
    List.empty
  })(m.executionContext)

}
