package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.cache.{LocalCacheManager, LocalCacheType}
import com.flipkart.connekt.commons.dao.UserProjectConfigDao
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.UserProjectConfig

import scala.util.Try


class UserProjectConfigService(upcDao: UserProjectConfigDao) extends Instrumented with TService {

  @Timed("add")
  def add(config: UserProjectConfig): Try[Unit] = Try_ {
    upcDao.addProjectConfiguration(config)
  }

  @Timed("getAllProjectConfigurations")
  def getAllProjectConfigurations(appName: String): Try[List[UserProjectConfig]] = Try_ {
    LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).get[List[UserProjectConfig]](appName).getOrElse {
      val data = upcDao.getProjectConfigurations(appName)
      LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).put[List[UserProjectConfig]](appName, data)
      data
    }
  }


  @Timed("getProjectConfiguration")
  def getProjectConfiguration(appName: String, name:String): Try[Option[UserProjectConfig]] = Try_ {
    val cacheKey = s"PC-$appName-$name"
    LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).get[Option[UserProjectConfig]](cacheKey).getOrElse {
      val data = upcDao.getProjectConfiguration(appName,name)
      LocalCacheManager.getCache(LocalCacheType.UserProjectConfig).put[Option[UserProjectConfig]](cacheKey, data)
      data
    }
  }

}
