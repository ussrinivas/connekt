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
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.receptors.directives.ChannelSegment
import com.flipkart.connekt.receptors.routes.BaseJsonHandler

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}

class RetryRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private lazy implicit val stencilService = ServiceFactory.getStencilService
  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")
  private val resendTimeout =  ConnektConfig.getInt("timeout.resend").getOrElse(5000).millis

  val route =
    authenticate {
      user =>
        pathPrefix("v1" / "resend") {
          path(ChannelSegment / Segment / Segment) {
            (channel: Channel, appName: String, messageId: String) =>
              authorize(user, "SEND_" + appName) {
                withRequestTimeout(resendTimeout) {
                  post {
                    complete {
                      Future {
                        val data = ServiceFactory.getMessageService(channel).getRequestInfo(messageId).get
                        data match {
                          case None =>
                            GenericResponse(StatusCodes.NotFound.intValue, Map("messageId" -> messageId), Response(s"No Message found for messageId $messageId.", null))
                          case Some(connektRequest) if connektRequest.clientId == user.userId =>
                            val queueName = ServiceFactory.getMessageService(channel).getRequestBucket(connektRequest, user)
                            ServiceFactory.getMessageService(channel).saveRequest(connektRequest, queueName, persistPayloadInDataStore = true) match {
                              case Success(id) =>
                                GenericResponse(StatusCodes.Accepted.intValue, Map("messageId" -> messageId), SendResponse(s"Retried for original messageId: $messageId.", Map(id -> connektRequest.destinations), List.empty))
                              case Failure(t) =>
                                GenericResponse(StatusCodes.InternalServerError.intValue, Map("messageId" -> messageId), Response(s"Internal server error: $messageId.", Map("debug" -> t.getMessage)))
                            }
                          case _ =>
                            GenericResponse(StatusCodes.BadRequest.intValue, Map("messageId" -> messageId), Response(s"You can only retry your own messages.", null))
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
