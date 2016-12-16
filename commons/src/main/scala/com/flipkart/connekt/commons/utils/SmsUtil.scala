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
package com.flipkart.connekt.commons.utils

import java.nio.charset.Charset

import com.flipkart.connekt.commons.iomodels.SmsMeta
import org.smpp.charset.Gsm7BitCharsetProvider

object SmsUtil {

  val GSM_CHARSET = new Gsm7BitCharsetProvider().charsetForName("X-Gsm7Bit")
  val USC2_CHARSET: Charset = Charset.forName("UTF-16")

  private val gsm7Bit = GSM_CHARSET.newEncoder()

  private val GSM7BITEXT: Set[String] = Set("\f", "^", "{", "}", "\\", "[", "~", "]", "|", "€")
  private val GSM_7BIT_ESC: Char = '\u001b'

  def isUnicode(charset: Charset): Boolean = {
    charset match {
      case GSM_CHARSET => false
      case _ => true
    }
  }

  def getCharset(content: String): Charset = {
    if (gsm7Bit.canEncode(content))
      GSM_CHARSET
    else
      USC2_CHARSET
  }

  // To calculate lenght.
  private def getPartCountAndLength7bit(content: String): Int = {

    val content7bit: StringBuilder = new StringBuilder
    content.foreach(c => {
      if (GSM7BITEXT.contains(c.toString)) {
        content7bit.append(GSM_7BIT_ESC)
      }
      content7bit.append(c)
    })

    val cLen = content7bit.toString().length

    val smsParts = cLen match {
      case x if x <= 160 => 1
      case _ =>
        val parts: Int = Math.ceil(cLen / 153.0).toInt
        val freeChars: Int = cLen - Math.floor(cLen / 153.0).toInt * 153
        if (freeChars >= parts - 1)
          parts
        else {
          var countParts = 0
          content7bit.map(c => {
            countParts += 1
            if (content7bit.length >= 152 && content7bit.charAt(152) == GSM_7BIT_ESC) content7bit.delete(0, 152)
            else content7bit.delete(0, 153)
          })
          countParts
        }
    }
    smsParts
  }

  def getSmsInfo(content: String): SmsMeta = {
    val charset = getCharset(content)
    val smsParts = charset match {
      case GSM_CHARSET => getPartCountAndLength7bit(content)
      case USC2_CHARSET =>
        if (content.length <= 70)
          1
        else
          Math.ceil(content.length / 67.0).toInt
    }
    SmsMeta(smsParts, charset)
  }
}
