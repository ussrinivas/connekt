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
package com.flipkart.connekt.receptors.routes.common

import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{Constants, GenericResponse, Response}
import com.flipkart.connekt.commons.services.{DeviceDetailsService, WAContactService}
import com.flipkart.connekt.commons.sync.{SyncManager, SyncMessage, SyncType}
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import org.apache.zookeeper.CreateMode
import com.flipkart.connekt.commons.iomodels.{GenericResponse, Response}
import com.flipkart.connekt.commons.services.DeviceDetailsService
import com.flipkart.connekt.commons.sync.{SyncManager, SyncMessage, SyncType}
import org.apache.zookeeper.CreateMode

class AdminRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private final val WA_CONTACT_QUEUE = Constants.WAConstants.WA_CONTACT_QUEUE

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("admin") {
            authorize(user, "ADMIN_CACHE_WARMUP") {
              pathPrefix("push" / "warmup") {
                path("jobs") {
                  get {
                    val result = DeviceDetailsService.cacheJobStatus.map {
                      case (jobId, _) => jobId -> DeviceDetailsService.cacheWarmUpJobStatus(jobId)
                    }
                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Registration cache warm-up jobs' status", result)))
                  }
                } ~
                  path(Segment) {
                    (appName: String) =>
                      get {
                        ConnektLogger(LogFile.SERVICE).info(s"Registration cache warm-up initiated for $appName by ${user.userId}")
                        val jobId = DeviceDetailsService.cacheWarmUp(appName)
                        complete(GenericResponse(StatusCodes.Created.intValue, null, Response(s"Registration cache warm-up started", Map("appName" -> appName, "jobId" -> jobId))))
                      }
                  }
              } ~ pathPrefix("whatsapp" / "warmup") {
                get {
                  WAContactService.instance.refreshWAContacts(WA_CONTACT_QUEUE)
                  complete(GenericResponse(StatusCodes.Created.intValue, null, Response("Wa Contact warm-up started", null)))
                }
              }
            } ~ pathPrefix("topology" / Segment) {
              (topology: String) => {
                authorize(user, "ADMIN_TOPOLOGY_UPDATE") {
                  path(Segment) {
                    (action: String) =>
                      get {
                        action match {
                          case ("start" | "stop") =>
                            topology.toLowerCase match {
                              case "email" => SyncManager.get().publish(SyncMessage(topic = SyncType.EMAIL_TOPOLOGY_UPDATE, List(action, topology)), CreateMode.PERSISTENT)
                              case "sms" => SyncManager.get().publish(SyncMessage(topic = SyncType.SMS_TOPOLOGY_UPDATE, List(action, topology)), CreateMode.PERSISTENT)
                              case "android" => SyncManager.get().publish(SyncMessage(topic = SyncType.ANDROID_TOPOLOGY_UPDATE, List(action, topology)), CreateMode.PERSISTENT)
                              case "ios" => SyncManager.get().publish(SyncMessage(topic = SyncType.IOS_TOPOLOGY_UPDATE, List(action, topology)), CreateMode.PERSISTENT)
                              case "openweb" => SyncManager.get().publish(SyncMessage(topic = SyncType.OPENWEB_TOPOLOGY_UPDATE, List(action, topology)), CreateMode.PERSISTENT)
                              case "window" => SyncManager.get().publish(SyncMessage(topic = SyncType.WINDOW_TOPOLOGY_UPDATE, List(action, topology)), CreateMode.PERSISTENT)
                              case _ => complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response("Topology not defined.", null)))
                            }
                            complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Topology for channel $topology action $action successful", null)))
                          case _ =>
                            complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response("Invalid request", null)))
                        }
                      }
                  }
                }
              }
            }
          }
        }
    }
