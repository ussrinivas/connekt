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

class DefaultGuardrailService extends TGuardrailService {

  override def isGuarded[E, R](entity: TGuardrailEntity[E], meta: TGuardrailEntityMetadata): Try[Boolean] = Success(false)

  override def guard[E, R](entity: TGuardrailEntity[E], meta: TGuardrailEntityMetadata): TGuardrailResponse[R] = ???

  override def modifyGuard[E, R](entity: TGuardrailEntity[E], meta: TGuardrailEntityMetadata): TGuardrailResponse[R] = ???
}
