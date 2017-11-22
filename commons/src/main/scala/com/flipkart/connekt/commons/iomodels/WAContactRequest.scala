package com.flipkart.connekt.commons.iomodels

import com.fasterxml.jackson.annotation.JsonProperty

case class WAContactRequest(@JsonProperty payload: Payload)

case class Payload(@JsonProperty blocking: String = "wait",
                   @JsonProperty(required = true) users: List[String])
