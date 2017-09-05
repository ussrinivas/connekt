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
package com.flipkart.connekt.receptors

import java.util.concurrent.atomic.AtomicBoolean

import com.flipkart.connekt.commons.connections.ConnectionProvider
import com.flipkart.connekt.commons.core.BaseApp
import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.KafkaProducerHelper
import com.flipkart.connekt.commons.services.{ConnektConfig, EventsDaoContainer, RequestDaoContainer}
import com.flipkart.connekt.commons.sync.SyncManager
import com.flipkart.connekt.commons.utils.ConfigUtils
import com.flipkart.connekt.receptors.service.ReceptorsServer
import com.typesafe.config.ConfigFactory

object ReceptorsBoot extends BaseApp {

  private val initialized = new AtomicBoolean(false)


  def start() {
    if (!initialized.getAndSet(true)) {

      ConnektLogger(LogFile.SERVICE).info("Receptors initializing.")

      val loggerConfigFile = ConfigUtils.getSystemProperty("log4j.configurationFile").getOrElse("log4j2-receptors.xml")

      ConnektLogger(LogFile.SERVICE).info(s"Receptors Logging using $loggerConfigFile")
      ConnektLogger.init(loggerConfigFile)

      val applicationConfigFile = ConfigUtils.getSystemProperty("receptors.appConfigurationFile").getOrElse("receptors-config.json")
      ConnektConfig(configServiceHost, configServicePort, apiVersion)(Seq("fk-connekt-root", "fk-connekt-".concat(ConfigUtils.getConfEnvironment), "fk-connekt-receptors", "fk-connekt-barklice", "fk-connekt-receptors-akka"))(applicationConfigFile)

      SyncManager.create(ConnektConfig.getString("sync.zookeeper").get)

      DaoFactory.setUpConnectionProvider(new ConnectionProvider())

      val hConfig = ConnektConfig.getConfig("connections.hbase")
      DaoFactory.initHTableDaoFactory(hConfig.get)

      val mysqlConf = ConnektConfig.getConfig("connections.mysql").getOrElse(ConfigFactory.empty())
      DaoFactory.initMysqlTableDaoFactory(mysqlConf)

      val couchbaseCf = ConnektConfig.getConfig("connections.couchbase").getOrElse(ConfigFactory.empty())
      DaoFactory.initCouchbaseCluster(couchbaseCf)

      val aeroSpikeCf = ConnektConfig.getConfig("connections.aerospike").getOrElse(ConfigFactory.empty())
      DaoFactory.initAeroSpike(aeroSpikeCf)

      DaoFactory.initReportingDao(DaoFactory.getCouchbaseBucket(ConnektConfig.getOrElse("couchbase.reporting.bucketname", "StatsReporting")))

      val kafkaConnConf = ConnektConfig.getConfig("connections.kafka.producerConnProps").getOrElse(ConfigFactory.empty())
      val kafkaProducerPoolConf = ConnektConfig.getConfig("connections.kafka.producerPool").getOrElse(ConfigFactory.empty())
      val kafkaProducerHelper = KafkaProducerHelper.init(kafkaConnConf, kafkaProducerPoolConf)

      ServiceFactory.initMessageQueueService(DaoFactory.getMessageQueueDao)
      ServiceFactory.initSchedulerService(DaoFactory.getHTableFactory.getConnection)
      ServiceFactory.initPNMessageService(DaoFactory.getPNRequestDao, DaoFactory.getUserConfigurationDao, kafkaProducerHelper, kafkaConnConf, ServiceFactory.getSchedulerService)
      ServiceFactory.initSMSMessageService(DaoFactory.getSmsRequestDao, DaoFactory.getUserConfigurationDao, kafkaProducerHelper, kafkaConnConf, ServiceFactory.getSchedulerService)

      ServiceFactory.initEmailMessageService(DaoFactory.getEmailRequestDao, DaoFactory.getUserConfigurationDao, kafkaProducerHelper, kafkaConnConf)

      val eventsDao = EventsDaoContainer(pnEventsDao = DaoFactory.getPNCallbackDao, emailEventsDao = DaoFactory.getEmailCallbackDao, smsEventsDao = DaoFactory.getSmsCallbackDao)
      val requestDao = RequestDaoContainer(smsRequestDao = DaoFactory.getSmsRequestDao, pnRequestDao = DaoFactory.getPNRequestDao, emailRequestDao = DaoFactory.getEmailRequestDao)
      ServiceFactory.initCallbackService(eventsDao, requestDao, kafkaProducerHelper)

      ServiceFactory.initAuthorisationService(DaoFactory.getPrivDao, DaoFactory.getUserInfoDao)
      ServiceFactory.initStorageService(DaoFactory.getKeyChainDao)
      ServiceFactory.initProjectConfigService(DaoFactory.getUserProjectConfigDao)
      ServiceFactory.initStatsReportingService(DaoFactory.getStatsReportingDao)
      ServiceFactory.initStencilService(DaoFactory.getStencilDao)

      //Start up the receptors
      ReceptorsServer(ConnektConfig.getConfig("react").get)

      ConnektLogger(LogFile.SERVICE).info("Started `Receptors` app")
    }
  }

  def terminate() = {
    ConnektLogger(LogFile.DAO).info("Receptors terminating.")
    if (initialized.get()) {
      ReceptorsServer.shutdown()
      DaoFactory.shutdownHTableDaoFactory()

      ConnektLogger.shutdown()
    }
  }

  def main(args: Array[String]) {
    System.setProperty("log4j.configurationFile", "log4j2-test.xml")
    start()
  }
}
