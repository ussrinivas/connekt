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
package com.flipkart.connekt.firefly

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.flipkart.connekt.busybees.streams.flows.StageSupervision
import com.flipkart.connekt.commons.connections.ConnectionProvider
import com.flipkart.connekt.commons.core.BaseApp
import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.KafkaProducerHelper
import com.flipkart.connekt.commons.services.{ConnektConfig, EventsDaoContainer, RequestDaoContainer}
import com.flipkart.connekt.commons.sync.SyncManager
import com.flipkart.connekt.commons.utils.ConfigUtils
import com.flipkart.connekt.firefly.dispatcher.HttpDispatcher
import com.typesafe.config.ConfigFactory

object FireflyBoot extends BaseApp {

  private val initialized = new AtomicBoolean(false)

  private implicit val system = ActorSystem("firefly")

  val settings = ActorMaterializerSettings(system)
    .withAutoFusing(enable = false)
    .withSupervisionStrategy(StageSupervision.decider)

  private implicit val mat = ActorMaterializer(settings.withDispatcher("akka.actor.default-dispatcher"))
  private implicit val ec = mat.executionContext

  def start() {
    if (!initialized.getAndSet(true)) {
      ConnektLogger(LogFile.SERVICE).info("Firefly initializing.")

      val loggerConfigFile = ConfigUtils.getSystemProperty("log4j.configurationFile").getOrElse("log4j2-firefly.xml")

      ConnektLogger(LogFile.SERVICE).info(s"Firefly logging using: $loggerConfigFile")
      ConnektLogger.init(loggerConfigFile)

      val applicationConfigFile = ConfigUtils.getSystemProperty("firefly.appConfigurationFile").getOrElse("firefly-config.json")
      ConnektConfig(configServiceHost, configServicePort, apiVersion)(Seq("fk-connekt-root", "fk-connekt-".concat(ConfigUtils.getConfEnvironment), "fk-connekt-firefly", "fk-connekt-firefly-akka"))(applicationConfigFile)

      SyncManager.create(ConnektConfig.getString("sync.zookeeper").get)

      DaoFactory.setUpConnectionProvider(new ConnectionProvider)

      val mysqlConf = ConnektConfig.getConfig("connections.mysql").getOrElse(ConfigFactory.empty())
      DaoFactory.initMysqlTableDaoFactory(mysqlConf)

      ServiceFactory.initStencilService(DaoFactory.getStencilDao)


      //Callbacks write
      val hConfig = ConnektConfig.getConfig("connections.hbase")
      DaoFactory.initHTableDaoFactory(hConfig.get)
      val eventsDao = EventsDaoContainer(pnEventsDao = DaoFactory.getPNCallbackDao, emailEventsDao = DaoFactory.getEmailCallbackDao, smsEventsDao = DaoFactory.getSmsCallbackDao, pullEventsDao = DaoFactory.getPullCallbackDao, waEventsDao = DaoFactory.getWACallbackDao)
      ServiceFactory.initCallbackService(eventsDao, null, null)

      val kafkaConsumerConnConf = ConnektConfig.getConfig("connections.kafka.consumerConnProps").getOrElse(ConfigFactory.empty())
      val kafkaProducerConnConf = ConnektConfig.getConfig("connections.kafka.producerConnProps").getOrElse(ConfigFactory.empty())
      val kafkaProducerPoolConf = ConnektConfig.getConfig("connections.kafka.producerPool").getOrElse(ConfigFactory.empty())
      val kafkaProducerHelper = KafkaProducerHelper.init(kafkaProducerConnConf, kafkaProducerPoolConf)
      ServiceFactory.initContactSyncService(kafkaProducerHelper)

      val requestDao = RequestDaoContainer(smsRequestDao = DaoFactory.getSmsRequestDao, pnRequestDao = DaoFactory.getPNRequestDao, emailRequestDao = DaoFactory.getEmailRequestDao, pullRequestDao = DaoFactory.getPullRequestDao, waRequestDao = DaoFactory.getWARequestDao)
      ServiceFactory.initCallbackService(eventsDao, requestDao, null)

      ServiceFactory.initSMSMessageService(DaoFactory.getSmsRequestDao, DaoFactory.getUserConfigurationDao, null, kafkaConsumerConnConf, null)

      HttpDispatcher.apply(ConnektConfig.getConfig("react").get)

      ClientTopologyManager(kafkaConsumerConnConf, ConnektConfig.getInt("firefly.retry.limit").get)

      InternalTopologyManager(kafkaProducerConnConf)
      WAContactTopologyManager(kafkaConsumerConnConf)

      ConnektLogger(LogFile.SERVICE).info("Started `Firefly` app")
    }
  }

  def terminate() = {
    ConnektLogger(LogFile.SERVICE).info("Firefly shutting down")
    if (initialized.get()) {
      DaoFactory.shutdownHTableDaoFactory()
      Option(ClientTopologyManager.instance).foreach(_.stopAllTopologies())
      ConnektLogger.shutdown()
      Option(InternalTopologyManager.instance).foreach(_.stopAllTopologies())
      Option(WAContactTopologyManager.instance).foreach(_.stopAllTopologies())
    }
  }

  def main(args: Array[String]) {
    System.setProperty("log4j.configurationFile", "log4j2-test.xml")
    start()
  }
}
