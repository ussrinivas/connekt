package com.flipkart.connekt.receptors.routes.master

import akka.connekt.AkkaHelpers._
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.WACheckContactService
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.wire.ResponseUtils._

import scala.collection.JavaConverters._

class CheckContactRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  private lazy implicit val stencilService = ServiceFactory.getStencilService
  private lazy val messageService = ServiceFactory.getMessageService(Channel.PUSH)

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("wa" / "checkcontact" / Segment) {
            (appName: String) =>
              pathEndOrSingleSlash {
                post {
                  meteredResource("checkcontact") {
                    authorize(user, "CHECK_CONTACT", s"CHECK_CONTACT_$appName") {
                      entity(as[ObjectNode]) { obj =>
                        val destinations = obj.get("destinations").asInstanceOf[ArrayNode].elements().asScala.map(_.asText).toSet
                        complete {
                          GenericResponse(StatusCodes.OK.intValue, null, Response(s"WACheckContactService for destinations ${destinations.mkString(",")}", WACheckContactService.gets(appName, destinations).get))
                        }
                      }
                    }
                  }
                }
              } ~ pathPrefix(Segment) {
                (destination: String) =>
                  pathEndOrSingleSlash {
                    get {
                      meteredResource("checkcontact") {
                        authorize(user, "CHECK_CONTACT", s"CHECK_CONTACT_$appName") {
                          complete {
                            WACheckContactService.get(appName, destination).get match {
                              case Some(wa) => GenericResponse(StatusCodes.OK.intValue, null, Response(s"WACheckContactService for destination $destination", wa))
                              case None => GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"No mapping found for destination $destination", null))
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
}
