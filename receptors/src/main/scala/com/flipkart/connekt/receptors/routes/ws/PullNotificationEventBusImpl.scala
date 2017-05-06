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
package com.flipkart.connekt.receptors.routes.ws

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.event.{EventBus, SubchannelClassification}
import akka.pattern.ask
import akka.util.{Subclassification, Timeout}
import com.flipkart.connekt.commons.iomodels.ConnektRequest


private class PullNotificationEventBusImpl extends EventBus with SubchannelClassification {

  private implicit val publishTimeout = Timeout(5,TimeUnit.SECONDS)

  override type Event = ConnektRequest
  override type Classifier = String
  override type Subscriber = ActorRef

  // Subclassification is an object providing `isEqual` and `isSubclass`
  // to be consumed by the other methods of this classifier
  override protected val subclassification: Subclassification[Classifier] = new TreePathSubClassification

  // is used for extracting the classifier from the incoming events
  override protected def classify(event: Event): Classifier = event.clientId

  // will be invoked for each event for all subscribers which registered themselves
  // for the event’s classifier
  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ? event
  }

}

/**
  * Matches subscribers at /base/segment to publish at /base or /base/segment
  */
private class TreePathSubClassification extends Subclassification[String] {
  override def isEqual(x: String, y: String): Boolean = x == y

  override def isSubclass(x: String, y: String): Boolean = {
    y.startsWith(x)
  }
}
