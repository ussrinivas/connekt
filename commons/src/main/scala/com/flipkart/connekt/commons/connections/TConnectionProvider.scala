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
package com.flipkart.connekt.commons.connections

import java.util.Properties
import javax.sql.DataSource

import com.aerospike.client.async.AsyncClient
import com.couchbase.client.java.Cluster
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.Connection

abstract class TConnectionProvider {

  def createCouchBaseConnection(nodes: List[String]): Cluster

  def createAeroSpikeConnection(nodes: List[String]): AsyncClient

  def createHbaseConnection(hConnConfig: Configuration) : Connection

  def createDatasourceConnection(mySQLProperties: Properties): DataSource
}
