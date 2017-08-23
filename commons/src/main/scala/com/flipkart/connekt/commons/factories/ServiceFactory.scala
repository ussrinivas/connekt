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
package com.flipkart.connekt.commons.factories

import com.flipkart.connekt.commons.dao._
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.helpers.KafkaProducerHelper
import com.flipkart.connekt.commons.iomodels.PullRequestInfo
import com.flipkart.connekt.commons.services.{KeyChainService, _}
import com.typesafe.config.Config
import org.apache.hadoop.hbase.client.Connection
import scala.concurrent.duration._

object ServiceFactory {

  var serviceCache = Map[ServiceType.Value, TService]()

  def initPNMessageService(requestDao: PNRequestDao, userConfiguration: TUserConfiguration, queueProducerHelper: KafkaProducerHelper, kafkaConsumerConfig: Config, schedulerService: SchedulerService) = {
    serviceCache += ServiceType.PN_MESSAGE -> new MessageService(requestDao, userConfiguration, queueProducerHelper, kafkaConsumerConfig, schedulerService)
  }

  def initEmailMessageService(requestDao: EmailRequestDao, userConfiguration: TUserConfiguration, queueProducerHelper: KafkaProducerHelper, kafkaConsumerConfig: Config): Unit = {
    serviceCache += ServiceType.EMAIL_MESSAGE -> new MessageService(requestDao, userConfiguration, queueProducerHelper, kafkaConsumerConfig, null)
  }

  def initSMSMessageService(requestDao: SmsRequestDao, userConfiguration: TUserConfiguration, queueProducerHelper: KafkaProducerHelper, kafkaConsumerConfig: Config, schedulerService: SchedulerService) = {
    serviceCache += ServiceType.SMS_MESSAGE -> new MessageService(requestDao, userConfiguration, queueProducerHelper, kafkaConsumerConfig, schedulerService)
  }

  def initPULLMessageService(requestDao: PullRequestDao) = {
    serviceCache += ServiceType.PULL_MESSAGE -> new PullMessageService(requestDao)
  }

  def initCallbackService(eventsDao: EventsDaoContainer, requestDao: RequestDaoContainer, queueProducerHelper: KafkaProducerHelper) = {
    serviceCache += ServiceType.CALLBACK -> new CallbackService(eventsDao, requestDao, queueProducerHelper)
  }

  def initAuthorisationService(priv: PrivDao, userInfo: TUserInfo) = {
    serviceCache += ServiceType.AUTHORISATION -> new AuthorisationService(priv, userInfo)
    serviceCache += ServiceType.USER_INFO -> new UserInfoService(userInfo)
  }

  def initStorageService(dao: TKeyChainDao) = {
    serviceCache += ServiceType.KEY_CHAIN -> new KeyChainService(dao)
  }

  def initProjectConfigService(dao: UserProjectConfigDao): Unit = {
    serviceCache += ServiceType.APP_CONFIG -> new UserProjectConfigService(dao)
  }

  def initStatsReportingService(dao: StatsReportingDao): Unit = {
    val instance = new ReportingService(dao)
    instance.init()
    serviceCache += ServiceType.STATS_REPORTING -> instance
  }

  def initSchedulerService(hConnection: Connection): Unit = {
    val instance = new SchedulerService(hConnection)
    serviceCache += ServiceType.SCHEDULER -> instance
  }

  def initMessageQueueService(dao: MessageQueueDao):Unit = {
    serviceCache += ServiceType.FETCH_PUSH_QUEUE -> new MessageQueueService(dao)
  }

  def initPullMessageQueueService(dao: MessageQueueDao, config: Config):Unit = {
    serviceCache += ServiceType.PULL_QUEUE -> new MessageQueueService(dao, config.getConfig("hbase.pull").getInt("ttl").days.toMillis, config.getConfig("hbase.pull").getInt("maxRecords"))
  }

  def getMessageService(channel: Channel): TMessageService = {
    channel match {
      case Channel.PUSH => serviceCache(ServiceType.PN_MESSAGE).asInstanceOf[TMessageService]
      case Channel.EMAIL => serviceCache(ServiceType.EMAIL_MESSAGE).asInstanceOf[TMessageService]
      case Channel.SMS => serviceCache(ServiceType.SMS_MESSAGE).asInstanceOf[TMessageService]
    }
  }

  def getPullMessageService = serviceCache(ServiceType.PULL_MESSAGE).asInstanceOf[PullMessageService]

  def getMessageQueueService = serviceCache(ServiceType.FETCH_PUSH_QUEUE).asInstanceOf[MessageQueueService]

  def getPullMessageQueueService = serviceCache(ServiceType.PULL_QUEUE).asInstanceOf[MessageQueueService]

  def initStencilService(dao: TStencilDao) = serviceCache += ServiceType.STENCIL -> new StencilService(dao)

  def getSchedulerService = serviceCache(ServiceType.SCHEDULER).asInstanceOf[SchedulerService]

  def getCallbackService = serviceCache(ServiceType.CALLBACK).asInstanceOf[TCallbackService]

  def getAuthorisationService = serviceCache(ServiceType.AUTHORISATION).asInstanceOf[TAuthorisationService]

  def getUserInfoService = serviceCache(ServiceType.USER_INFO).asInstanceOf[UserInfoService]

  def getKeyChainService = serviceCache(ServiceType.KEY_CHAIN).asInstanceOf[TStorageService]

  def getReportingService = serviceCache(ServiceType.STATS_REPORTING).asInstanceOf[ReportingService]

  def getStencilService = serviceCache(ServiceType.STENCIL).asInstanceOf[StencilService]

  def getUserProjectConfigService = serviceCache(ServiceType.APP_CONFIG).asInstanceOf[UserProjectConfigService]

}

object ServiceType extends Enumeration {
  val PN_MESSAGE, TEMPLATE, CALLBACK, USER_INFO, AUTHORISATION, KEY_CHAIN, STATS_REPORTING, SCHEDULER, STENCIL, SMS_MESSAGE, EMAIL_MESSAGE, APP_CONFIG, FETCH_PUSH_QUEUE, PULL_MESSAGE, PULL_QUEUE = Value
}
