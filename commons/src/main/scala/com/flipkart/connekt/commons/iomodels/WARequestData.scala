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
                         mime: String,
                         caption: Option[String] = None
                       )

case class WARequestData(
                          waType: WAType,
                          message: Option[String] = None,
                          attachments: List[WAAttachment]
                        ) extends ChannelRequestData {

  def validate(appName: String)(implicit stencilService: TStencilService): Unit = {
    attachments.foreach(a => {
      require(a.attachmentType != null)
      a.attachmentType match {
        case AttachmentType.document | AttachmentType.image => require(attachments.nonEmpty, "attachment is required")
        case _ => None
      }
    })
  }
}
