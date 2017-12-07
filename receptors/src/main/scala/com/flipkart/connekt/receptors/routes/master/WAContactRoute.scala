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
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.{ConnektConfig, WAContactService}
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.routes.helper.{PhoneNumberHelper, WAContactCheckHelper}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._

case class WAContactResponse(contactDetails: Any, invalidNumbers: List[String])

class WAContactRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  private lazy implicit val stencilService = ServiceFactory.getStencilService
  private lazy val contactService = ServiceFactory.getContactService
  private val baseUrl = ConnektConfig.getString("wa.base.uri").get
  private val checkContactInterval = ConnektConfig.getInt("wa.check.contact.interval.days").get

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
                              val allDetails = WAContactService.instance.gets(appName, formattedDestination.toSet).get
                              val ttlCheckedNumbers = allDetails.filter(d => {
                                val lastCheckInterval = System.currentTimeMillis() - d.lastCheckContactTS
                                lastCheckInterval < checkContactInterval.days.toMillis
                              })
                              val toCheckNumbers = formattedDestination.diff(ttlCheckedNumbers.map(_.destination))
                              val contactWAStatus = WAContactCheckHelper.checkContactViaWAApi(toCheckNumbers.toList, appName)
                              GenericResponse(StatusCodes.OK.intValue, null, Response("Some message", WAContactResponse(ttlCheckedNumbers ::: contactWAStatus, invalidDestinations.toList)))
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
                                      case Some(wa) =>
                                        val lastCheckInterval = System.currentTimeMillis() - wa.lastCheckContactTS
                                        if (lastCheckInterval < checkContactInterval.days.toMillis)
                                          GenericResponse(StatusCodes.OK.intValue, null, Response(s"WA status for destination $destination", wa))
                                        else {
                                          val contactWAStatus = WAContactCheckHelper.checkContactViaWAApi(List(n), appName)
                                          GenericResponse(StatusCodes.OK.intValue, null, Response(s"WA status for destination $destination", contactWAStatus.head))
                                        }
                                      case None =>
                                        val contactWAStatus = WAContactCheckHelper.checkContactViaWAApi(List(n), appName)
                                        GenericResponse(StatusCodes.OK.intValue, null, Response(s"WA status for destination $destination", contactWAStatus.head))
                                    }
                                  case None =>
                                    ConnektLogger(LogFile.PROCESSORS).error(s"Dropping whatsapp invalid numbers: $destination")
                                    GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Dropping whatsapp invalid numbers $destination", null))
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
