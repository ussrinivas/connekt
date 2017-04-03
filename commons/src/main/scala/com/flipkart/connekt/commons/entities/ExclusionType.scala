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

import com.fasterxml.jackson.core.`type`.TypeReference

import scala.concurrent.duration.{Duration, DurationInt}

class ExclusionTypeSeDeserialize extends TypeReference[ExclusionType.type]

object ExclusionType extends Enumeration {

  type ExclusionType = Value

  val BLACKLIST = Value("blacklist")
  val SHORT_TERM = Value("shortterm")
  val LONG_TERM = Value("longterm")
  val INTERMEDIATE_TERM = Value("intermediateterm")
  val SPAM = Value("spam")

  def ttl(eT: ExclusionType.Value): Duration = eT match {
    case ExclusionType.BLACKLIST => Duration.Inf
    case ExclusionType.LONG_TERM => 90.days
    case ExclusionType.INTERMEDIATE_TERM => 30.days
    case ExclusionType.SHORT_TERM => 3.days
    case ExclusionType.SPAM => Duration.Inf
    case _ => Duration.Inf
  }
}
