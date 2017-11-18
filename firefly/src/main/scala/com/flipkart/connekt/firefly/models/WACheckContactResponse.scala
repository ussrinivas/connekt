package com.flipkart.connekt.firefly.models

import com.fasterxml.jackson.databind.node.ObjectNode

case class WACheckContactResponse(meta: ObjectNode, payload: Results, error: String)

case class Results(results: List[WAContact])

case class WAContact(input_number: String, wa_exists: String, wa_username: String)
