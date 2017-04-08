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
package com.flipkart.connekt.receptors.routes.common

import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.entities.UserProjectConfig
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels.{GenericResponse, Response}
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.wire.ResponseUtils._

class ProjectConfigRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("project" / "config" / Segment) {
            appName: String =>
              authorize(user, "ADMIN_PROJECT", s"ADMIN_PROJECT_$appName") {
                pathEndOrSingleSlash {
                  get {
                    val properties = ServiceFactory.getUserProjectConfigService.getAllProjectConfigurations(appName).get
                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Project $appName's configs fetched.", properties)))
                  }
                } ~ path(Segment) {
                  (propertyName: String) =>
                    get {
                      ServiceFactory.getUserProjectConfigService.getProjectConfiguration(appName, propertyName).get match {
                        case Some(data) =>
                          complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Project $appName/$propertyName's config fetched.", data)))
                        case None =>
                          complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"Project $appName/$propertyName's config not defined.", null)))
                      }
                    } ~ put {
                      entity(as[UserProjectConfig]) { projectConfig =>
                        projectConfig.updatedBy = user.userId
                        projectConfig.createdBy = user.userId
                        projectConfig.name = propertyName
                        projectConfig.appName = appName
                        projectConfig.validate()
                        ServiceFactory.getUserProjectConfigService.add(projectConfig).get
                        complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Project $appName/${projectConfig.name}'s config has been added.", projectConfig)))
                      }
                    }
                }
              }
          }
        }
    }
}
