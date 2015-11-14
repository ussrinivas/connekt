package com.flipkart.connekt.commons.factories

import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

/**
 *
 *
 * @author durga.s
 * @version 11/20/15
 */
object ConnektLogger {
  def init(logConfFilePath: String) = LoggerFactoryConfigurator.configure(logConfFilePath)

  def stop() = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext].stop()

  def apply(logFile: LogFile.Value) = LoggerFactory.getLogger(logFile.toString)
}

object LogFile extends Enumeration {
  type LogFile = Value

  val FACTORY = Value("FACTORY")
  val SERVICE = Value("SERVICE")
  val DAO = Value("DAO")
}
