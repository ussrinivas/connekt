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
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.WAContactService
import com.flipkart.connekt.commons.utils.StringUtils.JSONMarshallFunctions
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.routes.helper.PhoneNumberHelper

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

case class WAContactResponse(contactExists: List[String], contactNotExists: List[String], invalidNumbers: List[String])

class WAContactRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  private lazy implicit val stencilService = ServiceFactory.getStencilService
  private lazy val contactService = ServiceFactory.getContactService

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
                              val formattedDestination = ListBuffer[String]()
                              val invalidDestinations = ListBuffer[String]()
                              destinations.foreach(d => {
                                PhoneNumberHelper.validateNFormatNumber(appName, d) match {
                                  case Some(n) => formattedDestination += n
                                  case None => invalidDestinations += d
                                }
                              })
                              val details = WAContactService.instance.gets(appName, formattedDestination.toSet).get
                              val checkedNumbers = details.map(d => {
                                d.destination
                              })
                              val toCheckNumbers = formattedDestination.diff(checkedNumbers)
                              GenericResponse(StatusCodes.OK.intValue, null, Response(WAContactResponse(checkedNumbers, toCheckNumbers.toList, invalidDestinations.toList).getJson, details))
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
                    get {
                      meteredResource("checkcontact") {
                        authorize(user, "CHECK_CONTACT", s"CHECK_CONTACT_$appName") {
                          complete {
                            Future {
                              profile(s"whatsapp.get.checkcontact.$appName") {
                                PhoneNumberHelper.validateNFormatNumber(appName, destination) match {
                                  case Some(n) =>
                                    WAContactService.instance.get(appName, n).get match {
                                      case Some(wa) => GenericResponse(StatusCodes.OK.intValue, null, Response(s"WACheckContactService for destination $destination", wa))
                                      case None =>
                                        contactService.enqueueContactEvents(ContactPayload(n, appName))
                                        GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"No mapping found for destination $destination enqueued for whatsapp check.", null))
                                    }
                                  case None =>
                                    ConnektLogger(LogFile.PROCESSORS).error(s"Dropping whatsapp invalid numbers: $destination")
                                    complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Dropping whatsapp invalid numbers $destination", null)))
                                }
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
