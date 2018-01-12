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
package com.flipkart.connekt.commons.utils

import com.flipkart.concord.guardrail.{TGuardrailEntity, TGuardrailEntityMetadata, TGuardrailResponse, TGuardrailService}

import scala.util.{Success, Try}

class DefaultGuardrailService extends TGuardrailService[String, AnyRef, AnyRef] {

  override def isGuarded(entity: TGuardrailEntity[String], meta: TGuardrailEntityMetadata): Try[Boolean] = Success(false)

  override def guard(entity: TGuardrailEntity[String], meta: TGuardrailEntityMetadata): TGuardrailResponse[AnyRef] = ???

  override def modifyGuard(entity: TGuardrailEntity[String], meta: TGuardrailEntityMetadata): TGuardrailResponse[AnyRef] = ???
}
