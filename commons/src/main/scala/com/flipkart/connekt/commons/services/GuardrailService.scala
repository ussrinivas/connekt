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

import com.flipkart.concord.guardrail.{TGuardrailEntity, TGuardrailEntityMetadata, TGuardrailService}
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.DefaultGuardrailService
import com.flipkart.metrics.Timed
import scala.util.Try

class GuardrailService

object GuardrailService extends Instrumented {

  private val projectConfigService = ServiceFactory.getUserProjectConfigService

  @Timed("isGuarded")
  def isGuarded[E, R, T](appName: String, channel: Channel, inpEntity: E, inpMeta: Map[String, AnyRef]): Try[Boolean] = {
    val gEntity = new TGuardrailEntity[E] {
      override def entity = inpEntity
    }
    val gMeta = new TGuardrailEntityMetadata {
      override def meta = inpMeta
    }
    val validatorClassName = projectConfigService.getProjectConfiguration(appName, s"validator-service-${channel.toString.toLowerCase}").get.map(_.value).getOrElse(classOf[DefaultGuardrailService].getName)
    try {
      ConnektLogger(LogFile.PROCESSORS).debug(s"GuardrailService received message for appName : $appName and channel $channel with entity ${inpEntity}")
      val guardrailServiceImpl: TGuardrailService[E, R, T] = Class.forName(validatorClassName).newInstance().asInstanceOf[TGuardrailService[E, R, T]]
      guardrailServiceImpl.isGuarded(gEntity, gMeta)
    } catch {
      case e: Throwable =>
        meter(s"guardrail.service.isGuarded.$validatorClassName.failure").mark()
        ConnektLogger(LogFile.PROCESSORS).error(s"GuardrailService error", e)
        throw new Exception(e)
    }
  }

  @Timed("guard")
  def guard[E, R, T](appName: String, channel: Channel, entity: TGuardrailEntity[E], inputMeta: Map[String, AnyRef]): Try[R] = {
    val validatorClassName = projectConfigService.getProjectConfiguration(appName, s"validator-service-${channel.toString.toLowerCase}").get.map(_.value).getOrElse(classOf[DefaultGuardrailService].getName)
    try {
      ConnektLogger(LogFile.PROCESSORS).debug(s"GuardrailService received message for appName : $appName and channel $channel with entity ${entity.entity}")
      val guardrailServiceImpl: TGuardrailService[E,R,T] = Class.forName(validatorClassName).newInstance().asInstanceOf[TGuardrailService[E, R, T]]
      val metadata = new TGuardrailEntityMetadata {
        override def meta = inputMeta
      }
      guardrailServiceImpl.guard(entity, metadata).response
    } catch {
      case e: Throwable =>
        meter(s"guardrail.service.guard.$validatorClassName.failure").mark()
        ConnektLogger(LogFile.PROCESSORS).error(s"GuardrailService error", e)
        throw new Exception(e)
    }
  }

  @Timed("modifyGuard")
  def modifyGuard[E, R, T](appName: String, channel: Channel, inpEntity: E, inputMeta: Map[String, AnyRef]): Try[T] = {
    val validatorClassName = projectConfigService.getProjectConfiguration(appName, s"validator-service-${channel.toString.toLowerCase}").get.map(_.value).getOrElse(classOf[DefaultGuardrailService].getName)
    try {
      ConnektLogger(LogFile.PROCESSORS).debug(s"GuardrailService received message for appName : $appName and channel $channel with entity ${inpEntity}")
      val guardrailServiceImpl: TGuardrailService[E, R, T] = Class.forName(validatorClassName).newInstance().asInstanceOf[TGuardrailService[E, R, T]]
      val gEntity = new TGuardrailEntity[E] {
        override def entity = inpEntity
      }
      val metadata = new TGuardrailEntityMetadata {
        override def meta = inputMeta
      }
      guardrailServiceImpl.modifyGuard(gEntity, metadata).response
    } catch {
      case e: Throwable =>
        meter(s"guardrail.service.modifyGuard.$validatorClassName.failure").mark()
        ConnektLogger(LogFile.PROCESSORS).error(s"GuardrailService error", e)
        throw new Exception(e)
    }
  }


}
