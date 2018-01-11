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
package com.flipkart.connekt.commons.entities

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.flipkart.connekt.commons.dao.JSONField

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type"
)
@JsonSubTypes(Array(
  new Type(value = classOf[HTTPEventSink], name = "HTTP"),
  new Type(value = classOf[HbaseEventSink], name = "HBASE"),
  new Type(value = classOf[KafkaEventSink], name = "KAFKA"),
  new Type(value = classOf[SpecterEventSink], name = "SPECTER"),
  new Type(value = classOf[RMQEventSink], name = "RMQ")
))
abstract class EventSink extends JSONField
case class HbaseEventSink(entityType: String) extends EventSink
case class HTTPEventSink(method:String, url: String) extends EventSink
case class KafkaEventSink(topic: String, broker: String) extends EventSink
case class SpecterEventSink() extends EventSink
case class RMQEventSink(queue: String, host: String, username: String, password: String) extends EventSink
