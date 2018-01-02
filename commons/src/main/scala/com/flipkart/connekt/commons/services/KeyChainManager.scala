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
import com.flipkart.connekt.commons.entities.MobilePlatform.MobilePlatform
import com.flipkart.connekt.commons.entities._
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.serializers.KryoSerializer

object KeyChainManager {

  private val storage = ServiceFactory.getKeyChainService

  private def getNameSpacedKey(platform: MobilePlatform, appName: String) = s"$platform.${appName.toLowerCase}"

  def addSimpleCredential(name: String, credential: SimpleCredential) = {
    val bytes = KryoSerializer.serialize(credential)
    storage.put(name, bytes)
  }

  @throws[Exception]
  def getSimpleCredential(name: String): Option[SimpleCredential] = {
    LocalCacheManager.getCache(LocalCacheType.AppCredential).get[SimpleCredential](name).orElse{
      val credential = storage.get(name).get.map(KryoSerializer.deserialize[SimpleCredential])
      credential.foreach(LocalCacheManager.getCache(LocalCacheType.AppCredential).put[SimpleCredential](name, _))
      credential
    }
  }

  def addAppleCredentials(name: String, credential: AppleCredential) = {
    val bytes = KryoSerializer.serialize(credential)
    storage.put(getNameSpacedKey(MobilePlatform.IOS, name), bytes)
  }

  @throws[Exception]
  def getAppleCredentials(name: String): Option[AppleCredential] = {
    val key = getNameSpacedKey(MobilePlatform.IOS, name)

    LocalCacheManager.getCache(LocalCacheType.AppCredential).get[AppleCredential](key).orElse{
      val credential = storage.get(key).get.map(KryoSerializer.deserialize[AppleCredential])
      credential.foreach(LocalCacheManager.getCache(LocalCacheType.AppCredential).put[AppleCredential](key, _))
      credential
    }
  }

  def addWhatsAppCredential(name: String, credential: WhatsAppCredential) = {
    val bytes = KryoSerializer.serialize(credential)
    storage.put(s"${Channel.WA}.${name.toLowerCase}", bytes)
  }

  @throws[Exception]
  def getWhatsAppCredentials(name: String): Option[WhatsAppCredential] = {
    val key = s"${Channel.WA}.${name.toLowerCase}"
    LocalCacheManager.getCache(LocalCacheType.AppCredential).get[WhatsAppCredential](key).orElse{
      val credential = storage.get(key).get.map(KryoSerializer.deserialize[WhatsAppCredential])
      credential.foreach(LocalCacheManager.getCache(LocalCacheType.WhatsAppCredential).put[WhatsAppCredential](key, _))
      credential
    }
  }


  def addMicrosoftCredential(name: String, credential: MicrosoftCredential) = {
    val bytes = KryoSerializer.serialize(credential)
    storage.put(getNameSpacedKey(MobilePlatform.WINDOWS, name), bytes)
  }

  @throws[Exception]
  def getMicrosoftCredential(name: String): Option[MicrosoftCredential] = {
    val key = getNameSpacedKey(MobilePlatform.WINDOWS, name)

    LocalCacheManager.getCache(LocalCacheType.AppCredential).get[MicrosoftCredential](key).orElse{
      val credential = storage.get(key).get.map(KryoSerializer.deserialize[MicrosoftCredential])
      credential.foreach(LocalCacheManager.getCache(LocalCacheType.AppCredential).put[MicrosoftCredential](key, _))
      credential
    }
  }

  def addGoogleCredential(name: String, credential: GoogleCredential) = {
    val bytes = KryoSerializer.serialize(credential)
    storage.put(getNameSpacedKey(MobilePlatform.ANDROID, name), bytes)
  }

  @throws[Exception]
  def getGoogleCredential(name: String): Option[GoogleCredential] = {
    val key = getNameSpacedKey(MobilePlatform.ANDROID, name)

    LocalCacheManager.getCache(LocalCacheType.AppCredential).get[GoogleCredential](key).orElse{
      val credential = storage.get(key).get.map(KryoSerializer.deserialize[GoogleCredential])
      credential.foreach(LocalCacheManager.getCache(LocalCacheType.AppCredential).put[GoogleCredential](key, _))
      credential
    }
  }

  def addKeyPairCredential(name: String, credential: KeyPairCredential) = {
    val bytes = KryoSerializer.serialize(credential)
    storage.put( getNameSpacedKey(MobilePlatform.OPENWEB, name), bytes)
  }

  @throws[Exception]
  def getKeyPairCredential(name: String): Option[KeyPairCredential] = {
    val key = getNameSpacedKey(MobilePlatform.OPENWEB, name)

    LocalCacheManager.getCache(LocalCacheType.AppCredential).get[KeyPairCredential](key).orElse{
      val credential = storage.get(key).get.map(KryoSerializer.deserialize[KeyPairCredential])
      credential.foreach(LocalCacheManager.getCache(LocalCacheType.AppCredential).put[KeyPairCredential](key, _))
      credential
    }
  }

}
