package com.flipkart.connekt.busybees.streams.flows.transformers

import akka.http.scaladsl.model.HttpRequest
import com.flipkart.connekt.busybees.models.EmailRequestTracker
import com.flipkart.connekt.busybees.streams.flows.MapFlowStage
import com.flipkart.connekt.commons.iomodels.EmailPayloadEnvelope


class EmailProviderPrepare extends MapFlowStage[EmailPayloadEnvelope, (HttpRequest, EmailRequestTracker)] {
  override val map: (EmailPayloadEnvelope) => List[(HttpRequest, EmailRequestTracker)] = emailPayloadEnvelope => {
   List.empty
  }
}
