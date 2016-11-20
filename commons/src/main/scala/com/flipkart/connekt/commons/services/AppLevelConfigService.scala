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
import com.flipkart.connekt.commons.dao.TAppLevelConfiguration
import com.flipkart.connekt.commons.entities.AppLevelConfig
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed

import scala.util.Try

class AppLevelConfigService(appLevelConfig: TAppLevelConfiguration) extends Instrumented with TAppLevelConfigService {

  @Timed("add")
  def add(config: AppLevelConfig): Try[Unit] = Try_ {
    appLevelConfig.addAppLevelConfiguration(config)
  }

  @Timed("getAppLevelConfig")
  def getAppLevelConfig(appName: String, channel: Channel): Try[List[AppLevelConfig]] = Try_ {
    val cacheKey = s"$appName-${channel.toString}".toLowerCase
    LocalCacheManager.getCache(LocalCacheType.AppLevelConfig).get[List[AppLevelConfig]](cacheKey).getOrElse {
      val data = appLevelConfig.getAllAppLevelConfiguration(appName, channel)
      LocalCacheManager.getCache(LocalCacheType.AppLevelConfig).put[List[AppLevelConfig]](cacheKey.toLowerCase, data)
      data
    }
  }

  @Timed("getChannelLevelConfig")
  override def getChannelLevelConfig(channel: Channel): Try[List[AppLevelConfig]] = Try_ {
    val cacheKey = s"${channel.toString}".toLowerCase
    LocalCacheManager.getCache(LocalCacheType.AppLevelConfig).get[List[AppLevelConfig]](cacheKey).getOrElse {
      val data = appLevelConfig.getAllChannelLevelConfiguration(channel)
      LocalCacheManager.getCache(LocalCacheType.AppLevelConfig).put[List[AppLevelConfig]](cacheKey.toLowerCase, data)
      data
    }
  }
}
