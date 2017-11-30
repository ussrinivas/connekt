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
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.WAContactService
import com.flipkart.connekt.receptors.routes.BaseJsonHandler

import scala.collection.JavaConverters._
import scala.concurrent.Future

class WAContactRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  private lazy implicit val stencilService = ServiceFactory.getStencilService
  private lazy val messageService = ServiceFactory.getMessageService(Channel.PUSH)

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("whatsapp" / "checkcontact" / Segment) {
            (appName: String) =>
              pathEndOrSingleSlash {
                post {
                  meteredResource("checkcontact") {
                    authorize(user, "CHECK_CONTACT", s"CHECK_CONTACT_$appName") {
                      entity(as[ObjectNode]) { obj =>
                        complete {
                          Future {
                            profile(s"whatsapp.post.checkcontact.$appName") {
                              val destinations = obj.get("destinations").asInstanceOf[ArrayNode].elements().asScala.map(_.asText).toSet
                              GenericResponse(StatusCodes.OK.intValue, null, Response(s"WACheckContactService for destinations ${destinations.mkString(",")}", WAContactService.instance.gets(appName, destinations).get))
                            }
                          }(ioDispatcher)
                        }
                      }
                    }
                  }
                }
              } ~ pathPrefix(Segment) {
                (destination: String) =>
                  pathEndOrSingleSlash {
                    post {
                      meteredResource("checkcontact") {
                        authorize(user, "CHECK_CONTACT", s"CHECK_CONTACT_$appName") {
                          complete {
                            Future {
                              profile(s"whatsapp.get.checkcontact.$appName") {
                                Thread.sleep(2000)
                                GenericResponse(StatusCodes.OK.intValue, null, Response(s"WACheckContactService for destination $destination", appName))

//                                WAContactService.instance.get(appName, destination).get match {
//                                  case Some(wa) => GenericResponse(StatusCodes.OK.intValue, null, Response(s"WACheckContactService for destination $destination", wa))
//                                  case None => GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"No mapping found for destination $destination", null))
//                                }
                              }
                            }(ioDispatcher)
                          }
                        }
                      }
                    }
                  }
              }
          }
        }
    }
}
