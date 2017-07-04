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

  val ec = new EventCartridge()
  ec.addInvalidReferenceEventHandler(new InvalidReferenceEventHandler {
    override def invalidGetMethod(context: Context, reference: String, `object`: scala.Any, property: String, info: Info): AnyRef = {
      if(!reference.startsWith("$!")){
        ConnektLogger(LogFile.PROCESSORS).error(s"VelocityFabric InvalidRefHandler/invalidGetMethod StencilID ${info.getTemplateName} RefName:  $reference")
        throw new VelocityException(s"Invalid Reference: Stencil: ${info.toString}, RefName: $reference")
      } else
        ""
    }

    override def invalidSetMethod(context: Context, leftreference: String, rightreference: String, info: Info): Boolean = false

    override def invalidMethod(context: Context, reference: String, `object`: scala.Any, method: String, info: Info): AnyRef = {
      ConnektLogger(LogFile.PROCESSORS).error(s"VelocityFabric InvalidRefHandler/invalidMethod StencilID ${info.getTemplateName} RefName: $reference")
      throw new VelocityException(s"Invalid Method: Stencil: ${info.toString}, RefName: $reference")
    }
  })

  /**
   *
   * @param context velocity engine operation context
   * @param vtlFabric input string containing the VTL to be rendered
   * @param errorTag identifier stencil name for log messages in case of error
   * @return output string of velocity rendering
   */
  def fabricate(id: String, context: Context, vtlFabric: String, errorTag: String): Try[String] = {
    try {
      val w = new StringWriter()
      ec.attachToContext(context)
      Velocity.evaluate(context, w, errorTag, vtlFabric)
      Success(w.toString)
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"Velocity fabricate failed for [$id}], ${e.getMessage}", e)
        Failure(new Throwable(s"Velocity fabricate failed for [$id}] error: ${e.getMessage}"))
    }
  }

  def fabricate(id: String, context: ObjectNode, vtlFabric: String, errorTag: String): Try[String] = {
    fabricate(id, VelocityUtils.convertToVelocityContext(context), vtlFabric, errorTag)
  }

  def validateVtl(): Try[Boolean] = Try.apply(true)

  def compute(id: String, context: ObjectNode): AnyRef = {
    fabricate(id, context, dataVtl, s"$id").get
  }
}
