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

import com.flipkart.concord.guardrail.TGuardrailService
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.DefaultGuardrailService
import com.flipkart.metrics.Timed

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

object ValidatorService extends Instrumented {

  lazy private val projectConfigService = ServiceFactory.getUserProjectConfigService

  @Timed("validate")
  def validate(appName: String, channel: Channel, params: AnyRef*)(implicit ec: ExecutionContextExecutor): Future[Try[Boolean]] = {
    try {
      ConnektLogger(LogFile.PROCESSORS).debug("ValidatorService received message")
      val validatorClassName = projectConfigService.getProjectConfiguration(appName, s"validator-service-${channel.toString.toLowerCase}").get.map(_.value).getOrElse(classOf[DefaultGuardrailService].getName)
      val validator: TGuardrailService = Class.forName(validatorClassName).newInstance().asInstanceOf[TGuardrailService]
      Future(validator.guard(params))
    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"ValidatorService error", e)
        throw new Exception(e)
    }
  }
}
