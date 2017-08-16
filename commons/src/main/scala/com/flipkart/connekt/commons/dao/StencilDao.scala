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

import com.flipkart.connekt.commons.entities.{Bucket, Stencil, StencilsEnsemble}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, TMySQLFactory}

class StencilDao(tableName: String, historyTableName: String, stencilComponentsTable: String, bucketRegistryTable: String, jdbcHelper: TMySQLFactory) extends TStencilDao with MySQLDao {

  val mysqlHelper = jdbcHelper

  override def getStencils(id: String, version: Option[String] = None): List[Stencil] = {
    implicit val j = mysqlHelper.getJDBCInterface
    try {
      val q1 =
        s"""
           |SELECT * FROM $historyTableName WHERE id = ? and version = ?
            """.stripMargin

      val q2 =
        s"""
           |SELECT * FROM $tableName WHERE id = ?
            """.stripMargin

      version.map(queryForList[Stencil](q1, id, _)).getOrElse(queryForList[Stencil](q2, id))
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching stencil [$id] ${e.getMessage}", e)
        throw e
    }
  }

  override def getStencilsByName(name: String, version: Option[String] = None): List[Stencil] = {
    implicit val j = mysqlHelper.getJDBCInterface
    try {
      val q1 =
        s"""
           |SELECT * FROM $historyTableName WHERE name = ? and version = ?
            """.stripMargin

      val q2 =
        s"""
           |SELECT * FROM $tableName WHERE name = ?
            """.stripMargin

      version.map(queryForList[Stencil](q1, name, _)).getOrElse(queryForList[Stencil](q2, name))
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching getStencilsByName [$name] ${e.getMessage}", e)
        throw e
    }
  }

  override def getStencilsByBucket(name: String): List[Stencil] = {
    implicit val j = mysqlHelper.getJDBCInterface
    try {
      val q2 = s"SELECT * FROM $tableName WHERE bucket = ?"
      queryForList[Stencil](q2, name)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching getStencilsByBucket [$name] ${e.getMessage}", e)
        throw e
    }
  }

  override def writeStencil(stencil: Stencil): Unit = {
    implicit val j = mysqlHelper.getJDBCInterface
    j.execute("START TRANSACTION")
    val q1 =
      s"""
         |INSERT INTO $tableName (id, name, component, type, engine, engineFabric, createdBy, updatedBy, version, bucket) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         |ON DUPLICATE KEY UPDATE name = ?, engine = ?, engineFabric = ?, updatedBy = ?, version = version + 1, bucket = ?
      """.stripMargin

    val q2 =
      s"""
         |INSERT INTO $historyTableName (id, name, component, type, engine, engineFabric, createdBy, updatedBy, version, bucket) VALUES(?, ?, ?, ?, ?, ?, ?, ? ,(SELECT version FROM $tableName WHERE id = ? AND component = ? AND name = ?), ?)
      """.stripMargin

    try {
      update(q1, stencil.id, stencil.name, stencil.component, stencil.`type`, stencil.engine.toString, stencil.engineFabric, stencil.createdBy, stencil.updatedBy, stencil.version.toString, stencil.bucket, stencil.name, stencil.engine.toString, stencil.engineFabric, stencil.updatedBy, stencil.bucket)
      update(q2, stencil.id, stencil.name, stencil.component, stencil.`type`, stencil.engine.toString, stencil.engineFabric, stencil.updatedBy, stencil.updatedBy, stencil.id, stencil.component, stencil.name, stencil.bucket)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error updating stencil [${stencil.id}] ${e.getMessage}", e)
        j.execute("ROLLBACK")
        throw e
    }
    j.execute("COMMIT")
  }


  override def deleteStencilByName(name: String, id:String): Unit = {
    implicit val j = mysqlHelper.getJDBCInterface
    try {
      val q = s"DELETE FROM $tableName WHERE name = ? and id = ?"
      update(q, name, id)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error deleting stencilByName [$name / $id] ${e.getMessage}", e)
        throw e
    }
  }

  override def deleteStencil(id:String): Unit = {
    implicit val j = mysqlHelper.getJDBCInterface
    try {
      val q = s"DELETE FROM $tableName WHERE id = ?"
      update(q, id)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error deleting bucket [$id] ${e.getMessage}", e)
        throw e
    }
  }

  override def getBucket(name: String): Option[Bucket] = {
    implicit val j = mysqlHelper.getJDBCInterface

    try {
      val q =
        s"""
           |SELECT * FROM $bucketRegistryTable WHERE name = ?
            """.stripMargin
      query[Bucket](q, name)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching bucket [$name] ${e.getMessage}", e)
        throw e
    }
  }

  override def getStencilsEnsemble(id: String): Option[StencilsEnsemble] = {
    implicit val j = mysqlHelper.getJDBCInterface

    try {
      val q =
        s"""
           |SELECT * FROM $stencilComponentsTable WHERE id = ?
            """.stripMargin
      query[StencilsEnsemble](q, id)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching stencil type [$id] ${e.getMessage}", e)
        throw e
    }
  }

  override def getStencilsEnsembleByName(name: String): Option[StencilsEnsemble] = {
    implicit val j = mysqlHelper.getJDBCInterface

    try {
      val q =
        s"""
           |SELECT * FROM $stencilComponentsTable WHERE name = ?
            """.stripMargin
      query[StencilsEnsemble](q, name)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error fetching stencil type [$name] ${e.getMessage}", e)
        throw e
    }
  }


  override def writeStencilsEnsemble(stencilComponents: StencilsEnsemble): Unit = {
    implicit val j = mysqlHelper.getJDBCInterface
    val q =
      s"""
         |INSERT INTO $stencilComponentsTable (id, name, components, createdBy, updatedBy) VALUES(?, ? , ? , ? , ?)
         |ON DUPLICATE KEY UPDATE name = ?,components = ?, updatedBy = ?
      """.stripMargin

    try {
      update(q, stencilComponents.id, stencilComponents.name, stencilComponents.components, stencilComponents.createdBy, stencilComponents.updatedBy, stencilComponents.name, stencilComponents.components, stencilComponents.updatedBy)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error updating stencil type [${stencilComponents.name}}] ${e.getMessage}", e)
        throw e
    }
  }

  override def writeBucket(bucket: Bucket): Unit = {
    implicit val j = mysqlHelper.getJDBCInterface
    val q =
      s"""
         |INSERT INTO $bucketRegistryTable (id, name) VALUES(?, ?)
      """.stripMargin

    try {
      update(q, bucket.id, bucket.name)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.DAO).error(s"Error updating stencil [${bucket.name}}] ${e.getMessage}", e)
        throw e
    }
  }

  override def getAllEnsemble(): List[StencilsEnsemble] = {
    implicit val j = mysqlHelper.getJDBCInterface
    val q =
      s"""
         |SELECT * from STENCILS_ENSEMBLE
       """.stripMargin
    queryForList[StencilsEnsemble](q)
  }

}

object StencilDao {
  def apply(tableName: String, historyTableName: String, stencilComponentsTable: String, bucketRegistryTable: String, jdbcHelper: TMySQLFactory) =
    new StencilDao(tableName: String, historyTableName: String, stencilComponentsTable: String, bucketRegistryTable: String, jdbcHelper: TMySQLFactory)
}
