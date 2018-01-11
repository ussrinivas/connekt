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
package com.flipkart.connekt.commons.tests

import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.{KafkaConsumerHelper, KafkaProducerHelper}
import com.flipkart.connekt.commons.services.{ConnektConfig, EventsDaoContainer, RequestDaoContainer}
import com.flipkart.connekt.commons.sync.SyncManager
import com.flipkart.connekt.commons.tests.connections.MockConnectionProvider
import com.flipkart.connekt.commons.utils.ConfigUtils
import com.typesafe.config.ConfigFactory

class CommonsBaseTest extends ConnektUTSpec {

  val kafkaProducerHelper: Option[KafkaProducerHelper] = None
  val kafkaConsumerHelper: Option[KafkaConsumerHelper] = None

  override def beforeAll() = {
    super.beforeAll()
    bootstrapReceptors()
  }

  def getKafkaConsumerConf = ConnektConfig.getConfig("connections.kafka.consumerConnProps").get

  def getKafkaConsumerHelper = kafkaConsumerHelper.getOrElse({
    val kafkaConsumerConf = ConnektConfig.getConfig("connections.kafka.consumerConnProps").getOrElse(ConfigFactory.empty())
    val kafkaConsumerPoolConf = ConnektConfig.getConfig("connections.kafka.consumerPool").getOrElse(ConfigFactory.empty())
    ConnektLogger(LogFile.SERVICE).info(s"Kafka Conf: ${kafkaConsumerConf.toString}")
    KafkaConsumerHelper(kafkaConsumerConf, kafkaConsumerPoolConf)
  })

  def getKafkaProducerHelper = kafkaProducerHelper.getOrElse({
    val kafkaConnConf = ConnektConfig.getConfig("connections.kafka.producerConnProps").getOrElse(ConfigFactory.empty())
    val kafkaProducerPoolConf = ConnektConfig.getConfig("connections.kafka.producerPool").getOrElse(ConfigFactory.empty())
    KafkaProducerHelper.init(kafkaConnConf, kafkaProducerPoolConf)
  })

  protected def bootstrapReceptors() = {

    ConnektLogger(LogFile.SERVICE).info(s"Test config initializing, configServiceHost: $configServiceHost:$configServicePort")
    val applicationConfigFile = ConfigUtils.getSystemProperty("receptors.appConfigurationFile").getOrElse("receptors-config.yaml")
    ConnektConfig(configServiceHost, configServicePort, apiVersion)(Seq("fk-connekt-root", "fk-connekt-".concat(ConfigUtils.getConfEnvironment), "fk-connekt-receptors", "fk-connekt-busybees", "fk-connekt-busybees-akka", "fk-connekt-firefly"))(applicationConfigFile)
    SyncManager.create(ConnektConfig.getString("sync.zookeeper").get)

    DaoFactory.setUpConnectionProvider(new MockConnectionProvider())

    val hConfig = ConnektConfig.getConfig("connections.hbase")

    DaoFactory.initHTableDaoFactory(hConfig.get)

    val mysqlConf = ConnektConfig.getConfig("connections.mysql").getOrElse(ConfigFactory.empty())
    DaoFactory.initMysqlTableDaoFactory(mysqlConf)

    val couchbaseCf = ConnektConfig.getConfig("connections.couchbase").getOrElse(ConfigFactory.empty())
    DaoFactory.initCouchbaseCluster(couchbaseCf)

    val aeroSpikeCf = ConnektConfig.getConfig("connections.aerospike").getOrElse(ConfigFactory.empty())
    DaoFactory.initAeroSpike(aeroSpikeCf)


    DaoFactory.initReportingDao(DaoFactory.getCouchbaseBucket(ConnektConfig.getOrElse("couchbase.reporting.bucketname", "StatsReporting")))

    ServiceFactory.initPNMessageService(DaoFactory.getPNRequestDao, DaoFactory.getUserConfigurationDao, getKafkaProducerHelper, getKafkaConsumerConf, null)
    ServiceFactory.initEmailMessageService(DaoFactory.getEmailRequestDao, DaoFactory.getUserConfigurationDao, getKafkaProducerHelper, getKafkaConsumerConf)

    val eventsDao = EventsDaoContainer(pnEventsDao = DaoFactory.getPNCallbackDao, emailEventsDao = DaoFactory.getEmailCallbackDao, smsEventsDao = DaoFactory.getSmsCallbackDao, pullEventsDao = DaoFactory.getPullCallbackDao, waEventsDao = DaoFactory.getWACallbackDao)
    val requestDao = RequestDaoContainer(smsRequestDao = DaoFactory.getSmsRequestDao, pnRequestDao = DaoFactory.getPNRequestDao, emailRequestDao = DaoFactory.getEmailRequestDao, pullRequestDao = DaoFactory.getPullRequestDao, waRequestDao = DaoFactory.getWARequestDao)
    ServiceFactory.initCallbackService(eventsDao, requestDao, getKafkaProducerHelper)

    ServiceFactory.initAuthorisationService(DaoFactory.getPrivDao, DaoFactory.getUserInfoDao)
    ServiceFactory.initStorageService(DaoFactory.getKeyChainDao)
    ServiceFactory.initProjectConfigService(DaoFactory.getUserProjectConfigDao)
    ServiceFactory.initStatsReportingService(DaoFactory.getStatsReportingDao)
    ServiceFactory.initStencilService(DaoFactory.getStencilDao)

    ConnektLogger(LogFile.SERVICE).info("BaseReceptorsTest bootstrapped.")

  }
}
