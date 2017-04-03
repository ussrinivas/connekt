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

import com.flipkart.connekt.commons.cache.{DistributedCacheManager, DistributedCacheType}
import com.flipkart.connekt.commons.core.Wrappers.{Try_, Try_#}
import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.entities.ExclusionType.ExclusionType
import com.flipkart.connekt.commons.entities.{ExclusionDetails, ExclusionEntity}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.metrics.Timed
import com.roundeights.hasher.Implicits.stringToHasher

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object ExclusionService extends Instrumented {

  private lazy val dao = DaoFactory.getExclusionDao
  private final val NO_EXCLUSION = "None"

  @Timed("add")
  def add(exclusionEntity: ExclusionEntity): Try[Unit] = profile(s"add.${exclusionEntity.appName}.${exclusionEntity.channel}") {
    dao.add(exclusionEntity).transform[Unit](_ => Try_#(message = "ExclusionService.add Failed") {
      DistributedCacheManager.getCache(DistributedCacheType.ExclusionDetails).put[String](cacheKey(exclusionEntity.channel, exclusionEntity.appName, exclusionEntity.destination), exclusionEntity.exclusionDetails.exclusionType.toString, exclusionEntity.exclusionDetails.ttl)
      BigfootService.ingestEntity(exclusionEntity.destination, exclusionEntity.toPublishFormat, exclusionEntity.namespace).get
    }, Failure(_))
  }

  @Timed("lookup")
  def lookup(channel: String, appName: String, destination: String, exclusionType: ExclusionType = null): Try[Boolean] = Try_#(message = "ExclusionService.lookup Failed") {
    val getExclusionType: Option[String] = Try_(DistributedCacheManager.getCache(DistributedCacheType.ExclusionDetails).get[String](cacheKey(channel, appName, destination))).getOrElse(None).orElse {
      val id = cacheKey(channel, appName, destination)
      val eType = dao.lookup(channel, appName, destination) match {
        case Success(exDetails) =>
          val eD = exDetails.getOrElse(ExclusionDetails(null))
          val eT = Option(eD.exclusionType).map(_.toString).getOrElse(NO_EXCLUSION)
          DistributedCacheManager.getCache(DistributedCacheType.ExclusionDetails).put[String](cacheKey(channel, appName, destination), eT, eD.ttl)
          Option(eD.exclusionType).map(_.toString)
        case Failure(_) =>
          ConnektLogger(LogFile.SERVICE).error(s"ExclusionService.get Failed for id : $id")
          None
      }
      eType
    }
    getExclusionType.forall(_ == NO_EXCLUSION)
  }

  @Timed("get")
  def get(channel: String, appName: String, destination: String): Try[List[ExclusionDetails]] = Try_#(message = "ExclusionService.get Failed") {
    dao.get(channel, appName, destination).getOrElse(List.empty)
  }


  @Timed("getAll")
  def getAll(channel: String, appName: String, exclusionType: ExclusionType): Try[List[ExclusionEntity]] = Try_#(message = "ExclusionService.getAll Failed") {
    dao.getAll(channel, appName, exclusionType).getOrElse(List.empty)
  }

  @Timed("delete")
  def delete(channel: String, appName: String, destination: String): Try[Unit] = {
    get(channel, appName, destination).flatMap(exclusionDetails => Try_ {
      DistributedCacheManager.getCache(DistributedCacheType.ExclusionDetails).put[String](cacheKey(channel, appName, destination), NO_EXCLUSION, Duration.Inf)
      dao.delete(channel, appName, destination)
      val entries = exclusionDetails.map(ExclusionEntity(channel, appName, destination, _, active = false))
      entries.foreach(eD => BigfootService.ingestEntity(eD.destination, eD.toPublishFormat, eD.namespace))
    })
  }

  private def cacheKey(channel: String, appName: String, destination: String): String = channel.toLowerCase + "_" + appName.toLowerCase + "_" + destination.sha256.hash.hex

}
