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
import com.flipkart.connekt.commons.core.Wrappers.Try_#
import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.KafkaProducerHelper
import com.flipkart.connekt.commons.services.{ConnektConfig, EventsDaoContainer, RequestDaoContainer}
import com.flipkart.connekt.commons.sync.SyncManager
import com.flipkart.connekt.commons.utils.ConfigUtils
import com.flipkart.connekt.firefly.flows.dispatchers.HttpDispatcher
import com.flipkart.connekt.firefly.topology.{ClientTopologyManager, SmsLatencyMeteringTopology, WAContactTopology}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationLong
import scala.concurrent.{Await, Future}

object FireflyBoot extends BaseApp {

  private val initialized = new AtomicBoolean(false)

  implicit val system = ActorSystem("firefly")

  val settings = ActorMaterializerSettings(system)
    .withAutoFusing(enable = false)
    .withSupervisionStrategy(StageSupervision.decider)

  implicit val mat = ActorMaterializer(settings.withDispatcher("akka.actor.default-dispatcher"))
  implicit val ec = mat.executionContext
  lazy val ioMat = ActorMaterializer(settings.withDispatcher("akka.actor.io-dispatcher"))

  var wAContactTopology: WAContactTopology = _
  var smsLatencyMeteringTopology: SmsLatencyMeteringTopology = _

  private lazy val WA_CONTACT_TOPIC = ConnektConfig.getString("wa.contact.topic.name").get
  private lazy val CALLBACK_QUEUE_NAME = ConnektConfig.get("firefly.latency.metric.kafka.topic").getOrElse("ckt_callback_events_%s")

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
      ServiceFactory.initStorageService(DaoFactory.getKeyChainDao)
      ServiceFactory.initProjectConfigService(DaoFactory.getUserProjectConfigDao)

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
      ServiceFactory.initEmailMessageService(DaoFactory.getEmailRequestDao, DaoFactory.getUserConfigurationDao, kafkaProducerHelper, kafkaConsumerConnConf)
      ServiceFactory.initPNMessageService(DaoFactory.getPNRequestDao, DaoFactory.getUserConfigurationDao, kafkaProducerHelper, kafkaConsumerConnConf, null)
      ServiceFactory.initWAMessageService(DaoFactory.getWARequestDao, DaoFactory.getUserConfigurationDao, kafkaProducerHelper, kafkaConsumerConnConf, null)

      HttpDispatcher.apply(ConnektConfig.getConfig("react").get)

      ClientTopologyManager(kafkaConsumerConnConf, ConnektConfig.getInt("firefly.retry.limit").get)

      smsLatencyMeteringTopology = new SmsLatencyMeteringTopology(kafkaConsumerConnConf, CALLBACK_QUEUE_NAME.format(Channel.SMS.toString.toLowerCase))
      smsLatencyMeteringTopology.run

      wAContactTopology = new WAContactTopology(kafkaConsumerConnConf, WA_CONTACT_TOPIC)
      wAContactTopology.run

      ConnektLogger(LogFile.SERVICE).info("Started `Firefly` app")
    }
  }

  def terminate() = {
    ConnektLogger(LogFile.SERVICE).info("Firefly shutting down")
    if (initialized.get()) {
      DaoFactory.shutdownHTableDaoFactory()
      Option(ClientTopologyManager.instance).foreach(_.stopAllTopologies())
      val shutdownFutures = Option(wAContactTopology).map(_.shutdown()) :: Option(smsLatencyMeteringTopology).map(_.shutdown()) :: Nil
      Try_#(message = "Topology Shutdown Didn't Complete")(Await.ready(Future.sequence(shutdownFutures.flatten), 20.seconds))
      ConnektLogger.shutdown()
    }
  }

  def main(args: Array[String]) {
    System.setProperty("log4j.configurationFile", "log4j2-test.xml")
    start()
  }
}
