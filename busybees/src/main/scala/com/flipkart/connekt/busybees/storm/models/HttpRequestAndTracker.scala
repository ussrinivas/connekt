package com.flipkart.connekt.busybees.storm.models

import akka.http.scaladsl.model.HttpRequest
import com.flipkart.connekt.busybees.models.GCMRequestTracker

case class HttpRequestAndTracker(httpRequest : HttpRequest, requestTracker : GCMRequestTracker)
