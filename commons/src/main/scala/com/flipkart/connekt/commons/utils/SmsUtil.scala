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

object SmsUtil {

  val GSM_CHARSET_7BIT: Int = 0
  val GSM_CHARSET_UNICODE: Int = 2
  private val GSM_7BIT_ESC: Char = '\u001b'
  private val GSM7BIT: Set[String] = Set("@", "£", "$", "¥", "è", "é", "ù", "ì", "ò", "Ç", "\n", "Ø", "ø", "\r", "Å", "å",
    "Δ", "_", "Φ", "Γ", "Λ", "Ω", "Π", "Ψ", "Σ", "Θ", "Ξ", "\u001b", "Æ", "æ", "ß", "É", " ", "!", "'", "#", "¤", "%", "&",
    "\"", "(", ")", "*", "+", ",", "-", ".", "/", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ":", ";", "<", "=",
    ">", "?", "¡", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
    "V", "W", "X", "Y", "Z", "Ä", "Ö", "Ñ", "Ü", "§", "¿", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
    "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "ä", "ö", "ñ", "ü", "à")

  private val GSM7BITEXT: Set[String] = Set("\f", "^", "{", "}", "\\", "[", "~", "]", "|", "€")

  def getCharset(content: String): Int = {
    val isNotUnicode = content.forall(b => GSM7BIT.contains(b.toString) || GSM7BITEXT.contains(b.toString))
    if (isNotUnicode)
      GSM_CHARSET_7BIT
    else
      GSM_CHARSET_UNICODE
  }

  private def getPartCount7bit(content: String): Int = {
    val content7bit: StringBuilder = new StringBuilder
    content.foreach(c => {
      if (GSM7BITEXT.contains(c.toString)) {
        content7bit.append('\u001b')
      }
      content7bit.append(c)
    })
    val cLen = content7bit.length

    cLen match {
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
  }

  def getPartCount(content: String): Int = {
    getCharset(content) match {
      case GSM_CHARSET_7BIT => getPartCount7bit(content)
      case GSM_CHARSET_UNICODE =>
        if (content.length <= 70)
          1
        else
          Math.ceil(content.length / 67.0).toInt
    }
  }

  def isUnicode(content: String): Boolean = {
    getCharset(content) match {
      case GSM_CHARSET_7BIT => false
      case GSM_CHARSET_UNICODE => true
    }
  }
}
