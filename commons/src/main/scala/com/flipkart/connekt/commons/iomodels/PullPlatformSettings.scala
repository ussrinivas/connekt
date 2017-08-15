package com.flipkart.connekt.commons.iomodels

import com.fasterxml.jackson.annotation.JsonProperty

/**
  * Created by saurabh.mimani on 15/08/17.
  */
case class PullPlatformSettings (
                                  Android: String = "0",
                                  iOS: String = "0",
                                  WP: String = "0"
                                ) {
  def this() {
    this("0", "0", "0")
  }
}
