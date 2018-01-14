package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.cache.{DistributedCacheManager, DistributedCacheType}
import com.flipkart.connekt.commons.core.Wrappers.Try_#
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed

import scala.util.Try

object SessionControlService extends Instrumented {

  @Timed("add")
  private def add(appName: String, destination: String, count: Integer): Try[Boolean] = Try_#(message = "SessionControlService.add Failed") {
    DistributedCacheManager.getCache(DistributedCacheType.SessionControl).put[Integer](cacheKey(appName, destination), count)
  }

  @Timed("get")
  def get(appName: String, destination: String): Try[Integer] = Try_#(message = "SessionControlService.get Failed") {
    DistributedCacheManager.getCache(DistributedCacheType.SessionControl).get[Integer](cacheKey(appName, destination)).getOrElse(0)
  }

  def decrease(appName: String, destination: String): Try[Boolean] = {
    val currentCount = get(appName, destination).get
    add(appName, destination, currentCount - 1)
  }

  def increase(appName: String, destination: String): Try[Boolean] = {
    val currentCount = get(appName, destination).get
    add(appName, destination, currentCount + 1)
  }

  private def cacheKey(appName: String, destination: String): String = appName.toLowerCase + "_" + destination

}
