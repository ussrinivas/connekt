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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.WebSocketDirectives
import akka.stream._
import com.flipkart.connekt.commons.iomodels.ConnektRequest
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.io.StdIn

object SimpleWSServer extends WebSocketDirectives {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val route =
      path("hello") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      } ~ path("realtime" / Segment / Segment ) {  (userId, deviceId) =>
        get {
          handleWebSocketMessages(PullNotificationBroker.getWSFlow(s"$userId/$deviceId"))
        } ~ post {
          val c = s"""
                     |{ "id" : "123456789",
                     |	"channel": "EMAIL",
                     |	"sla": "H",
                     |	"channelData": {
                     |		"type": "EMAIL",
                     |		"subject": "Hello Kinshuk. GoodLuck!",
                     |		"text": "Text",
                     |    "html" : "<b>html</b>"
                     |	},
                     |	"channelInfo" : {
                     |	    "type" : "EMAIL",
                     |     "appName" : "FKProd",
                     |     "to" : [{ "name": "Kinshuk", "address": "kinshuk1989@gmail.com" }]
                     |	},
                     | "clientId" : "123456"
                     |}
                   """.stripMargin.getObj[ConnektRequest]

          PullNotificationBus.publish(c.copy(clientId = userId))
          PullNotificationBus.publish(c.copy(clientId = s"$userId/$deviceId"))
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Done"))

        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
