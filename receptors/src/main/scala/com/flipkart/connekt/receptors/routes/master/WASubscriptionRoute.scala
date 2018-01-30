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
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.{ConnektConfig, GuardrailService}
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.receptors.routes.helper.{PhoneNumberHelper, WAContactCheckHelper}
import scala.concurrent.Future
import scala.util.{Failure, Success}

class WASubscriptionRoute (implicit am: ActorMaterializer) extends BaseJsonHandler {

  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")
  private val welcomeDefualtMsgStencil = ConnektConfig.getString("whatsapp.welcome.default.message.stencil")

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("whatsapp" / "subscription" / Segment / Segment) {
            (appName: String, destination: String) =>
              pathEndOrSingleSlash {
                put {
                  meteredResource("waSubscription") {
                    authorize(user, "WA_SUBSCRIPTION", s"WA_SUBSCRIPTION_$appName") {
                      entity(as[WASubscriptionRequest]) { subRequest =>
                        complete {
                          Future {
                            PhoneNumberHelper.validateNFormatNumber(appName, destination) match {
                              case Some(validNumber) =>
                                GuardrailService.modifyGuard[String, Any, Map[String, Boolean]](appName, Channel.WA, validNumber, subRequest.asMap.asInstanceOf[Map[String, AnyRef]]) match {
                                  case Success(subResp) =>
                                    if (subResp("firstTimeUser") && subRequest.subscription == SubscriptionValues.SUBS) {
                                      val waValidUsers = WAContactCheckHelper.checkContact(appName, Set(validNumber))._1
                                      val number = waValidUsers.headOption match {
                                        case Some(n) =>
                                          val channelInfo = WARequestInfo(appName = appName, destinations = Set(n.userName))
                                          val channelData = WARequestData(waType = WAType.hsm)
                                          val meta = WAMetaData(appName, subRequest.bucket, subRequest.subBucket, subRequest.source, subRequest.accountId).asMap.asInstanceOf[Map[String, String]]
                                          val connektRequest = ConnektRequest(generateUUID, user.userId, Some("WELCOME"), Channel.WA.toString, "H", welcomeDefualtMsgStencil, None, None, channelInfo, channelData, StringUtils.getObjectNode, meta)
                                          val queueName = ServiceFactory.getMessageService(Channel.WA).getRequestBucket(connektRequest, user)
                                          ServiceFactory.getMessageService(Channel.WA).saveRequest(connektRequest, queueName)
                                        case None =>
                                          ConnektLogger(LogFile.SERVICE).error(s"User is not available in whatsapp, $validNumber ")
                                      }
                                    }
                                    GenericResponse(StatusCodes.OK.intValue, null, Response(s"WA subscription has been set to : ${subRequest.subscription}, for destination : $validNumber", null))
                                  case Failure(f) =>
                                    ConnektLogger(LogFile.SERVICE).error(s"Subscription update Failed ")
                                    GenericResponse(StatusCodes.BadRequest.intValue, null, Response("Subscription update Failed.", f))
                                }
                              case None =>
                                GenericResponse(StatusCodes.BadRequest.intValue, null, Response("No valid destinations found", destination))
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
