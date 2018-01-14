package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.cache.{DistributedCacheManager, DistributedCacheType}
import com.flipkart.connekt.commons.core.Wrappers.Try_#
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed
import com.roundeights.hasher.Implicits.stringToHasher

import scala.util.Try

object SessionControlService extends Instrumented {

  @Timed("add")
  def add(channel: String, appName: String, key: String, count: Integer): Try[Boolean] = Try_#(message = "SessionControlService.add Failed") {
    DistributedCacheManager.getCache(DistributedCacheType.SessionControl).put[Integer](cacheKey(channel, appName, key), count)
  }

  @Timed("get")
  def get(channel: String, appName: String, key: String): Try[Integer] = Try_#(message = "SessionControlService.get Failed") {
    DistributedCacheManager.getCache(DistributedCacheType.SessionControl).get[Integer](cacheKey(channel, appName, key)).getOrElse(0)
  }

  def decrease(channel: String, appName: String, key: String): Try[Boolean] = {
    val currentCount = get(channel, appName, key).get
    if (currentCount <= 0) {
      add(channel, appName, key, 0)
    } else {
      add(channel, appName, key, currentCount - 1)
    }
  }

  def increase(channel: String, appName: String, key: String): Try[Boolean] = {
    val currentCount = get(channel, appName, key).get
    add(channel, appName, key, currentCount + 1)
  }

  private def cacheKey(channel: String, appName: String, key: String): String = channel.toLowerCase + "_" + appName.toLowerCase + "_" + key.sha256.hash.hex

}
