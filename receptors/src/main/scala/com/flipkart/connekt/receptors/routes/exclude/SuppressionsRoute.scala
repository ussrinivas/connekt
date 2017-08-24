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
package com.flipkart.connekt.receptors.routes.exclude

import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.entities.ExclusionType.ExclusionType
import com.flipkart.connekt.commons.entities.{ExclusionDetails, ExclusionEntity, ExclusionType}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.ExclusionService
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.receptors.directives.{ChannelSegment, ExclusionTypeSegment}
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.wire.ResponseUtils._

class SuppressionsRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("suppressions" / ChannelSegment / Segment) {
            (channel: Channel, appName: String) =>
              pathPrefix(ExclusionTypeSegment) {
                (exclusionType: ExclusionType) =>
                  authorize(user, "SUPPRESSIONS", s"SUPPRESSIONS_$appName") {
                    path(Segment) {
                      (destination: String) =>
                        (put | get) {
                          extractRequestContext { ctx =>
                            parameterMap { urlParams =>
                              val stringBody = ctx.request.entity.getString
                              val payload = if (stringBody.isEmpty) urlParams.getJson else stringBody
                              val eD = ExclusionDetails(ExclusionType.withName(exclusionType), metaInfo = payload)
                              ExclusionService.add(ExclusionEntity(channel, appName.toLowerCase, destination.trim, eD)).get
                              complete(GenericResponse(StatusCodes.Created.intValue, null, Response(s"Suppression request received for destination : $destination", null)))
                            }
                          }

                        }
                    } ~ path("all") {
                      get {
                        val results = ExclusionService.getAll(channel, appName.toLowerCase, exclusionType).get
                        complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Get Suppression list for channel `$channel`, appname `$appName` and exclusionType `$exclusionType`", results)))
                      }
                    }
                  }
              } ~ path(Segment) {
                (destination: String) =>
                  authorize(user, "SUPPRESSIONS", s"SUPPRESSIONS_$appName", s"SUPPRESSIONS_READ_$appName") {
                    get {
                      val details = ExclusionService.get(channel, appName.toLowerCase, destination.trim).get
                      if (details.nonEmpty)
                        complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Suppression get request received for destination : $destination", details)))
                      else
                        complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"No Suppressions found for destination : $destination", null)))
                    } ~ method(HttpMethods.TRACE) {
                      val notFiltered = ExclusionService.lookup(channel, appName.toLowerCase, destination.trim).get
                      complete {
                        HttpResponse(
                          status = {
                            if (notFiltered) StatusCodes.NotFound else StatusCodes.OK
                          },
                          headers = Nil,
                          entity = HttpEntity.Empty
                        )
                      }
                    }
                  } ~ delete {
                    authorize(user, "SUPPRESSIONS", s"SUPPRESSIONS_$appName") {
                      ExclusionService.delete(channel, appName.toLowerCase, destination.trim).get
                      complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Suppression remove request received for destination : $destination", null)))
                    }
                  }
              }
          }
        }
    }
}
