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
import org.apache.commons.lang.StringUtils

case class WAContactRequest(@JsonProperty payload: Payload)

case class Payload(@JsonProperty blocking: String = "wait",
                   @JsonProperty(required = true) users: Set[String])

case class WAMetaData(@JsonProperty(required = true) domain: String,
                      @JsonProperty(required = true) bucket: String,
                      @JsonProperty(required = true) subBucket: String,
                      source: String = StringUtils.EMPTY,
                      accountId: String = StringUtils.EMPTY)
