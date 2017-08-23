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
package com.flipkart.connekt.receptors.routes.master

import akka.connekt.AkkaHelpers._
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.iomodels.{GenericResponse, Response}
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.commons.factories.ServiceFactory
import scala.concurrent.duration._

class UpdateRoute (implicit am: ActorMaterializer) extends BaseJsonHandler {
  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("markAsRead" / "pull" / Segment / Segment) {
            (appName: String, userId: String) =>
              pathEndOrSingleSlash {
                post {
                  authorize(user, "markAsRead", s"markAsRead_$appName") {
                    parameters('client ? "", 'platform ? "", 'appVersion.as[String] ? "0") { (client, platform, appVersion) =>
                      val profiler = timer(s"markAsRead.$appName").time()
                      val filterOptions = Map("client" -> client, "platform" -> platform, "appVersion" -> appVersion)
                      ServiceFactory.getPullMessageService.markAsRead(appName, userId, System.currentTimeMillis - 90.days.toMillis, System.currentTimeMillis, filterOptions)
                      profiler.stop()
                      complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Updated messages for $userId", ("Status" -> "Success"))))
                    }
                  }
                }
              }
          }
        }
    }
}
