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

import com.fasterxml.jackson.annotation.JsonProperty

case class WARequest(
                      @JsonProperty(required = true) payload: WAPayload
                    )

trait WAPayload

case class WAMediaPayload(
                           @JsonProperty(required = true) filename: String
                         ) extends WAPayload

abstract class WASendPayload extends WAPayload{
  def to:String
}

case class HSMWAPayload(
                         @JsonProperty(required = true) hsm: HsmData,
                         @JsonProperty(required = true) to: String
                       ) extends WASendPayload

//TODO: Need a relook at fallback_lc BE REMOVED
case class HsmData(
                    @JsonProperty(required = true) namespace: String,
                    @JsonProperty(required = true) element_name: String,
                    @JsonProperty(required = false) fallback_lg: String = "en",
                    @JsonProperty(required = false) fallback_lc: String = "US",
                    @JsonProperty(required = true) localizable_params: List[Map[String, String]]
                  )

case class TxtWAPayload(
                         @JsonProperty(required = true) body: String,
                         @JsonProperty(required = true) to: String
                       ) extends WASendPayload

case class DocumentWAPayload(
                              @JsonProperty(required = true) document: FileData,
                              @JsonProperty(required = true) to: String
                            ) extends WASendPayload

case class ImageWAPayload(
                           @JsonProperty(required = true) image: FileData,
                           @JsonProperty(required = true) to: String
                         ) extends WASendPayload

case class FileData(
                     @JsonProperty(required = true) filename: String,
                     @JsonProperty(required = false) caption: String
                   )
