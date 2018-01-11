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
package com.flipkart.connekt.commons.helpers

import com.flipkart.connekt.commons.iomodels._
import com.roundeights.hasher.Implicits._

object ConnektRequestHelper {

  abstract class FlatConnektRequest

  implicit class FlatRequest(request: ConnektRequest) extends FlatConnektRequest {

    def id = request.id

    def destinations: Set[String] = request.channelInfo match {
      case pn: PNRequestInfo => pn.deviceIds
      case email: EmailRequestInfo => email.to.map(_.address) ++ Option(email.cc).getOrElse(Set.empty).map(_.address) ++ Option(email.bcc).getOrElse(Set.empty).map(_.address)
      case sms: SmsRequestInfo => sms.receivers
      case wa: WARequestInfo => wa.destinations
      case _ => null
    }

    def kafkaKey: String = request.channelInfo match {
      case _: WARequestInfo => destinations.toString.sha256.hash.hex
      case _ => request.id
    }


    def appName: String = request.channelInfo match {
      case pn: PNRequestInfo => pn.appName
      case email: EmailRequestInfo => email.appName
      case sms: SmsRequestInfo => sms.appName
      case pull: PullRequestInfo => pull.appName
      case wa: WARequestInfo => wa.appName
      case _ => null
    }

    def platform = request.channelInfo match {
      case pn: PNRequestInfo => pn.platform
      case _ => null
    }
  }

}
