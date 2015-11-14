package com.flipkart.connekt.commons.behaviors

import com.typesafe.config.{ConfigFactory, Config}
import scala.annotation.tailrec

/**
 *
 *
 * @author durga.s
 * @version 11/15/15
 */
trait ConfigAccumulator {

  def overlayConfigs(configs: Config*): Config = {
    @tailrec
    def go(configs: Config*)(acc: Config): Config = {
      if(configs.isEmpty) acc
      else
        go(configs.tail: _*)(configs.head.withFallback(acc))
    }

    go(configs: _*)(ConfigFactory.empty())
  }
}
