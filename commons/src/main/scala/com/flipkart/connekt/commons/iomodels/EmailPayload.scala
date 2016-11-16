package com.flipkart.connekt.commons.iomodels
import scala.collection.mutable

case class EmailPayload(to: Set[EmailAddress], cc: Set[EmailAddress], bcc: Set[EmailAddress], data: EmailRequestData, from: EmailAddress, replyTo:EmailAddress)

abstract class PayloadEnvelope{
  def provider:mutable.ListBuffer[String]
}

case class EmailPayloadEnvelope(messageId: String, appName: String, contextId: String, clientId: String, payload: EmailPayload, meta: Map[String, Any], provider:mutable.ListBuffer[String] = mutable.ListBuffer[String]()) extends PayloadEnvelope
