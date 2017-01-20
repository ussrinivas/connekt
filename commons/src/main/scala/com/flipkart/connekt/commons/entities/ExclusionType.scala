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
  val SHORT_TERM_FAILURE = Value("shortTermFailure")
  val LONG_TERM_FAILURE = Value("longTermFailure")
  val INTERMEDIATE_TERM_FAILURE = Value("intermediateTermFailure")
  val SPAM = Value("spam")

  def ttl(eT: ExclusionType.Value): Duration = eT match {
    case ExclusionType.BLACKLIST => Duration.Inf
    case ExclusionType.LONG_TERM_FAILURE => 90.days
    case ExclusionType.INTERMEDIATE_TERM_FAILURE => 30.days
    case ExclusionType.SHORT_TERM_FAILURE => 3.days
    case ExclusionType.SPAM => Duration.Inf
    case _ => Duration.Inf
  }

  def getAll: List[ExclusionType] = List(BLACKLIST, LONG_TERM_FAILURE, INTERMEDIATE_TERM_FAILURE, SHORT_TERM_FAILURE, SPAM)
}
