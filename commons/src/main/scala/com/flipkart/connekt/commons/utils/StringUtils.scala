package com.flipkart.connekt.commons.utils

import org.apache.commons.codec.CharEncoding

/**
 *
 *
 * @author durga.s
 * @version 11/23/15
 */
object StringUtils {

  implicit class StringHandyFunctions(val s: String) {
    def getUtf8Bytes = s.getBytes(CharEncoding.UTF_8)
  }
}
