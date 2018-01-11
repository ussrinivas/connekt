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
import akka.http.scaladsl.unmarshalling.Unmarshaller.messageUnmarshallerFromEntityUnmarshaller
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.databind.node.{BaseJsonNode, ObjectNode}
import com.flipkart.concord.guardrail.TGuardrailEntity
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.{ChannelRequestInfo, _}
import com.flipkart.connekt.receptors.directives.ChannelSegment
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.commons.helpers.CallbackRecorder._
import com.flipkart.connekt.commons.services.GuardrailService

import scala.util.{Failure, Success, Try}

class InboundMessageRoute(implicit am: ActorMaterializer) extends BaseJsonHandler with PredefinedFromEntityUnmarshallers {

  private lazy implicit val stencilService = ServiceFactory.getStencilService

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix(ChannelSegment) { channel: Channel =>
            path("inbound" / "parse" / Segment / Segment) {
              (appName: String, providerName: String) =>
                authorize(user, "CREATE_INBOUND_EVENTS", s"CREATE_INBOUND_EVENTS_$appName") {
                  (post | get) {
                    parameterMap { urlParams =>
                      entity(as[String](messageUnmarshallerFromEntityUnmarshaller(stringUnmarshaller))) { stringBody =>
                        val payload: ObjectNode = stringBody match {
                          case x if x.isEmpty => Map("get" -> urlParams).getJsonNode
                          case y => Map("post" -> y.getObj[BaseJsonNode]).getJsonNode
                        }
                        val stencil = stencilService.getStencilsByName(s"ckt-$channel-$providerName").find(_.component.equalsIgnoreCase("inbound")).get
                        val event = stencilService.materialize(stencil, payload).asInstanceOf[InboundMessageCallbackEvent].copy(clientId = user.userId, appName = appName, channel = channel, sender = providerName)
                        event.validate()
                        event.enqueue
                        ConnektLogger(LogFile.SERVICE).debug(s"Received inbound event {}", supplier(event.toString))
                        complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"InboundMessage event recieved successfully for appName : $appName, providerName : $providerName", null)))
                      }
                    }
                  }
                }
            } ~ path("inbound" / "process" / Segment / Segment) {
              (appName: String, providerName: String) =>
                authorize(user, "PROCESS_INBOUND_EVENTS", s"PROCESS_INBOUND_EVENTS_$appName") {
                  post {
                    entity(as[CallbackEvent]) { callbackEvent =>
                      channel match {
                        case Channel.WA =>
                          val appLevelConfigService = ServiceFactory.getUserProjectConfigService
                          val inboundEvent = callbackEvent.asInstanceOf[InboundMessageCallbackEvent]
                          ConnektLogger(LogFile.SERVICE).debug(s"Received Whatsapp inbound process request with payload: ${inboundEvent.toString}")
                          val cargo = inboundEvent.cargo.getObj[Map[String, String]]
                          if (cargo.contains("from")) {
                            val sender = cargo("from")
                            GuardrailService.isGuarded[String, Boolean](appName, Channel.WA, sender, Map("domain" -> "flipkart", "source" -> "Whatsapp", "bucket" -> "transactional", "subBucket" -> "order", "accountId" -> "")) match {
                              case Success(isGuarded) if !isGuarded =>
                                val text = inboundEvent.message.getObj[Map[String, String]].getOrElse("text", "")
                                val channelInfo = WARequestInfo(appName = appName, destinations = Set(sender))
                                val standardResponses = appLevelConfigService.getProjectConfiguration(appName.toLowerCase, "whatsapp-standard-responses").get.get.value.getObj[ObjectNode]
                                text.toLowerCase match {
                                  case "stop" =>
                                    val guardrailEntity = new TGuardrailEntity[String] {
                                      override def entity: String = sender
                                    }
                                    GuardrailService.guard[String, Boolean](appName, channel, guardrailEntity, Map("domain" -> "flipkart", "source" -> "Whatsapp"))
                                    val channelData = WARequestData(waType = WAType.text, message = Some(standardResponses.get("stop").asText()))
                                    val connektRequest = new ConnektRequest(generateUUID, "whatspp", Some("UNSUBS"), channel.toString, "H", None, None, None, channelInfo, channelData, null)
                                    val queueName = ServiceFactory.getMessageService(Channel.WA).getRequestBucket(connektRequest, user)
                                    ServiceFactory.getMessageService(Channel.WA).saveRequest(connektRequest, queueName)
                                  case _ =>
                                    val channelData = WARequestData(waType = WAType.text, message = Some(standardResponses.get("default").asText()))
                                    val connektRequest = new ConnektRequest(generateUUID, "whatspp", Some("WhatsAppReply"), channel.toString, "H", None, None, None, channelInfo, channelData, null)
                                    val queueName = ServiceFactory.getMessageService(Channel.WA).getRequestBucket(connektRequest, user)
                                    ServiceFactory.getMessageService(Channel.WA).saveRequest(connektRequest, queueName)
                                }
                              case Success(pref) =>
                                ConnektLogger(LogFile.SERVICE).info(s"No Whatsapp reply sent to sender: $sender as sender is guarded.")
                              case Failure(f) =>
                                ConnektLogger(LogFile.SERVICE).error(s"GuardrailService failed: ${f.toString}")
                            }
                            complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"InboundMessage event processed successfully for appName : $appName, providerName : $providerName", null)))
                          } else {
                            complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Ignoring inboundMessage event as it doesn't contains Sender destination for appName : $appName, providerName : $providerName", null)))
                          }
                        case _ =>
                          complete(GenericResponse(StatusCodes.NotAcceptable.intValue, null, Response(s"InboundMessage event for channel : $channel is not supported", null)))
                      }
                    }
                  }
                }
            }
          }
        }
    }
}
