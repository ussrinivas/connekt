package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.utils.ConfigUtils
import com.flipkart.utils.config.KloudConfig
import com.typesafe.config.Config

import scala.collection.JavaConverters._


object ConnektConfig {

  var instance: KloudConfig = null

  def apply(configHost: String = "10.47.0.101", configPort: Int = 80)
           (bucketIdMap: Seq[String] = Seq("fk-connekt-credentials", "fk-connekt-root", "fk-connekt-".concat(ConfigUtils.getConfEnvironment))) = {
    this.synchronized {
      if (null == instance) {
        instance = new KloudConfig(configHost, configPort)(bucketIdMap)
        instance.init()
      }
    }
    instance
  }

  def getList[V](k: String): List[V] = {
    instance.get[V](k).map(_.asInstanceOf[java.util.ArrayList[V]].asScala.toList).getOrElse(Nil)
  }

  def getOrElse[V](k: String, default: V): V = instance.getOrElse(k, default)

  def get[V](k: String): Option[V] = instance.get[V](k)

  def getString(k: String): Option[String] = instance.getString(k)

  def getInt(k: String): Option[Int] = instance.get[Int](k)

  def getDouble(k: String): Option[Double] = instance.get[Double](k)

  def getBoolean(k: String): Option[Boolean] = instance.get[Boolean](k)

  def getConfig(k: String): Option[Config] = instance.getConfig(k)


}