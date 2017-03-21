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
package com.flipkart.connekt.commons.tests.dao

import java.util.UUID

import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.tests.CommonsBaseTest
import com.flipkart.connekt.commons.utils.StringUtils

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

class MessageQueueDaoTest extends CommonsBaseTest {

  private val contactId = "cae73a32-cc99-429e-b024-aab8aeob5"
  private lazy val dao = DaoFactory.getMessageQueueDao

  override def beforeAll() = {
    super.beforeAll()
  }

  "PullMessageDao test" should "add data" in {
    val expiry = System.currentTimeMillis() + 5.minutes.toMillis

    11 to 20 foreach( i =>
      noException should be thrownBy Await.result(dao.enqueueMessage("testApp",contactId, StringUtils.generateRandomStr(6) + i.toString, expiry), 30.seconds)
    )

    val fetch = Await.result( dao.getMessages("testApp",contactId, None), 30.seconds)
    println(fetch.size)
    println(fetch)

    fetch.nonEmpty shouldEqual true


    println(Await.result( dao.trimMessages("testApp",contactId, 2), 30.seconds))
    println(Await.result( dao.trimMessages("testApp",contactId, 2), 30.seconds))
    println(Await.result( dao.trimMessages("testApp",contactId, 2), 30.seconds))
    println(Await.result( dao.trimMessages("testApp",contactId, 2), 30.seconds))


    println(Await.result( dao.getMessages("testApp",contactId, None), 30.seconds))


  }


}
