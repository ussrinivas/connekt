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

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.cache.{LocalCacheManager, LocalCacheType}
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.dao.TStencilDao
import com.flipkart.connekt.commons.entities.fabric._
import com.flipkart.connekt.commons.entities.{Bucket, Stencil, StencilEngine, StencilsEnsemble}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.sync.SyncType._
import com.flipkart.connekt.commons.sync.{SyncDelegate, SyncManager, SyncMessage, SyncType}
import com.flipkart.metrics.Timed

import scala.util.{Failure, Success, Try}

class StencilService(stencilDao: TStencilDao) extends TStencilService with Instrumented with SyncDelegate {

  SyncManager.get().addObserver(this, List(SyncType.STENCIL_CHANGE, SyncType.STENCIL_COMPONENTS_UPDATE, SyncType.STENCIL_FABRIC_CHANGE))

  private def stencilCacheKey(id: String, version: Option[String] = None):String = id + version.getOrElse("")

  def fabricCacheKey(id: String, component: String, version: String):String  = id + component + version

  def checkStencil(stencil: Stencil): Try[Boolean] = {
    try {
      stencil.engine match {
        case StencilEngine.GROOVY =>
          FabricMaker.create[GroovyFabric](stencil.engineFabric)
        case StencilEngine.VELOCITY =>
          FabricMaker.createVtlFabric(stencil.engineFabric)
      }
      require(getStencilsEnsembleByName(stencil.`type`).get.components.split(',').contains(stencil.component), "Invalid Stencil Component")
      Success(true)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.SERVICE).error(s"StencilService validation failed for id ${stencil.id} ", e)
        Failure(e)
    }
  }

  @Timed("render")
  def materialize(stencil: Stencil, req: ObjectNode): AnyRef = {
    LocalCacheManager.getCache(LocalCacheType.EngineFabrics).get[EngineFabric](fabricCacheKey(stencil.id, stencil.component, stencil.version.toString)).orElse {
      val fabric = stencil.engine match {
        case StencilEngine.GROOVY =>
          FabricMaker.create[GroovyFabric](stencil.engineFabric)
        case StencilEngine.VELOCITY =>
          FabricMaker.createVtlFabric(stencil.engineFabric)
      }
      LocalCacheManager.getCache(LocalCacheType.EngineFabrics).put[EngineFabric](fabricCacheKey(stencil.id, stencil.component, stencil.version.toString), fabric)
      Option(fabric)
    }.map(_.compute(stencil.id, req)).orNull
  }

  @Timed("add")
  def add(id: String, stencils: List[Stencil]): Try[Unit] = Try_ {
    stencilDao.writeStencils(stencils)
  }

  @Timed("delete")
  def delete(id: String): Try[Unit] = Try {
    stencilDao.deleteStencil(id)
  }

  @Timed("update")
  def update(id: String, stencils: List[Stencil]): Try[Unit] = Try_ {
    stencilDao.writeStencils(stencils)
    LocalCacheManager.getCache(LocalCacheType.Stencils).remove(stencilCacheKey(id))
    SyncManager.get().publish(SyncMessage(SyncType.STENCIL_CHANGE, List(id)))
    stencils.headOption.foreach { stn =>
      LocalCacheManager.getCache(LocalCacheType.Stencils).remove(stencilCacheKey(stn.name))
      SyncManager.get().publish(SyncMessage(SyncType.STENCIL_CHANGE, List(stn.name)))
    }
  }

  @Timed("update")
  def updateWithIdentity(id: String, prevName: String, stencils: List[Stencil]): Try[Unit] = Try_ {
    update(id, stencils)
    stencilDao.deleteStencilByName(prevName, id)
    SyncManager.get().publish(SyncMessage(SyncType.STENCIL_CHANGE, List(prevName)))
    LocalCacheManager.getCache(LocalCacheType.Stencils).remove(stencilCacheKey(prevName))
  }

  @Timed("get")
  def get(id: String, version: Option[String] = None): List[Stencil] = {
    LocalCacheManager.getCache(LocalCacheType.Stencils).get[List[Stencil]](stencilCacheKey(id, version)).orElse {
      val stencils = stencilDao.getStencils(id, version)
      LocalCacheManager.getCache(LocalCacheType.Stencils).put[List[Stencil]](stencilCacheKey(id, version), stencils)
      Option(stencils)
    }.get
  }

  @Timed("getStencilsByName")
  def getStencilsByName(name: String, version: Option[String] = None): List[Stencil] = {
    LocalCacheManager.getCache(LocalCacheType.Stencils).get[List[Stencil]](stencilCacheKey(name, version)).orElse {
      val stencils = stencilDao.getStencilsByName(name, version)
      LocalCacheManager.getCache(LocalCacheType.Stencils).put[List[Stencil]](stencilCacheKey(name, version), stencils)
      Option(stencils)
    }.get
  }

  @Timed("getStencilsByBucket")
  def getStencilsByBucket(name: String): List[Stencil] = {
     stencilDao.getStencilsByBucket(name)
  }

  @Timed("getBucket")
  def getBucket(name: String): Option[Bucket] = {
    LocalCacheManager.getCache(LocalCacheType.StencilsBucket).get[Bucket](name).orElse {
      val bucket = stencilDao.getBucket(name)
      bucket.foreach(b => LocalCacheManager.getCache(LocalCacheType.StencilsBucket).put[Bucket](name, b))
      bucket
    }
  }

  @Timed("addBucket")
  def addBucket(bucket: Bucket): Try[Unit] = {
    getBucket(bucket.name) match {
      case Some(_) =>
        Failure(new Exception(s"Bucket already exists for name: ${bucket.name}"))
      case _ =>
        LocalCacheManager.getCache(LocalCacheType.StencilsBucket).put[Bucket](bucket.name, bucket)
        Success(stencilDao.writeBucket(bucket))
    }
  }

  @Timed("getStencilsEnsemble")
  def getStencilsEnsemble(id: String): Option[StencilsEnsemble] = {
    LocalCacheManager.getCache(LocalCacheType.StencilsEnsemble).get[StencilsEnsemble](id).orElse {
      val stencilComponents = stencilDao.getStencilsEnsemble(id)
      stencilComponents.foreach(b => LocalCacheManager.getCache(LocalCacheType.StencilsEnsemble).put[StencilsEnsemble](id, b))
      stencilComponents
    }
  }

  @Timed("getStencilsEnsembleByName")
  def getStencilsEnsembleByName(name: String): Option[StencilsEnsemble] = {
    LocalCacheManager.getCache(LocalCacheType.StencilsEnsemble).get[StencilsEnsemble](name).orElse {
      val stencilComponents = stencilDao.getStencilsEnsembleByName(name)
      stencilComponents.foreach(b => LocalCacheManager.getCache(LocalCacheType.StencilsEnsemble).put[StencilsEnsemble](name, b))
      stencilComponents
    }
  }


  @Timed("addStencilsEnsemble")
  def addStencilComponents(stencilComponents: StencilsEnsemble): Try[Unit] = Try_ {
    stencilDao.writeStencilsEnsemble(stencilComponents)
    LocalCacheManager.getCache(LocalCacheType.StencilsEnsemble).put[StencilsEnsemble](stencilComponents.id, stencilComponents)
    SyncManager.get().publish(SyncMessage(SyncType.STENCIL_COMPONENTS_UPDATE, List(stencilComponents.id)))
  }

  @Timed("getAllEnsemble")
  override def getAllEnsemble(): List[StencilsEnsemble] = {
    stencilDao.getAllEnsemble()
  }


  override def onUpdate(syncType: SyncType, args: List[AnyRef]): Unit = {
    ConnektLogger(LogFile.SERVICE).info("StencilService.onUpdate ChangeType : {} Message : {} ", syncType, args.map(_.toString))
    syncType match {
      case SyncType.STENCIL_CHANGE => Try_ {
        LocalCacheManager.getCache(LocalCacheType.Stencils).remove(stencilCacheKey(args.head.toString))
        LocalCacheManager.getCache(LocalCacheType.Stencils).remove(stencilCacheKey(args.head.toString, args.lastOption.map(_.toString)))
      }
      case SyncType.STENCIL_FABRIC_CHANGE => Try_ {
        LocalCacheManager.getCache(LocalCacheType.EngineFabrics).remove(args.head.toString)
      }
      case SyncType.STENCIL_COMPONENTS_UPDATE => Try_ {
        LocalCacheManager.getCache(LocalCacheType.StencilsEnsemble).remove(args.head.toString)
      }
      case _ =>
    }
  }

}
