package com.flipkart.connekt.busybees.utils

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.util.{Base64, Properties}
import javax.activation.{DataHandler, DataSource}
import javax.mail.internet.{MimeBodyPart, MimeMessage, MimeMultipart}
import javax.mail.{Address, Message, Session}

import com.flipkart.connekt.commons.iomodels.{Attachment, EmailPayloadEnvelope}

object MailUtils {

  implicit class EMLCreator(val request: EmailPayloadEnvelope) {

    private val session = Session.getDefaultInstance(new Properties())
    session.setDebug(true)

    def toEML:String = {
      val msg = new MimeMessage(session)

      msg.setFrom(request.payload.from.toInternetAddress)
      msg.setReplyTo(Array(request.payload.replyTo.toInternetAddress))
      msg.setRecipients(Message.RecipientType.TO, request.payload.to.map(_.toInternetAddress.asInstanceOf[Address]).toArray)
      msg.setRecipients(Message.RecipientType.CC, request.payload.cc.map(_.toInternetAddress.asInstanceOf[Address]).toArray)
      msg.setRecipients(Message.RecipientType.BCC, request.payload.bcc.map(_.toInternetAddress.asInstanceOf[Address]).toArray)
      msg.setSubject(request.payload.data.subject)

      val multiPart = new MimeMultipart("alternative")

      val textPart = new MimeBodyPart()
      textPart.setText(request.payload.data.text, "utf-8")

      val htmlPart = new MimeBodyPart()
      htmlPart.setContent(request.payload.data.html, "text/html; charset=utf-8")

      multiPart.addBodyPart(htmlPart)
      multiPart.addBodyPart(textPart)

      //add attachments
      request.payload.data.attachments.foreach(file => {
        val attachmentPart = new MimeBodyPart()
        val byteDataSource = new AttachmentDataSource(file)
        attachmentPart.setDataHandler(new DataHandler(byteDataSource))
        attachmentPart.setFileName(file.name)
        multiPart.addBodyPart(attachmentPart)
      })

      msg.setContent(multiPart)

      val os = new ByteArrayOutputStream()
      msg.writeTo(os)

      new String(os.toByteArray,"UTF-8")
    }

  }


  class AttachmentDataSource(private val attachment: Attachment) extends DataSource {

    def getContentType: String = attachment.mime

    def getInputStream: InputStream =  new ByteArrayInputStream(Base64.getDecoder.decode(attachment.base64Data))

    def getName: String = attachment.name

    def getOutputStream: OutputStream = null

  }

}
