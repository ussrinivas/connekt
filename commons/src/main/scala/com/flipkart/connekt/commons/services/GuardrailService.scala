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

import scala.collection.JavaConversions.mapAsScalaMap
import scala.util.Try

class GuardrailService

object GuardrailService extends Instrumented {

  private val projectConfigService = ServiceFactory.getUserProjectConfigService

  @Timed("isGuarded")
  def isGuarded[E, R](appName: String, channel: Channel, entity: TGuardrailEntity[E], meta: TGuardrailEntityMetadata): Try[Boolean] = {
    val validatorClassName = projectConfigService.getProjectConfiguration(appName, s"validator-service-${channel.toString.toLowerCase}").get.map(_.value).getOrElse(classOf[DefaultGuardrailService].getName)
    try {
      ConnektLogger(LogFile.PROCESSORS).debug(s"GuardrailService received message for appName : $appName and channel $channel with entity ${entity.entity}")
      val validator: TGuardrailService[E, R] = Class.forName(validatorClassName).newInstance().asInstanceOf[TGuardrailService[E, R]]
      validator.isGuarded(entity, meta)
    } catch {
      case e: Throwable =>
        meter(s"guardrail.service.isGuarded.$validatorClassName.failure").mark()
        ConnektLogger(LogFile.PROCESSORS).error(s"GuardrailService error", e)
        throw new Exception(e)
    }
  }

  @Timed("guard")
  def guard[E, R](appName: String, channel: Channel, entity: TGuardrailEntity[E], jMeta: java.util.LinkedHashMap[String, AnyRef]): Try[R] = {
    val validatorClassName = projectConfigService.getProjectConfiguration(appName, s"validator-service-${channel.toString.toLowerCase}").get.map(_.value).getOrElse(classOf[DefaultGuardrailService].getName)
    try {
      ConnektLogger(LogFile.PROCESSORS).debug(s"GuardrailService received message for appName : $appName and channel $channel with entity ${entity.entity}")
      val validator: TGuardrailService[E, R] = Class.forName(validatorClassName).newInstance().asInstanceOf[TGuardrailService[E, R]]
      val metadata = new TGuardrailEntityMetadata {
        override def meta = jMeta.toMap
      }
      val guardResponse = validator.guard(entity, metadata)
      guardResponse.get.response
    } catch {
      case e: Throwable =>
        meter(s"guardrail.service.guard.$validatorClassName.failure").mark()
        ConnektLogger(LogFile.PROCESSORS).error(s"GuardrailService error", e)
        throw new Exception(e)
    }
  }

}
