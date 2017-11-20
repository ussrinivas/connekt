package com.flipkart.connekt.commons.iomodels

import com.fasterxml.jackson.databind.node.ObjectNode

case class WAResponse(meta: ObjectNode, payload: Results, error: String) extends WAGeneratedEvent

case class Results(results: List[WAContact])

case class WAContact(input_number: String, wa_exists: String, wa_username: String)

trait WAGeneratedEvent
