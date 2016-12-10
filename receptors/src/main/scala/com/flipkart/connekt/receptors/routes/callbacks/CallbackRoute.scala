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
package com.flipkart.connekt.receptors.routes.callbacks

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.databind.node.{BaseJsonNode, ObjectNode}
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.entities.MobilePlatform._
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.receptors.directives.MPlatformSegment
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.wire.ResponseUtils._
import org.apache.commons.lang.RandomStringUtils

import scala.collection.JavaConverters._
import scala.util.Try

class CallbackRoute(implicit am: ActorMaterializer) extends BaseJsonHandler with PredefinedFromEntityUnmarshallers {

  private lazy implicit val stencilService = ServiceFactory.getStencilService

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("push") {
            path("callback" / MPlatformSegment / Segment / Segment) {
              (appPlatform: MobilePlatform, appName: String, deviceId: String) =>
                meteredResource(s"saveEvent.$appPlatform.$appName") {
                  verifySecureCode(appName.toLowerCase, user.apiKey, deviceId) {
                    authorize(user, "ADD_EVENTS", s"ADD_EVENTS_$appName") {
                      post {
                        entity(as[PNCallbackEvent]) { e =>
                          val event = e.copy(messageId = Option(e.messageId).orEmpty, eventId = RandomStringUtils.randomAlphabetic(10), clientId = user.userId, contextId = Option(e.contextId).orEmpty, platform = appPlatform.toString, appName = appName, deviceId = deviceId, eventType = Option(e.eventType).map(_.toLowerCase).orNull)
                          event.validate()
                          event.persist
                          ServiceFactory.getReportingService.recordPushStatsDelta(user.userId, Option(e.contextId), None, Some(event.platform), event.appName, event.eventType)
                          ConnektLogger(LogFile.SERVICE).debug(s"Received callback event ${event.toString}")
                          complete(GenericResponse(StatusCodes.OK.intValue, null, Response("PN callback saved successfully.", null)))
                        }
                      }
                    }
                  }
                }
            } ~ path("callbacks" / MPlatformSegment / Segment / Segment) {
              (appPlatform: MobilePlatform, appName: String, deviceId: String) =>
                meteredResource(s"saveEvents.$appPlatform.$appName") {
                  verifySecureCode(appName.toLowerCase, user.apiKey, deviceId) {
                    authorize(user, "ADD_EVENTS", s"ADD_EVENTS_$appName") {
                      post {
                        entity(as[Array[PNCallbackEvent]]) { rawEvents =>

                          val validEvents = rawEvents.flatMap(event => {
                            Try {
                              val e = event.copy(messageId = Option(event.messageId).orEmpty, eventId = RandomStringUtils.randomAlphabetic(10), clientId = user.userId, contextId = Option(event.contextId).orEmpty, platform = appPlatform.toString, appName = appName, deviceId = deviceId, eventType = Option(event.eventType).map(_.toLowerCase).orNull)
                              e.validate()
                              Some(e)
                            }.getOrElse(None)
                          }).toList

                          validEvents.persist

                          validEvents.foreach(event => {
                            ServiceFactory.getReportingService.recordPushStatsDelta(user.userId, Some(event.contextId), None, Some(event.platform), event.appName, event.eventType)
                          })

                          ConnektLogger(LogFile.SERVICE).debug(s"Received callback events ${validEvents.getJson}")
                          complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Batch PN callback request recieved.", s"Events successfully ingested : ${validEvents.length}")))
                        }
                      }
                    }
                  }
                }
            } ~ path("callback" / MPlatformSegment / Segment / Segment / Segment) {
              (appPlatform: MobilePlatform, appName: String, contactId: String, messageId: String) =>
                meteredResource(s"deleteEvents.$appPlatform.$appName") {
                  authorize(user, s"DELETE_EVENTS_$appName") {
                    delete {
                      ConnektLogger(LogFile.SERVICE).debug(s"Received event delete request for: ${messageId.toString}")
                      val deletedEvents = ServiceFactory.getCallbackService.deleteCallBackEvent(messageId, s"${appName.toLowerCase}$contactId", Channel.PUSH)
                      complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"PN callback events deleted successfully for requestId: $messageId.", deletedEvents)))
                    }
                  }
                }
            }
          } ~ pathPrefix("email") {
            path("callbacks" / Segment / Segment) {
              (appName: String, providerName: String) =>
                authorize(user, "ADD_EVENTS", s"ADD_EVENTS_$appName") {
                  meteredResource(s"saveEvent.email.$appName") {
                    extractRequestContext { ctx =>
                      (post | get) {
                        parameterMap { urlParams =>

                          val payload: ObjectNode = ctx.request.entity.getString match {
                            case x if x.isEmpty => Map("get" -> urlParams).getJsonNode
                            case y => Map("post" -> y.getObj[BaseJsonNode]).getJsonNode
                          }

                          val stencil = stencilService.getStencilsByName(s"ckt-email-$providerName").find(_.component.equalsIgnoreCase("webhook")).get
                          val rawEvents = stencilService.materialize(stencil, payload).asInstanceOf[java.util.ArrayList[EmailCallbackEvent]].asScala

                          val validEvents = rawEvents.flatMap(event => {
                            Try {
                              val e = event.copy(messageId = Option(event.messageId).orEmpty, eventId = RandomStringUtils.randomAlphabetic(10), clientId = Option(event.clientId).getOrElse(user.userId), appName = appName, contextId = Option(event.contextId).orEmpty, eventType = Option(event.eventType).map(_.toLowerCase).orNull)
                              e.validate()
                              Some(e)
                            }.getOrElse(None)
                          }).toList

                          validEvents.persist

                          validEvents.foreach(event => {
                            ServiceFactory.getReportingService.recordPushStatsDelta(user.userId, Some(event.contextId), None, Some(Channel.EMAIL), event.appName, event.eventType)
                          })

                          ConnektLogger(LogFile.SERVICE).debug(s"Received callback events ${validEvents.getJson}")
                          complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Email callbacks request recieved.", s"Events successfully ingested : ${validEvents.length}")))

                        }
                      }
                    }
                  }
                }
            }
          } ~ pathPrefix("sms") {
            path("callback" / Segment) {
              (provider: String) =>
                meteredResource(s"sms.saveEvent.$provider") {
                  authorize(user, "ADD_EVENTS") {
                    extractRequestContext { ctx =>
                      (post | get) {
                        parameterMap { urlParams =>

                          val payload: ObjectNode = ctx.request.entity.getString match {
                            case x if x.isEmpty => Map("get" -> urlParams).getJsonNode
                            case y => Map("post" -> y.getObj[BaseJsonNode]).getJsonNode
                          }

                          val stencil = stencilService.getStencilsByName(s"ckt-sms-$provider").find(_.component.equalsIgnoreCase("webhook")).get
                          val rawEvents = stencilService.materialize(stencil, payload).asInstanceOf[java.util.ArrayList[SmsCallbackEvent]].asScala

                          val validEvents = rawEvents.flatMap(event => {
                            Try {
                              val e = event.copy(messageId = Option(event.messageId).orEmpty, eventId = RandomStringUtils.randomAlphabetic(10), clientId = Option(event.clientId).getOrElse(user.userId), appName = event.appName, contextId = Option(event.contextId).orEmpty, eventType = Option(event.eventType).map(_.toLowerCase).orNull)
                              e.validate()
                              Some(e)
                            }.getOrElse(None)
                          }).toList

                          validEvents.persist

                          validEvents.foreach(event => {
                            ServiceFactory.getReportingService.recordPushStatsDelta(user.userId, Some(event.contextId), None, Some(Channel.SMS), event.appName, event.eventType)
                          })

                          ConnektLogger(LogFile.SERVICE).debug(s"Received callback events ${validEvents.getJson}")
                          complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Sms callbacks request recieved.", s"Events successfully ingested : ${validEvents.length}")))

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
