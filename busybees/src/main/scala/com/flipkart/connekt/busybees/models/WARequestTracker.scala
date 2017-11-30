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
package com.flipkart.connekt.busybees.models

import com.flipkart.connekt.commons.iomodels.ConnektRequest

case class WARequestTracker(
                             override val messageId: String,
                             clientId: String,
                             destination: String,
                             appName: String,
                             contextId: String,
                             meta: Map[String, Any],
                             override val provider: String = "whatsapp"
                            ) extends RequestTracker

case class WAMediaRequestTracker(
                                  override val messageId: String,
                                  request: ConnektRequest,
                                  override val provider: String = "whatsapp"
                            ) extends RequestTracker
