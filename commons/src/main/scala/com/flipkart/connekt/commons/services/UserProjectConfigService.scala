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
package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.cache.{LocalCacheManager, LocalCacheType}
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.dao.UserProjectConfigDao
import com.flipkart.connekt.commons.entities.UserProjectConfig
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.sync.SyncType.SyncType
import com.flipkart.connekt.commons.sync.{SyncDelegate, SyncManager, SyncMessage, SyncType}
import com.flipkart.metrics.Timed

import scala.util.Try


class UserProjectConfigService(upcDao: UserProjectConfigDao) extends Instrumented with TService with SyncDelegate {

  SyncManager.get().addObserver(this, List(SyncType.USER_PROJECT_CONFIG_CHANGE))

  @Timed("add")
  def add(config: UserProjectConfig): Try[Unit] = Try_ {
    upcDao.addProjectConfiguration(config)
    SyncManager.get().publish(SyncMessage(SyncType.USER_PROJECT_CONFIG_CHANGE, List(config.appName, config.name)))
  }

  @Timed("getAllProjectConfigurations")
  def getAllProjectConfigurations(appName: String): Try[List[UserProjectConfig]] = Try_ {
    val cacheKey = appNameCacheKey(appName)
    LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).get[List[UserProjectConfig]](cacheKey).getOrElse {
      val data = upcDao.getProjectConfigurations(appName)
      LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).put[List[UserProjectConfig]](cacheKey, data)
      data
    }
  }


  @Timed("getProjectConfiguration")
  def getProjectConfiguration(appName: String, configName: String): Try[Option[UserProjectConfig]] = Try_ {
    val cacheKey = appNameConfigCacheKey(appName, configName)
    LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).get[Option[UserProjectConfig]](cacheKey).getOrElse {
      val data = upcDao.getProjectConfiguration(appName, configName)
      LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).put[Option[UserProjectConfig]](cacheKey, data)
      data
    }
  }

  override def onUpdate(syncType: SyncType, args: List[AnyRef]): Any = {
    ConnektLogger(LogFile.SERVICE).info("UserProjectConfigService.onUpdate ChangeType : {} Message : {} ", syncType, args.map(_.toString))
    syncType match {
      case SyncType.USER_PROJECT_CONFIG_CHANGE => Try_ {
        val appName = args.head.toString
        val name = args(1).toString
        LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).remove(appNameCacheKey(appName))
        LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).remove(appNameConfigCacheKey(appName, name))
      }
      case _ =>
    }
  }

  private def appNameConfigCacheKey(appname: String, configName: String) = s"PC-$appname-$configName"

  private def appNameCacheKey(appname: String) = s"PC-$appname"

}
