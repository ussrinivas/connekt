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

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonProperty, JsonSubTypes, JsonTypeInfo}

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type"
)
@JsonSubTypes(Array(
  new Type(value = classOf[HSMWaPayload], name = "HSM"),
  new Type(value = classOf[PDFWaPayload], name = "PDF")
))
abstract class WaPayload (
                       @JsonProperty(required = true) to: String
                     )


case class HSMWaPayload(
                         @JsonProperty(required = true) hsm: HsmData
                       )

case class HsmData(
                    @JsonProperty(required = true) namespace: String,
                    @JsonProperty(required = true) element_name: String,
                    @JsonProperty(required = false) fallback_lg: String = "en",
                    @JsonProperty(required = false) fallback_lc: String = "US",
                    @JsonProperty(required = true) localizable_params: List[Map[String, String]]
                  )

case class PDFWaPayload (
                          @JsonProperty(required = true) document: DocumentData
                        )

case class DocumentData (
                          @JsonProperty(required = true) filename: String,
                          @JsonProperty(required = true) caption: String
                        )
