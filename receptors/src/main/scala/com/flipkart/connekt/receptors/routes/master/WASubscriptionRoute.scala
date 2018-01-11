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
import com.flipkart.connekt.commons.services.{ConnektConfig, GuardrailService, WAContactService}
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.receptors.routes.helper.{PhoneNumberHelper, WAContactCheckHelper}
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}

class WASubscriptionRoute (implicit am: ActorMaterializer) extends BaseJsonHandler {
  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  def WELCOME_STENCIL = ConnektConfig.getOrElse("whatsapp.welcome.stencil","STNR9N6VD")
  private val checkContactInterval = ConnektConfig.getInt("wa.check.contact.interval.days").get

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("whatsapp" / "subscription" / Segment) {
            (appName: String) =>
              pathPrefix(Segment) {
                (destination: String) =>
                  pathEndOrSingleSlash {
                    put {
                      meteredResource("waSubscription") {
                        authorize(user, "WA_SUBSCRIPTION", s"WA_SUBSCRIPTION_$appName") {
                          entity(as[WASubscriptionRequest]) { subRequest =>
                            complete {
                              Future {
                                PhoneNumberHelper.validateNFormatNumber(appName, destination) match {
                                  case Some(validNumber) =>
                                    GuardrailService.upsertSubscription[String, Any](appName, Channel.WA, validNumber, subRequest.asMap.asInstanceOf[Map[String, AnyRef]]) match {
                                      case Success(subResp) =>
                                        if (subResp.asInstanceOf[Map[String,Any]]("firstTimeUser").toString.toBoolean) {
                                          val number = WAContactService.instance.get(appName, destination).get match {
                                            case Some(wa) if (System.currentTimeMillis() - wa.lastCheckContactTS) < checkContactInterval.days.toMillis =>
                                              wa.userName
                                            case _ =>
                                              val contactWAStatus = WAContactCheckHelper.checkContactViaWAApi(List(destination), appName)
                                              contactWAStatus.head.userName
                                          }
                                          val channelInfo = WARequestInfo(appName = appName, destinations = Set(number))
                                          val channelData = WARequestData(waType = WAType.hsm)
                                          val channelDataModel = StringUtils.getObjectNode
                                          channelDataModel.put("pickupTime", "10AM")
                                          channelDataModel.put("orderId", "ankit-order-id")
                                          val meta = Map("bucket" -> "transactional", "subBucket" -> "orderRelated", "domain" -> "flipkart")
                                          val connektRequest = new ConnektRequest(generateUUID, "whatspp", Some("WELCOME"), "wa", "H", Some(WELCOME_STENCIL), None, None, channelInfo, channelData, channelDataModel, meta)
                                          val queueName = ServiceFactory.getMessageService(Channel.WA).getRequestBucket(connektRequest, user)
                                          ServiceFactory.getMessageService(Channel.WA).saveRequest(connektRequest, queueName, persistPayloadInDataStore = true)
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
}
