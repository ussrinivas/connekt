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
package com.flipkart.connekt.commons.tests.services

import com.flipkart.connekt.commons.entities.{Channel, ExclusionDetails, ExclusionEntity, ExclusionType}
import com.flipkart.connekt.commons.services.ExclusionService
import com.flipkart.connekt.commons.tests.CommonsBaseTest


class ExclusionServiceTest extends CommonsBaseTest {


  "ExclusionService" should "do cached lookup" in {
    ExclusionService.lookup(Channel.EMAIL.toString,"lorem", "jane@doe.com").get
    val result = ExclusionService.lookup(Channel.EMAIL.toString,"lorem", "jane@doe.com").get
    assert( result  != null)
  }


  "ExclusionService" should "add and lookup" in {
    ExclusionService.add(ExclusionEntity(Channel.EMAIL.toString,"lorem", "dummy@doe.com" ,ExclusionDetails(ExclusionType.LONG_TERM))).get
    val result = ExclusionService.lookup(Channel.EMAIL.toString,"lorem", "dummy@doe.com").get
    println(result)
    assert( result  == false)
  }

  "ExclusionService" should "fail lookup" in {
    val result = ExclusionService.lookup(Channel.EMAIL.toString,"lorem", "john&jane@doe.com").get
    assert( result  == true)
  }


}
