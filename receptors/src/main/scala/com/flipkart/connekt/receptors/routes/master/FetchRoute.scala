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
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.entities.MobilePlatform.MobilePlatform
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.receptors.directives.MPlatformSegment
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.wire.ResponseUtils._
import com.flipkart.connekt.commons.services.ConnektConfig

import scala.concurrent.duration._
import scala.util.Try


class FetchRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  private lazy implicit val stencilService = ServiceFactory.getStencilService
  private lazy val messageService = ServiceFactory.getMessageService(Channel.PUSH)
  private lazy val pullmessageService = ServiceFactory.getPullMessageService
  private lazy val pullMessageTTL = ConnektConfig.get("connections.hbase.hbase.pull.ttl").getOrElse(90)

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("fetch" / "push" / MPlatformSegment / Segment / Segment) {
            (platform: MobilePlatform, appName: String, instanceId: String) =>
              pathEndOrSingleSlash {
                  get {
                    authorize(user, "FETCH", s"FETCH_$appName") {
                      parameters('startTs.as[Long], 'endTs ? System.currentTimeMillis, 'skipIds.*) { (startTs, endTs, skipIds) =>

                      require(startTs < endTs, "startTs must be prior to endTs")

                      val profiler = timer(s"fetch.$platform.$appName").time()

                      val skipMessageIds: Set[String] = skipIds.toSet
                      val safeStartTs = if (startTs < (System.currentTimeMillis - 7.days.toMillis)) System.currentTimeMillis - 1.days.toMillis else startTs

                      val pendingMessageIds = ServiceFactory.getMessageQueueService.getMessageIds(appName, instanceId, Some(Tuple2(safeStartTs + 1, endTs)))

                      complete {
                        pendingMessageIds.map(_ids => {
                          val filteredMessageIds = _ids.distinct.filterNot(skipMessageIds.contains)

                          val fetchedMessages: Try[List[ConnektRequest]] = messageService.getRequestInfo(filteredMessageIds.toList)
                          val sortedMessages:Try[Seq[ConnektRequest]] = fetchedMessages.map{ _messages =>
                            val mIdRequestMap = _messages.map(r => r.id -> r).toMap
                            filteredMessageIds.flatMap(mId => mIdRequestMap.find(_._1 == mId).map(_._2))
                          }

                          val messages = sortedMessages.map(_.filter(_.expiryTs.forall(_ >= System.currentTimeMillis)).filterNot(_.isTestRequest)).getOrElse(List.empty[ConnektRequest])

                          val pushRequests = messages.map(r => {
                            r.id -> {
                              val channelData = Option(r.channelData) match {
                                case Some(PNRequestData(_, pnData)) if pnData != null => r.channelData
                                case _ => r.getComputedChannelData
                              }
                              val pnRequestData = channelData.asInstanceOf[PNRequestData]
                              pnRequestData.data.put("contextId", r.contextId.orEmpty).put("messageId", r.id)
                            }
                          }).toMap

                          val transformedRequests = stencilService.getStencilsByName(s"ckt-${appName.toLowerCase}-fetch").headOption match {
                            case None => pushRequests
                            case Some(stencil) => stencilService.materialize(stencil, pushRequests.getJsonNode)
                          }

                          profiler.stop()
                          GenericResponse(StatusCodes.OK.intValue, null, Response(s"Fetched result for $instanceId", transformedRequests))
                            .respondWithHeaders(scala.collection.immutable.Seq(RawHeader("endTs", endTs.toString), RawHeader("Access-Control-Expose-Headers", "endTs")))

                        })(ioDispatcher)

                      }
                    }
                  }
                } ~ delete {
                    authorize(user, "FETCH_REMOVE", s"FETCH_REMOVE_$appName") {
                      ServiceFactory.getMessageQueueService.empty(appName, instanceId)
                      complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Emptied $appName / $instanceId", null)))
                    }
                  }
              } ~ path(Segment) { messageId: String =>
                delete {
                  authorize(user, "FETCH_REMOVE", s"FETCH_REMOVE_$appName") {
                    ServiceFactory.getMessageQueueService.removeMessage(appName, instanceId, messageId)
                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Removed $messageId from $appName / $instanceId", null)))
                  }
                }
              }
          } ~ pathPrefix("fetch" / "pull" / Segment / Segment) {
            (appName: String, contactIdentifier: String) =>
              pathEndOrSingleSlash {
                get {
                  parameterMap { urlParams =>
                    authorize(user, "FETCH", s"FETCH_$appName") {
                      parameters('startTs.as[Long], 'endTs ? System.currentTimeMillis, 'size ? 10, 'offset ? 0) { (startTs, endTs, size, offset) =>
                        meteredResource(s"fetch.$appName") {
                          require(startTs < endTs, "startTs must be prior to endTs")

                          val sortedMessages = ServiceFactory.getPullMessageService.getRequest(appName, contactIdentifier, Some(startTs, endTs), urlParams)
                          complete {
                            sortedMessages.map {
                              case (messages, messageMetaDataMap) => {
                                val unreadCount = messages.count(m => !messageMetaDataMap(m.id).read.get)
                                val pullRequesData = messages.map { prd =>
                                  val data = prd.channelData.asInstanceOf[PullRequestData].data
                                  data.put("messageId", prd.id)
                                  data.put("read", messageMetaDataMap(prd.id).read.get)
                                  data.put("createTs", messageMetaDataMap(prd.id).createTs)
                                  data.put("expiryTs", messageMetaDataMap(prd.id).expiryTs)
                                  data
                                }
                                val pullResponse = Map(
                                  "total" -> pullRequesData.size,
                                  "unread" -> unreadCount,
                                  "notifications" -> pullRequesData.slice(offset, offset + size)
                                )
                                GenericResponse(StatusCodes.OK.intValue, null, Response(s"Fetched result for $contactIdentifier", pullResponse))
                                  .respondWithHeaders(scala.collection.immutable.Seq(RawHeader("endTs", endTs.toString), RawHeader("Access-Control-Expose-Headers", "endTs")))
                              }
                            }(ioDispatcher)
                          }
                        }
                      }
                    }
                  }
                }
              } ~ path(Segment) { messageId: String =>
                delete {
                  authorize(user, "PULL_REMOVE", s"PULL_REMOVE_$appName") {
                    parameterMap { urlParams =>
                      complete {
                        ServiceFactory.getPullMessageQueueService.removeMessage(appName, contactIdentifier, messageId).map { _ =>
                          ServiceFactory.getPullMessageService.writeCallbackEvent(appName, contactIdentifier, List(messageId), urlParams)
                          GenericResponse(StatusCodes.OK.intValue, null, Response(s"Removed $messageId from $appName / $contactIdentifier", null))
                        }
                      }
                    }
                  }
                }
              }
          } ~ pathPrefix("markAsRead" / "pull" / Segment / Segment) {
            (appName: String, userId: String) =>
              pathEndOrSingleSlash {
                post {
                  meteredResource(s"markAsRead.$appName") {
                    parameterMap { urlParams =>
                      authorize(user, "PULL_MARKASREAD", s",PULL_MARKASREAD_$appName") {
                        parameters('client ? "", 'platform ? "", 'appVersion.as[String] ? "0") { (client, platform, appVersion) =>
                          complete {
                            val messageIds = ServiceFactory.getPullMessageService.markAsRead(appName, userId, urlParams)
                            messageIds.map{ messages =>
                              GenericResponse(StatusCodes.OK.intValue, null, Response(s"Updated messages for $userId", messages))
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
