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

import java.util.Date
import javax.persistence.Column

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonSerializer, SerializerProvider}
import com.flipkart.connekt.commons.entities.ConfigFormat.ConfigFormat
import com.flipkart.connekt.commons.utils.StringUtils._
import org.apache.commons.lang.StringUtils

class UserProjectConfig {

  @Column(name = "appName")
  var appName: String = _

  @Column(name = "name")
  var name: String = _

  @Column(name = "value")
  var value: String = StringUtils.EMPTY

  @EnumTypeHint(value = "com.flipkart.connekt.commons.entities.ConfigFormat")
  @Column(name = "format")
  @JsonSerialize(using = classOf[FormatToStringSerializer])
  @JsonDeserialize(using = classOf[FormatToStringDeserializer])
  var format: ConfigFormat.ConfigFormat = ConfigFormat.STRING

  @Column(name = "lastUpdatedTS")
  var lastUpdatedTs: Date = new Date(System.currentTimeMillis())

  @Column(name = "updatedBy")
  var updatedBy: String = StringUtils.EMPTY

  @Column(name = "creationTS")
  var creationTS: Date = new Date(System.currentTimeMillis())

  @Column(name = "createdBy")
  var createdBy: String = StringUtils.EMPTY

  override def toString = s"UserApplicationConfig($appName, $name, $value)"

  def validate() = {
    require(name.isDefined, "`name` must be defined.")
    require(!com.flipkart.connekt.commons.utils.StringUtils.isNullOrEmpty(value), "`value` must be defined.")
    require(!com.flipkart.connekt.commons.utils.StringUtils.isNullOrEmpty(format), "`format` cannot be null.")
  }
}

object ConfigFormat extends Enumeration {
  type ConfigFormat = Value
  val STRING, JSON = Value
}

class FormatToStringSerializer extends JsonSerializer[ConfigFormat] {
  override def serialize(t: ConfigFormat.Value, jsonGenerator: JsonGenerator, serializerProvider: SerializerProvider) = {
    jsonGenerator.writeObject(t.toString)
  }
}

class FormatToStringDeserializer extends JsonDeserializer[ConfigFormat] {
  @Override
  override def deserialize(parser: JsonParser, context: DeserializationContext): ConfigFormat.Value = {
    try {
      ConfigFormat.withName(parser.getValueAsString.toUpperCase)
    } catch {
      case e: NoSuchElementException =>
        null
    }
  }
}
