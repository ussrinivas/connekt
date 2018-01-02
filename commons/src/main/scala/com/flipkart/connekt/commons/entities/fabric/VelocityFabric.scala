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
package com.flipkart.connekt.commons.entities.fabric

import java.io.StringWriter

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.utils.VelocityUtils
import org.apache.velocity.app.Velocity
import org.apache.velocity.app.event.{EventCartridge, InvalidReferenceEventHandler}
import org.apache.velocity.context.Context
import org.apache.velocity.exception.VelocityException
import org.apache.velocity.util.introspection.Info

import scala.util.{Failure, Success, Try}

class VelocityFabric(dataVtl: String) extends EngineFabric {

  Velocity.setProperty("runtime.references.strict", false)
  Velocity.setProperty("directive.if.tostring.nullcheck", true)

  val ec = new EventCartridge()
  ec.addInvalidReferenceEventHandler(new InvalidReferenceEventHandler {
    override def invalidGetMethod(context: Context, reference: String, `object`: scala.Any, property: String, info: Info): AnyRef = {
      if(!reference.startsWith("$!")){
        ConnektLogger(LogFile.PROCESSORS).error(s"VelocityFabric InvalidRefHandler/invalidGetMethod StencilID ${info.getTemplateName} RefName:  $reference")
        throw new VelocityException(s"Invalid Reference: Stencil: ${info.toString}, RefName: $reference")
      }
      null
    }

    override def invalidSetMethod(context: Context, leftreference: String, rightreference: String, info: Info): Boolean = false

    override def invalidMethod(context: Context, reference: String, `object`: scala.Any, method: String, info: Info): AnyRef = {
      if( reference.startsWith("$!"))
        ""
      else
        null
    }

  })

  /**
   *
   * @param logRef Reference to be logged in case of error
   * @param context velocity engine operation context
   * @param vtlFabric input string containing the VTL to be rendered
   * @return output string of velocity rendering
   */
  def fabricate(logRef: String, context: Context, vtlFabric: String): Try[String] = {
    try {
      val w = new StringWriter()
      ec.attachToContext(context)
      Velocity.evaluate(context, w, logRef, vtlFabric)
      Success(w.toString)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"Velocity fabricate failed for [$logRef], ${e.getMessage}", e)
        Failure(new Throwable(s"Velocity fabricate failed for [$logRef] error: ${e.getMessage}"))
    }
  }

  def fabricate(logRef: String, context: ObjectNode, vtlFabric: String): Try[String] = {
    fabricate(logRef, VelocityUtils.convertToVelocityContext(context), vtlFabric)
  }

  def validateVtl(): Try[Boolean] = Try.apply(true)

  def compute(logRef: String, context: ObjectNode): AnyRef = {
    fabricate(logRef, context, dataVtl).get
  }
}
