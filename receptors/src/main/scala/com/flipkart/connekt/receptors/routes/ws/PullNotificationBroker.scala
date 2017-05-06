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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.FlowShape
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source}
import akka.{Done, NotUsed}
import com.flipkart.connekt.commons.iomodels.ConnektRequest
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Failure, Success}

object PullNotificationBroker {

  implicit val system = ActorSystem("pull-broker")
  implicit val ec = system.dispatcher

  private val subscribers = mutable.HashMap[String,mutable.ListBuffer[ActorRef]]()

  def getWSFlow(destination:String) :  Flow[Message, Message, NotUsed]= {

    val jobManagerSource = Source.actorPublisher[ConnektRequest](Props(classOf[PullNotificationPublisher],destination)).mapMaterializedValue { actorRef =>
      val buf = subscribers.getOrElseUpdate(destination,mutable.ListBuffer.empty[ActorRef])
      buf += actorRef
      println(s"$destination actorPublisher Created, Current Size : ${buf.size}")
      actorRef
    }.watchTermination() {  case (mat,end)  =>
      end.onSuccess {
        case _ =>
          val buf = subscribers(destination)
          buf -= mat
          println(s"$destination actorPublisher Terminated, Current Size : ${buf.size}")
      }
      mat
    }.map( connektRequest => TextMessage(connektRequest.getJson))

    val processor = Flow[Message].map {
      case TextMessage.Strict(txt) => TextMessage("ECHO: " + txt)
      case _ => TextMessage("Message type unsupported")
    }.watchTermination() {  case (mat,end)  =>
      end.onSuccess {
        case _ =>
          println( s"$destination Flow Terminated")
      }
      mat
    }

    val customFlow = Flow.fromGraph(GraphDSL.create() { implicit b =>
      val upstream = b.add(processor)
      val downstream = b.add(jobManagerSource)
      val merge = b.add(Merge[Message](2, eagerComplete = true))

      upstream.out ~> merge.in(0)
      downstream.out ~> merge.in(1)

      FlowShape(upstream.in, merge.out)
    })

    customFlow
  }

}


private class PullNotificationPublisher(clientId:String) extends ActorPublisher[ConnektRequest] {

  import akka.stream.actor.ActorPublisherMessage._

  val MaxBufferSize = 100
  var buf = Vector.empty[ConnektRequest]

  override def receive: Receive = {
    case _: ConnektRequest if buf.size == MaxBufferSize =>
      sender() ! Failure(new Throwable("Buffer Size Excedded"))
    case request: ConnektRequest =>
      sender() ! Success(Done)
      if (buf.isEmpty && totalDemand > 0)
        onNext(request)
      else {
        buf :+= request
        deliverBuf()
      }

    case Request(_) =>
      deliverBuf()
    case Cancel =>
      context.stop(self)
  }

  override def preStart(): Unit = {
    PullNotificationBus.subscribe(clientId, self)
    println(s"Actor for $clientId Created")
    super.preStart()
  }

  override def postStop(): Unit = {
    PullNotificationBus.unsubscribe(clientId, self)
    println(s"Actor for $clientId Terminated")
    super.postStop()
  }

  @tailrec final def deliverBuf(): Unit =
    if (totalDemand > 0) {
      /*
       * totalDemand is a Long and could be larger than
       * what buf.splitAt can accept
       */
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        use foreach onNext
      } else {
        val (use, keep) = buf.splitAt(Int.MaxValue)
        buf = keep
        use foreach onNext
        deliverBuf()
      }
    }

}
