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

import com.flipkart.connekt.commons.services.TStencilService

case class WAAttachment(
                         attachmentType: AttachmentType,
                         base64Data: String,
                         name: String,
                         caption: String
                       )

case class WARequestData(
                          waType: WAType,
                          message: Option[String] = None,
                          attachments: List[WAAttachment]
                        ) extends ChannelRequestData {

  def validate(appName: String)(implicit stencilService: TStencilService): Unit = {
    require(waType != null)
    waType match {
      case WAType.text => require(message.isDefined, "message is required")
      case WAType.media => require(attachments != null && attachments.nonEmpty, "attachment is required")
      case _ => None
    }
  }
}
