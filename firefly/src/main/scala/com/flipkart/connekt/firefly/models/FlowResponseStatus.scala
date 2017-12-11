package com.flipkart.connekt.firefly.models

import com.flipkart.connekt.commons.iomodels.TopologyOutputDatatype

object Status extends Enumeration {
  type Status = Value
  val Success = Value("success")
  val Failed = Value("failed")
}

private[firefly] case class FlowResponseStatus(responseStatus: Status.Status) extends TopologyOutputDatatype
