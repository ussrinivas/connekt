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
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.GuardrailService
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.commons.utils.StringUtils.ObjectHandyFunction
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.routes.helper.{PhoneNumberHelper, WAContactCheckHelper}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class WABulkCheckContactResponse(contactDetails: List[WACheckContactResp], invalidNumbers: List[String])

case class WACheckContactResp(destination: String, exists: String, @JsonInclude(Include.NON_NULL) subscribed: Any)

case class WABulkCheckContactRequest(@JsonProperty(required = true) destinations: Set[String],
                                     bucket: String,
                                     subBucket: String)

class WAContactRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  private lazy implicit val stencilService = ServiceFactory.getStencilService

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
                      entity(as[WABulkCheckContactRequest]) { obj =>
                        complete {
                          Future {
                            profile(s"whatsapp.post.checkcontact.$appName") {
                              val bucket = obj.bucket
                              val subBucket = obj.subBucket
                              val bucketsNonEmpty = !(StringUtils.isNullOrEmpty(bucket) || StringUtils.isNullOrEmpty(subBucket))
                              require(bucketsNonEmpty || (bucket == null && subBucket == null), "Both bucket and subBucket should be non empty")

                              val destinations = obj.destinations
                              val formattedDestination = ListBuffer[String]()
                              val invalidDestinations = ListBuffer[String]()
                              destinations.foreach(d => {
                                PhoneNumberHelper.validateNFormatNumber(appName, d) match {
                                  case Some(n) => formattedDestination += n
                                  case None => invalidDestinations += d
                                }
                              })
                              val (waPresentUsers, waAbsentUsers) = WAContactCheckHelper.checkContact(appName, formattedDestination.toSet)
                              val meta = WAMetaData(appName, bucket, subBucket).asMap.asInstanceOf[Map[String, String]]
                              val waAllUsers = waPresentUsers ::: waAbsentUsers
                              val waBulkCheckContactResponse = waAllUsers.map(w => {
                                val subscribed = if (bucketsNonEmpty) {
                                  val meta = WAMetaData(appName, bucket, subBucket).asMap.asInstanceOf[Map[String, String]]
                                  GuardrailService.isGuarded[String, Boolean, Map[_, _]](appName, Channel.WA, w.destination, meta) match {
                                    case Success(sub) => !sub
                                    case Failure(_) => false
                                  }
                                } else null
                                WACheckContactResp(w.destination, w.exists, subscribed)
                              })
                              GenericResponse(StatusCodes.OK.intValue, null, Response("WA status for destinations", WABulkCheckContactResponse(waBulkCheckContactResponse, invalidDestinations.toList)))
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
                          parameterMap { params =>
                            val bucket = params.getOrElse("bucket", "")
                            val subBucket = params.getOrElse("subBucket", "")
                            val bucketsNonEmpty = !(StringUtils.isNullOrEmpty(bucket) || StringUtils.isNullOrEmpty(subBucket))
                            require(bucketsNonEmpty || params.isEmpty, "Both bucket and subBucket should be non empty")
                            complete {
                              Future {
                                profile(s"whatsapp.get.checkcontact.$appName") {
                                  PhoneNumberHelper.validateNFormatNumber(appName, destination) match {
                                    case Some(n) =>
                                      val subscribed = if (bucketsNonEmpty) {
                                        val meta = WAMetaData(appName, bucket, subBucket).asMap.asInstanceOf[Map[String, String]]
                                        GuardrailService.isGuarded[String, Boolean, Map[_, _]](appName, Channel.WA, n, meta) match {
                                          case Success(sub) => !sub
                                          case Failure(_) => false
                                        }
                                      } else null
                                      val (waPresentUsers, waAbsentUsers) = WAContactCheckHelper.checkContact(appName, Set(n))
                                      val waDetails = (waPresentUsers ::: waAbsentUsers).head
                                      GenericResponse(StatusCodes.OK.intValue, null, Response(s"WA status for destination $destination", WACheckContactResp(destination, waDetails.exists, subscribed)))
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
}
