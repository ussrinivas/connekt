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

import com.fasterxml.jackson.databind.node._
import org.apache.velocity.VelocityContext
import org.apache.velocity.context.Context

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

object VelocityUtils {

  /**
    * Convert to Velocity Context Helpers
    * --------- START ------------------
    */
  def convertToVelocityContext(node: ObjectNode): Context = {
    val fields = node.fieldNames()
    val vContext = new VelocityContext()
    fields.asScala.foreach(fieldName =>  vContext.put(fieldName, getValue(node.get(fieldName))))
    vContext
  }

  private def convertToVelocityContext(array: ArrayNode): Any = {

    //TODO: This scala version doesn't' work. asJava is still returning seqWrapper
    //array.map(row => getValue(row)).toList.asJava.asInstanceOf[java.util.ArrayList[Object]]

    val arrayList = new java.util.ArrayList[Any]()
    for (value <- array) arrayList.add(getValue(value))
    arrayList
  }


  private def getValue(obj: Any): Any = {
    obj match {
      case _: IntNode => obj.asInstanceOf[IntNode].intValue()
      case _: DoubleNode => obj.asInstanceOf[DoubleNode].doubleValue()
      case _: BooleanNode => obj.asInstanceOf[BooleanNode].booleanValue()
      case _: ArrayNode => convertToVelocityContext(obj.asInstanceOf[ArrayNode])
      case _: ObjectNode => convertToVelocityContext(obj.asInstanceOf[ObjectNode])
      case _: NullNode | null => null
      case _: ValueNode => obj.asInstanceOf[ValueNode].asText()
      case _ => obj.toString
    }
  }

  /**
    * --------- END ------------------
    */
}
