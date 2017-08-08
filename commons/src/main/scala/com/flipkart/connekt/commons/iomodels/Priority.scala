/*
 *         -╥⌐⌐⌐⌐            -⌐⌐⌐⌐-
 *      ≡╢░░░░⌐\░░░φ     ╓╝░░░░⌐░░░░╪╕
 *     ╣╬░░`    `░░░╢┘ φ▒╣╬╝╜     ░░╢╣Q
 *    ║╣╬░⌐        ` ╤▒▒▒Å`        ║╢╬╣
 *    ╚╣╬░⌐        ╔▒▒▒▒`«╕        ╢╢╣▒
 *     ╫╬░░╖    .░ ╙╨╨  ╣╣╬░φ    ╓φ░╢╢Å
 *      ╙╢░░░░⌐"░░░╜     ╙Å░░░░⌐░░░░╝`
 *        ``˚¬ ⌐              ˚˚⌐´
 *
 *      Copyright © 2016 Flipkart.com
 */
package com.flipkart.connekt.commons.iomodels

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonSerializer, SerializerProvider}
import com.flipkart.connekt.commons.entities.Channel

object Priority extends Enumeration {

  val HIGH = Value("high")
  val NORMAL = Value("normal")
}

class PriorityType extends TypeReference[Priority.type]

class PriorityStringSerializer extends JsonSerializer[Priority.Value] {
  override def serialize(t: Priority.Value, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider) = {
    jsonGenerator.writeObject(t.toString)
  }
}

class PriorityStringDeserializer extends JsonDeserializer[Priority.Value] {
  @Override
  override def deserialize(parser:JsonParser, context:DeserializationContext):Priority.Value={
    try {
      Priority.withName(parser.getValueAsString.toLowerCase)
    } catch {
      case e: NoSuchElementException =>
        null
    }
  }
}
