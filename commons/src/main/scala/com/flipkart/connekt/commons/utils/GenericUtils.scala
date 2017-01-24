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

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object GenericUtils {

  def typeToClassTag[T: TypeTag]: ClassTag[T] = {
    ClassTag[T](typeTag[T].mirror.runtimeClass(typeTag[T].tpe))
  }

  implicit class CaseClassPatch[T](val input: T) {
    def patch(delta: Map[String, AnyRef]): T = {
      val clz = input.getClass
      val constructor = clz.getDeclaredConstructors.head
      val fields = clz.getDeclaredFields
      val arguments = constructor.getParameterTypes.indices.map { i =>
        val fieldName = fields(i).getName
        delta.getOrElse(fieldName, clz.getMethod(fieldName).invoke(input))
      }
      constructor.newInstance(arguments: _*).asInstanceOf[T]
    }
  }
}
