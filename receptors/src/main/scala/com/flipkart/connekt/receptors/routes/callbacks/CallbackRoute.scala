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
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.entities.MobilePlatform._
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.{ConnektConfig, WAMessageIdMappingService}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.receptors.directives.{ChannelSegment, MPlatformSegment}
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.wire.ResponseUtils._
import org.apache.commons.lang.RandomStringUtils

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class CallbackRoute(implicit am: ActorMaterializer) extends BaseJsonHandler with PredefinedFromEntityUnmarshallers {

  private val seenEventTypes = ConnektConfig.getList[String]("core.pn.seen.events").map(_.toLowerCase)

  private lazy implicit val stencilService = ServiceFactory.getStencilService

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix(ChannelSegment) { channel: Channel =>
            path("callback" / MPlatformSegment / Segment / Segment) {
              (appPlatform: MobilePlatform, appName: String, deviceId: String) =>
                meteredResource(s"saveEvent.$appPlatform.$appName") {
                  verifySecureCode(appName.toLowerCase, user.apiKey, deviceId) {
                    authorize(user, "ADD_EVENTS", s"ADD_EVENTS_$appName") {
                      post {
                        entity(as[PNCallbackEvent]) { e =>
                          val event = e.copy(messageId = Option(e.messageId).orEmpty, eventId = RandomStringUtils.randomAlphabetic(10), clientId = user.userId, contextId = Option(e.contextId).orEmpty, platform = appPlatform.toString, appName = appName, deviceId = deviceId, eventType = Option(e.eventType).map(_.toLowerCase).orNull)
                          event.validate()
                          event.enqueue

                          ServiceFactory.getReportingService.recordPushStatsDelta(user.userId, Option(e.contextId), None, Some(event.platform), event.appName, event.eventType)
                          if (seenEventTypes.contains(event.eventType.toLowerCase))
                            ServiceFactory.getMessageQueueService.removeMessage(appName, event.deviceId, event.messageId)

                          ConnektLogger(LogFile.SERVICE).debug(s"Received callback event {}", supplier(event.toString))
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

                          validEvents.enqueue

                          validEvents.foreach(event => {
                            ServiceFactory.getReportingService.recordPushStatsDelta(user.userId, Some(event.contextId), None, Some(event.platform), event.appName, event.eventType)
                            if (seenEventTypes.contains(event.eventType.toLowerCase))
                              ServiceFactory.getMessageQueueService.removeMessage(appName, event.deviceId, event.messageId)
                          })

                          ConnektLogger(LogFile.SERVICE).debug(s"Received callback events {}", supplier(validEvents.getJson))
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
                      ConnektLogger(LogFile.SERVICE).debug(s"Received event delete request for {}", supplier(messageId.toString))
                      val deletedEvents = ServiceFactory.getCallbackService.deleteCallBackEvent(messageId, s"${appName.toLowerCase}$contactId", Channel.PUSH)
                      complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"PN callback events deleted successfully for requestId: $messageId.", deletedEvents)))
                    }
                  }
                }
            } ~ path("callbacks" / Segment / Segment) {
              (appName: String, providerName: String) =>
                authorize(user, "ADD_EVENTS", s"ADD_EVENTS_$appName") {
                  meteredResource(s"saveEvent.$channel") {
                    (post | get) {
                      parameterMap { urlParams =>
                        entity(as[String](messageUnmarshallerFromEntityUnmarshaller(stringUnmarshaller))) { stringBody =>
                          val payload: ObjectNode = stringBody match {
                            case x if x.isEmpty => Map("get" -> urlParams).getJsonNode
                            case y => Map("post" -> y.getObj[BaseJsonNode]).getJsonNode
                          }
                          val stencil = stencilService.getStencilsByName(s"ckt-$channel-$providerName").find(_.component.equalsIgnoreCase("webhook")).get
                          val validEvents = channel match {
                            case Channel.EMAIL =>
                              stencilService.materialize(stencil, payload).asInstanceOf[java.util.ArrayList[EmailCallbackEvent]].asScala.flatMap(event => {
                                Try {
                                  val e = event.copy(messageId = Option(event.messageId).orEmpty, eventId = RandomStringUtils.randomAlphabetic(10), clientId = Option(event.clientId).getOrElse(user.userId), appName = appName, contextId = Option(event.contextId).orEmpty, eventType = Option(event.eventType).map(_.toLowerCase).orNull)
                                  e.validate()
                                  Some(e)
                                }.getOrElse(None)
                              }).toList
                            case Channel.SMS => stencilService.materialize(stencil, payload).asInstanceOf[java.util.ArrayList[SmsCallbackEvent]].asScala.flatMap(event => {
                              Try {
                                val e = event.copy(messageId = Option(event.messageId).orEmpty, eventId = RandomStringUtils.randomAlphabetic(10), clientId = Option(event.clientId).getOrElse(user.userId), appName = event.appName, contextId = Option(event.contextId).orEmpty, eventType = Option(event.eventType).map(_.toLowerCase).orNull)
                                e.validate()
                                Some(e)
                              }.getOrElse(None)
                            }).toList
                          }
                          validEvents.enqueue
                          validEvents.foreach(event => {
                            ServiceFactory.getReportingService.recordChannelStatsDelta(event.clientId, Some(event.contextId), None, channel, event.appName, event.eventType)
                          })
                          ConnektLogger(LogFile.SERVICE).debug(s"Received callback events ", supplier(validEvents.getJson))
                          complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"${channel.toUpperCase} callbacks request recieved.", s"Events successfully ingested : ${validEvents.length}")))
                        }
                      }
                    }
                  }
                }
            } ~ path("callback" / Segment) {
              (appName: String) =>
                authorize(user, "ADD_EVENTS", s"ADD_EVENTS_$appName") {
                  meteredResource(s"saveEvent.$channel") {
                    post {
                      parameterMap { urlParams =>
                        entity(as[ObjectNode]) { payload =>
                          val stencil = stencilService.getStencilsByName(s"ckt-$channel").find(_.component.equalsIgnoreCase("webhook")).get
                          channel match {
                            case Channel.WA =>
                              val waEvent = stencilService.materialize(stencil, payload).asInstanceOf[WAGeneratedEvent]
                              waEvent match {
                                case wACallbackEvent: WACallbackEvent =>
                                  WAMessageIdMappingService.get(appName, wACallbackEvent.providerMessageId.get) match {
                                    case Success(s) if s.isDefined =>
                                      val wE = s.get
                                      val messageId = wE.connektMessageId
                                      val clientId = wE.clientId
                                      val contextId = wE.contextId
                                      val e = wACallbackEvent.copy(
                                        messageId = Option(messageId).orEmpty,
                                        providerMessageId = wACallbackEvent.providerMessageId,
                                        clientId = Option(clientId).getOrElse(user.userId),
                                        appName = appName,
                                        contextId = Option(contextId).orEmpty,
                                        eventType = Option(wACallbackEvent.eventType).map(_.toLowerCase).getOrElse("")
                                      )
                                      e.validate()
                                      e.enqueue
                                      ServiceFactory.getReportingService.recordChannelStatsDelta(e.clientId, Some(e.contextId), None, channel, e.appName, e.eventType)
                                      ConnektLogger(LogFile.SERVICE).debug(s"Whatapp callback events recieved ", supplier(e.getJson))
                                      complete(GenericResponse(StatusCodes.OK.intValue, null, Response("Whatapp callbacks request received.", "Event successfully ingested")))
                                    case Success(_) =>
                                      complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"No Whatsapp entry found for whatsapp messageId ${wACallbackEvent.providerMessageId.get}.", null)))
                                    case Failure(f) =>
                                      ConnektLogger(LogFile.SERVICE).error(s"Whatsapp callback events failed", f)
                                      complete(GenericResponse(StatusCodes.InternalServerError.intValue, null, Response("Whatsapp callback events failed", f.getCause)))
                                  }
                                case waResponse: WAGeneratedEvent =>
                                  ConnektLogger(LogFile.SERVICE).error(s"Whatsapp response is not Callback event. Error occurred.", supplier(waResponse.getJson))
                                  complete(GenericResponse(StatusCodes.InternalServerError.intValue, null, Response(s"${channel.toUpperCase} Whatsapp response is not Callback event. Error occurred.", waResponse)))
                              }
                            case _ =>
                              complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response("Channel not implemented yet.", null)))
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
