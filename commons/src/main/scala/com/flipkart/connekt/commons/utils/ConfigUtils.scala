package com.flipkart.connekt.commons.utils

import com.typesafe.config.{ConfigFactory, ConfigException, Config}

/**
 *
 *
 * @author durga.s
 * @version 11/26/15
 */
object ConfigUtils {

  implicit class configHandyFunctions(val c: Config) {
    def getOrElse[T](key: String, default: T) = try {
      c.getAnyRef(key).asInstanceOf[T]
    } catch {
      case e: ConfigException.Missing => default
    }
  }

  val emptyConf = ConfigFactory.empty()

  implicit class optConfigHelpers(val c: Option[Config]) {
    def getValueOrElse[T](key: String, default: T): T =
      c.getOrElse(emptyConf).getOrElse[T](key, default)
  }
}
