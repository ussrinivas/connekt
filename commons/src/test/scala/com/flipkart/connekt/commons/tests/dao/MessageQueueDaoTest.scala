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

  private val contactId = UUID.randomUUID().toString
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
    println(fetch)

    fetch.size shouldEqual 10

    println(Await.result( dao.trimMessages("testApp",contactId, 2), 30.seconds))
    println(Await.result( dao.trimMessages("testApp",contactId, 2), 30.seconds))
    println(Await.result( dao.trimMessages("testApp",contactId, 2), 30.seconds))
    println(Await.result( dao.trimMessages("testApp",contactId, 2), 30.seconds))

    val fetch2 = Await.result( dao.getMessages("testApp",contactId, None), 30.seconds)
    println(fetch2)
    fetch2.size shouldEqual 2

    println(Await.result( dao.trimMessages("testApp",contactId, 5), 30.seconds))
    val fetch3 = Await.result( dao.getMessages("testApp",contactId, None), 30.seconds)
    println(fetch3)

    fetch3.size shouldEqual 0

    Await.result(dao.trimMessages("testApp",contactId, 5), 30.seconds)
    Await.result(dao.getMessages("testApp",contactId, None), 30.seconds).size shouldEqual 0

  }


}
