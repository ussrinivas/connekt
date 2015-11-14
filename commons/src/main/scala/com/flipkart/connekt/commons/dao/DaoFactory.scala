package com.flipkart.connekt.commons.dao

import com.flipkart.connekt.commons.behaviors.HTableFactory
import com.flipkart.connekt.commons.services.HConnectionHelper
import com.typesafe.config.Config

/**
 *
 *
 * @author durga.s
 * @version 11/23/15
 */
object DaoFactory {

  var daoMap = Map[DaoType.Value, Dao]()
  var hTableFactory: HTableFactory = null

  def initHTableDaoFactory(hConnectionConfig: Config) = {
    hTableFactory = HConnectionHelper.createHbaseConnection(hConnectionConfig)
    daoMap += DaoType.DEVICE_DETAILS -> DeviceDetailsDao("fk-connekt-proto", hTableFactory)
  }

  def shutdownHTableDaoFactory() = {
    Option(hTableFactory).foreach(_.shutdown())
  }

  def initMysqlTableDaoFactory(mysqlConnectionConfig: Config) = ???

  def getDeviceDetailsDao: DeviceDetailsDao = daoMap(DaoType.DEVICE_DETAILS).asInstanceOf[DeviceDetailsDao]
}

object DaoType extends Enumeration {
  val DEVICE_DETAILS, REQUEST_META = Value
}

