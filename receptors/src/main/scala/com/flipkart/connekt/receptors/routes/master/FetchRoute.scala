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

import scala.concurrent.duration._
import scala.util.Try


class FetchRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")

  private lazy implicit val stencilService = ServiceFactory.getStencilService
  private lazy val messageService = ServiceFactory.getMessageService(Channel.PUSH)
  private lazy val pullmessageService = ServiceFactory.getPullMessageService

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

                      val pendingMessageIds = ServiceFactory.getMessageQueueService.getMessages(appName, instanceId, Some(Tuple2(safeStartTs + 1, endTs)))

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
            (appName: String, instanceId: String) =>
              pathEndOrSingleSlash {
                get {
                  authorize(user, "FETCH", s"FETCH_$appName") {
                    parameters('startTs.as[Long], 'endTs ? System.currentTimeMillis, 'size ? 10, 'offset ? 0 ) { (startTs, endTs, size, offset) =>
                      require(startTs < endTs, "startTs must be prior to endTs")

                      val profiler = timer(s"fetch.$appName").time()

                      val safeStartTs = if (startTs < (System.currentTimeMillis - 30.days.toMillis)) System.currentTimeMillis - 7.days.toMillis else startTs

                      val pendingMessages = ServiceFactory.getInAppMessageQueueService.getMessagesWithDetails(appName, instanceId, Some(Tuple2(safeStartTs + 1, endTs)))


                      complete {
                        pendingMessages.map(_ids => {
                          val messageMap = _ids.toMap
                          val distinctMessageIds = _ids.map(_._1).distinct
                          val paginatedMessageIds = distinctMessageIds.slice(offset, offset + size)
                          var unreadCount = distinctMessageIds.map(1L - messageMap(_).read.get).foldRight(0L)(_ + _)

                          val fetchedMessages: Try[List[ConnektRequest]] = pullmessageService.getRequestInfo(paginatedMessageIds.toList)
                          val sortedMessages: Try[Seq[ConnektRequest]] = fetchedMessages.map { _messages =>
                            val mIdRequestMap = _messages.map(r => r.id -> r).toMap
                            paginatedMessageIds.flatMap(mId => mIdRequestMap.find(_._1 == mId).map(_._2))
                          }

                          val messages = sortedMessages.map(_.filter(_.expiryTs.forall(_ >= System.currentTimeMillis)).filterNot(_.isTestRequest)).getOrElse(List.empty[ConnektRequest])

                          val pullRequests = messages.map(r => {
                            {
                              val channelData = Option(r.channelData) match {
                                case Some(PNRequestData(_, pnData)) if pnData != null => r.channelData
                                case _ => r.getComputedChannelData
                              }
                              val pullRequestData = channelData.asInstanceOf[PullRequestData]
                              Map(
                                "read" -> (messageMap(r.id).read.get == 1L),
                                "messageId" -> r.id
                              ) ++ pullRequestData.ccToMap
                            }
                          })

                          val pullResponse = Map(
                            "total" -> pullRequests.size,
                            "unread" -> unreadCount,
                            "notifications" -> pullRequests
                          )
                          profiler.stop()
                          GenericResponse(StatusCodes.OK.intValue, null, Response(s"Fetched result for $instanceId", pullResponse))
                            .respondWithHeaders(scala.collection.immutable.Seq(RawHeader("endTs", endTs.toString), RawHeader("Access-Control-Expose-Headers", "endTs")))

                        })(ioDispatcher)

                      }
                    }
                  }
                }
              } ~ path(Segment) { messageId: String =>
                delete {
                  authorize(user, "FETCH_REMOVE", s"FETCH_REMOVE_$appName") {
                    ServiceFactory.getInAppMessageQueueService.removeMessage(appName, instanceId, messageId)
                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Removed $messageId from $appName / $instanceId", null)))
                  }
                }
              }
          }
        }
    }
}
