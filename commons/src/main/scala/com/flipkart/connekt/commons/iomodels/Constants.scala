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

import com.flipkart.connekt.commons.services.ConnektConfig

object Constants {

  object WAConstants extends Enumeration {
    type WAConstants = Value

    val WHATSAPP = Value("whatsapp")
    val WHATSAPP_CONTACTS = Value("wacontact")
    val WHATSAPP_CHECK_CONTACT_URI = Value("/api/check_contacts.php")
    val WA_CONTACT_QUEUE = Value(ConnektConfig.getString("wa.contact.topic.name").get)
  }

  object LatencyMeterConstants extends Enumeration {
    type LatencyMeterConstants = Value

    val LATENCY_METER = Value("latencymeter")
    val SMS_LATENCY_METER = Value("smslatencymeter")
    val WA_LATENCY_METER = Value("walatencymeter")
  }


}
