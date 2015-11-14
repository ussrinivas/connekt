package com.flipkart.connekt.commons.factories

import com.flipkart.connekt.commons.behaviors.HTableFactory
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.{HConnectionManager, HConnection, HTableInterface}

/**
 *
 *
 * @author durga.s
 * @version 11/16/15
 */
class HTableFactoryWrapper(hConnConfig: Configuration) extends HTableFactory {

  val hConnectionConfig = hConnConfig
  var hConnection: HConnection = HConnectionManager.createConnection(hConnectionConfig)

  override def shutdown(): Unit = hConnection.close()

  override def getTableInterface(tableName: String): HTableInterface = hConnection.getTable(tableName)

  override def releaseTableInterface(hTableInterface: HTableInterface): Unit = hTableInterface.close()
}
