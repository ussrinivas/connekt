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
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.wire.ResponseUtils._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}

class TemporaryRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private lazy implicit val stencilService = ServiceFactory.getStencilService
  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("send" / "pulls") {
            path(Segment) {
              (appName: String) => {
                authorize(user, "SEND_PULL", "SEND_" + appName) {
                  idempotentRequest(appName) {
                    post {
                      getXHeaders { headers =>
                        entity(as[Array[ConnektRequest]]) { arrCR =>
                          complete {
                            Future {
                              profile(s"sendDevicePulls.$appName") {

                                val success = scala.collection.mutable.Map[String, Set[String]]()
                                val failure = ListBuffer[String]()

                                arrCR.foreach { r =>

                                  val request = r.copy(clientId = user.userId, channel = "pull", meta = {
                                    Option(r.meta).getOrElse(Map.empty[String, String]) ++ headers
                                  }, channelInfo =
                                    r.channelInfo.asInstanceOf[PullRequestInfo].copy(appName = appName.toLowerCase)
                                  )
                                  ConnektLogger(LogFile.SERVICE).debug(s"Received PULL request with payload: ${request.toString}")

                                  val pullRequestInfo = request.channelInfo.asInstanceOf[PullRequestInfo]
                                  if (pullRequestInfo.userIds != null && pullRequestInfo.userIds.nonEmpty) {
                                    ServiceFactory.getPullMessageService.saveRequest(request) match {
                                      case Success(result) => success += result -> pullRequestInfo.userIds
                                      case Failure(t) => failure ++= pullRequestInfo.userIds
                                    }
                                  } else {
                                    ConnektLogger(LogFile.SERVICE).error(s"Request Validation Failed, $request ")
                                  }
                                }
                                if (success.nonEmpty) {
                                  val (responseCode, message) = if (success.nonEmpty) Tuple2(StatusCodes.Created, s"In App request processed") else Tuple2(StatusCodes.InternalServerError, s"In App request failed")
                                  GenericResponse(responseCode.intValue, null, SendResponse(message, success.toMap, failure.toList)).respond
                                } else {
                                  GenericResponse(StatusCodes.BadRequest.intValue, null, Response("Request Validation Failed, Please ensure mandatory field values.", null)).respond
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
}
