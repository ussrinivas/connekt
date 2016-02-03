package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.cache.{DistributedCacheType, DistributedCacheManager}
import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.entities.DeviceDetails
import com.flipkart.connekt.commons.factories.{LogFile, ConnektLogger}

/**
 * Created by kinshuk.bairagi on 16/01/16.
 */
object DeviceDetailsService {

  lazy val dao = DaoFactory.getDeviceDetailsDao

  def add(deviceDetails: DeviceDetails) = {
    try {
      DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).remove(cacheKey(deviceDetails.appName, deviceDetails.userId))
      dao.add(deviceDetails.appName, deviceDetails)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error("Device Detail add service failed " + e.getCause)
    }
  }

  /**
   *
   * @param deviceId
   * @param deviceDetails
   */
  def update(deviceId: String, deviceDetails: DeviceDetails) = {
    try {
      val existingDevice = get(deviceDetails.appName, deviceId)
      existingDevice match {
        case None =>
        case Some(device) =>
          DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).remove(cacheKey(device.appName, device.userId))
          DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).remove(cacheKey(device.appName, device.token))
          DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).remove(cacheKey(device.appName, device.deviceId))
      }

      DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).remove(cacheKey(deviceDetails.appName, deviceDetails.userId))

      dao.update(deviceDetails.appName, deviceId, deviceDetails)
    } catch {
      case e: Exception => ConnektLogger(LogFile.SERVICE).error("Device Detail update service failed " + e.getCause, e)
    }
  }

  def getByTokenId(appName: String, tokenId: String): Option[DeviceDetails] = {
    try {
      DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).get[DeviceDetails](cacheKey(appName, tokenId)) match {
        case Some(device) => Some(device)
        case None =>
          val device = dao.getByTokenId(appName, tokenId)
          device.foreach(d => DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).put[DeviceDetails](cacheKey(appName, tokenId), d))
          device
      }
    } catch {
      case e: Exception => ConnektLogger(LogFile.SERVICE).error("Device Detail getByToken service failed " + e.getCause, e)
        None
    }
  }

  def getByUserId(appName: String, userId: String): List[DeviceDetails] = {
    try {
      DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).get[List[DeviceDetails]](cacheKey(appName, userId)) match {
        case Some(deviceList) => deviceList
        case None =>
          val deviceList = dao.getByUserId(appName, userId)
          DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).put[List[DeviceDetails]](cacheKey(appName, userId), deviceList)
          deviceList
      }
    }
    catch {
      case e: Exception => ConnektLogger(LogFile.SERVICE).error("Device Detail get by userId service failed " + e.getCause, e)
        List()
    }
  }


  def get(appName: String, deviceId: String): Option[DeviceDetails] = {
    try {
      DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).get[DeviceDetails](cacheKey(appName, deviceId)) match {
        case Some(device) => Some(device)
        case None =>
          val device = dao.get(appName, deviceId)
          DistributedCacheManager.getCache(DistributedCacheType.DeviceDetails).put[DeviceDetails](cacheKey(appName, deviceId), device.orNull)
          device
      }
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error("Device Detail get service failed " + e.getCause, e)
        None
    }
  }

  private def cacheKey(appName: String, id: String): String = appName + "_" + id

}