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

import com.flipkart.connekt.commons.entities._
import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.tests.CommonsBaseTest

class SubscriptionDaoTest extends CommonsBaseTest {

  val subscription = new Subscription()

  subscription.name = "SubscriptionDaoTest"
  subscription.id = UUID.randomUUID().toString
  subscription.shutdownThreshold = 4
  subscription.createdBy = "connekt-insomnia"
  subscription.sink = new HTTPEventSink("POST","http://requestb.in/wis41kwi")
  subscription.stencilId = "testEventFilter"

  "add test" should "not throw exception" in {
    noException should be thrownBy DaoFactory.getSubscriptionDao.add(subscription)
  }

  "update test" should "not throw exception" in {
    subscription.name = "UpdatedSubscriptionName"
    subscription.shutdownThreshold = 2
    subscription.stencilId = "updatedEventFilter"
    noException should be thrownBy DaoFactory.getSubscriptionDao.update(subscription)
  }

  "get test" should "return a instance of Subscription" in {
    assert(DaoFactory.getSubscriptionDao.get(subscription.id).get.isInstanceOf[Subscription])
  }

  "getFailure test" should "not return a Subscription" in {
    assert(DaoFactory.getSubscriptionDao.get(UUID.randomUUID().toString).isEmpty)
  }

  "delete test" should "not throw exception" in {
    noException should be thrownBy DaoFactory.getSubscriptionDao.delete(subscription.id)
  }


}
