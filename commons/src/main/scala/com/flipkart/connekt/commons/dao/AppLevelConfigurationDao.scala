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
package com.flipkart.connekt.commons.dao

import com.flipkart.connekt.commons.entities.AppLevelConfig
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, TMySQLFactory}
import com.flipkart.connekt.commons.utils.StringUtils._
import org.springframework.dao.{DataAccessException, IncorrectResultSizeDataAccessException}

class AppLevelConfigurationDao(table: String, mysqlFactory: TMySQLFactory) extends TAppLevelConfiguration with MySQLDao {

  val mysqlHelper = mysqlFactory

  override def addAppLevelConfiguration(ucfg: AppLevelConfig) = {
    implicit val j = mysqlHelper.getJDBCInterface
    val q =
      s"""
         |INSERT INTO $table(appName, config, value, format, channel, updatedBy, createdBy) VALUES(?, ?, ?, ?, ? , ?, ?)
         |ON DUPLICATE KEY UPDATE  value = ?, format = ?, updatedBy = ?
      """.stripMargin

    try {
      update(q, ucfg.appName, ucfg.config, ucfg.value, ucfg.format.toString, ucfg.channel.toString, ucfg.updatedBy, ucfg.createdBy, ucfg.value, ucfg.format.toString, ucfg.updatedBy)
    } catch {
      case e: DataAccessException =>
        ConnektLogger(LogFile.DAO).error(s"Error adding UserChannelConfiguration [${ucfg.getJson}] info: ${e.getMessage}", e)
        throw e
    }
  }

  override def getAllAppLevelConfiguration(appName: String, channel: Channel): List[AppLevelConfig] = {
    implicit val j = mysqlHelper.getJDBCInterface
    val q =
      s"""
         |SELECT * FROM $table WHERE appName = ? and channel = ?
      """.stripMargin

    try {
      queryForList[AppLevelConfig](q, appName, channel.toString)
    } catch {
      case e@(_: IncorrectResultSizeDataAccessException | _: DataAccessException) =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching allUserChannelConfiguration for appName [$appName] and channel [$channel] info: ${e.getMessage}", e)
        throw e
    }
  }

  override def getAllChannelLevelConfiguration(channel: Channel): List[AppLevelConfig] = {
    implicit val j = mysqlHelper.getJDBCInterface
    val q =
      s"""
         |SELECT * FROM $table WHERE channel = ?
      """.stripMargin

    try {
      queryForList[AppLevelConfig](q, channel.toString)
    } catch {
      case e@(_: IncorrectResultSizeDataAccessException | _: DataAccessException) =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching allUserChannelConfiguration for channel [$channel] info: ${e.getMessage}", e)
        throw e
    }
  }
}

object AppLevelConfigurationDao {

  def apply(tableName: String = "APP_LEVEL_CONFIG", mysqlFactory: TMySQLFactory) =
    new AppLevelConfigurationDao(tableName, mysqlFactory)

}
