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

import com.flipkart.connekt.commons.entities.UserProjectConfig
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, TMySQLFactory}
import org.springframework.dao.{DataAccessException, IncorrectResultSizeDataAccessException}

class UserProjectConfigDao(table: String, mysqlFactory: TMySQLFactory) extends MySQLDao {

  private val mysqlHelper = mysqlFactory

  def addProjectConfiguration(upc: UserProjectConfig):Unit = {
    implicit val j = mysqlHelper.getJDBCInterface
    val q =
      s"""
         |INSERT INTO $table(appName, name, value, format,  updatedBy, createdBy) VALUES(?, ?, ?, ?, ? , ? )
         |ON DUPLICATE KEY UPDATE  value = ?, format = ?, updatedBy = ?
      """.stripMargin

    try {
      update(q, upc.appName, upc.name, upc.value, upc.format.toString, upc.updatedBy, upc.createdBy, upc.value, upc.format.toString, upc.updatedBy)
    } catch {
      case e: DataAccessException =>
        ConnektLogger(LogFile.DAO).error(s"Error adding UserProjectConfig [$upc] info: ${e.getMessage}", e)
        throw e
    }
  }

  def getProjectConfigurations(appName: String): List[UserProjectConfig] = {
    implicit val j = mysqlHelper.getJDBCInterface
    val q =
      s"""
         |SELECT * FROM $table WHERE appName = ?
      """.stripMargin

    try {
      queryForList[UserProjectConfig](q, appName)
    } catch {
      case e@(_: IncorrectResultSizeDataAccessException | _: DataAccessException) =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching allUserChannelConfiguration for appName [$appName] info: ${e.getMessage}", e)
        throw e
    }
  }

  def getProjectConfiguration(appName: String, name:String): Option[UserProjectConfig] = {
    implicit val j = mysqlHelper.getJDBCInterface
    val q =
      s"""
         |SELECT * FROM $table WHERE appName = ? and name = ?
      """.stripMargin

    try {
      queryForList[UserProjectConfig](q, appName, name).headOption
    } catch {
      case e@(_: IncorrectResultSizeDataAccessException | _: DataAccessException) =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching getAppLevelConfiguration for [$appName/$name] info: ${e.getMessage}", e)
        throw e
    }
  }
}

object UserProjectConfigDao {

  def apply(tableName: String = "APP_CONFIG", mysqlFactory: TMySQLFactory) =
    new UserProjectConfigDao(tableName, mysqlFactory)

}
