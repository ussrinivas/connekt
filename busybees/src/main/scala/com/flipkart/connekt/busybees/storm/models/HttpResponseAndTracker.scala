package com.flipkart.connekt.busybees.storm.models

import akka.http.scaladsl.model.HttpResponse
import com.flipkart.connekt.busybees.models.GCMRequestTracker

case class HttpResponseAndTracker(httpResponse : HttpResponse, requestTracker : GCMRequestTracker)
