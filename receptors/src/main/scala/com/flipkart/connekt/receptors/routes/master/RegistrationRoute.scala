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

import akka.connekt.AkkaHelpers.ActorMaterializerFunctions
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.flipkart.connekt.commons.entities.DeviceDetails
import com.flipkart.connekt.commons.entities.MobilePlatform.MobilePlatform
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels.{ContactPayload, GenericResponse, Response}
import com.flipkart.connekt.commons.services.{ConnektConfig, DeviceDetailsService}
import com.flipkart.connekt.commons.utils.GenericUtils.CaseClassPatch
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.receptors.directives.MPlatformSegment
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.routes.helper.PhoneNumberHelper
import com.flipkart.connekt.receptors.wire.ResponseUtils._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class RegistrationRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private implicit val ioDispatcher = am.getSystem.dispatchers.lookup("akka.actor.route-blocking-dispatcher")
  private val registrationTimeout = ConnektConfig.getInt("timeout.registration").getOrElse(8000).millis
  private val contactService = ServiceFactory.getContactService

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("registration") {
            pathPrefix("push") {
              path(MPlatformSegment / Segment / Segment) {
                (platform: MobilePlatform, appName: String, deviceId: String) =>
                  extractTestRequestContext { isTestRequest =>
                    authorize(user, "REGISTRATION", s"REGISTRATION_$appName") {
                      verifySecureCode(appName.toLowerCase, user.apiKey, deviceId) {
                        withRequestTimeout(registrationTimeout) {
                          put {
                            meteredResource(s"register.$platform.$appName") {
                              entity(as[DeviceDetails]) { d =>
                                complete {
                                  val newDeviceDetails = d.copy(appName = appName, osName = platform.toString, deviceId = deviceId, active = true)
                                  newDeviceDetails.validate()
                                  if (!isTestRequest) {

                                    val existingDeviceDetails = Future(DeviceDetailsService.get(appName, deviceId).get)
                                    val deviceDetailsWithToken = Future(DeviceDetailsService.getByTokenId(newDeviceDetails.appName, newDeviceDetails.token).get)

                                    deviceDetailsWithToken.flatMap[ToResponseMarshallable] {
                                      case Some(device) if device.deviceId != newDeviceDetails.deviceId =>
                                        meter("token.deviceId.mapping.update").mark()
                                        DeviceDetailsService.delete(appName, device.deviceId).get
                                        DeviceDetailsService.add(newDeviceDetails).get
                                        ConnektLogger(LogFile.SERVICE).warn(s"DeviceDetails replacing with new deviceId ${newDeviceDetails.deviceId} and token as ${newDeviceDetails.token} which was already assigned to deviceId ${device.deviceId}")
                                        FastFuture.successful(GenericResponse(StatusCodes.OK.intValue, null, Response(s"DeviceDetails replacing with new deviceId ${newDeviceDetails.deviceId} and token as ${newDeviceDetails.token} which was already assigned to deviceId ${device.deviceId}", newDeviceDetails)).respond)
                                      case _ =>
                                        existingDeviceDetails.flatMap[ToResponseMarshallable] {
                                          case Some(deviceDetail) =>
                                            DeviceDetailsService.update(deviceId, newDeviceDetails).get
                                            FastFuture.successful(GenericResponse(StatusCodes.OK.intValue, null, Response(s"DeviceDetails updated for ${newDeviceDetails.deviceId}", newDeviceDetails)).respond)
                                          case None =>
                                            DeviceDetailsService.add(newDeviceDetails).get
                                            FastFuture.successful(GenericResponse(StatusCodes.Created.intValue, null, Response(s"DeviceDetails created for ${newDeviceDetails.deviceId}", newDeviceDetails)).respond)
                                        }
                                    }
                                  }
                                  else {
                                    FastFuture.successful(GenericResponse(StatusCodes.Created.intValue, null, Response(s"DeviceDetails skipped for ${newDeviceDetails.deviceId}", newDeviceDetails)).respond)
                                  }
                                }
                              }
                            }
                          } ~ delete {
                            meteredResource(s"unregister.$platform.$appName") {
                              DeviceDetailsService.get(appName, deviceId).get match {
                                case Some(_) =>
                                  DeviceDetailsService.delete(appName, deviceId).get
                                  complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"DeviceDetails deleted for $deviceId", null)))
                                case None =>
                                  complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"No Device Found for $appName / $deviceId", null)))
                              }
                            }
                          } ~ patch {
                            meteredResource(s"patch.$platform.$appName") {
                              entity(as[Map[String, AnyRef]]) { patchedDevice =>
                                if (!isTestRequest) {
                                  val result = DeviceDetailsService.get(appName, deviceId).transform[Either[DeviceDetails, Unit]]({
                                    case Some(deviceDetail) =>
                                      val updatedDevice = deviceDetail.patch(patchedDevice)
                                      updatedDevice.validate()
                                      DeviceDetailsService.update(deviceId, updatedDevice).map(u => Left(updatedDevice))
                                    case None => Success(Right(Unit))
                                  }, Failure(_)).get

                                  result match {
                                    case Right(_) =>
                                      complete(GenericResponse(StatusCodes.Accepted.intValue, null, Response(s"No DeviceDetails exists for $deviceId", null))) //This is done to honor Varadhi contract of sidelining non 2XX request.
                                    case Left(d) =>
                                      complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"DeviceDetails updated for $deviceId", d)))
                                  }
                                } else {
                                  complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"DeviceDetails skipped for $deviceId", patchedDevice)))
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
              } ~ path(Segment / "users" / Segment) {
                (appName: String, userId: String) =>
                  withRequestTimeout(registrationTimeout) {
                    get {
                      meteredResource(s"getUserDevices.$appName") {
                        authorize(user, "REGISTRATION_READ", s"REGISTRATION_READ_$appName") {
                          val deviceDetails = DeviceDetailsService.getByUserId(appName, userId).get
                          complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"DeviceDetails fetched for app: $appName, user: $userId", Map[String, Any]("deviceDetails" -> deviceDetails))))
                        }
                      }
                    }
                  }
              } ~ path(MPlatformSegment / Segment / Segment) {
                (platform: MobilePlatform, appName: String, deviceId: String) =>
                  withRequestTimeout(registrationTimeout) {
                    get {
                      meteredResource(s"getRegistration.$platform.$appName") {
                        authorize(user, "REGISTRATION_READ", s"REGISTRATION_READ_$appName") {
                          DeviceDetailsService.get(appName, deviceId).get match {
                            case Some(deviceDetail) =>
                              complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"DeviceDetails fetched for app: $appName id: $deviceId", Map[String, Any]("deviceDetails" -> deviceDetail))))
                            case None =>
                              complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"No DeviceDetails found for app: $appName id: $deviceId", null)))
                          }
                        }
                      }
                    }
                  }
              } ~ path(Segment / "snapshot") {
                (appName: String) =>
                  withoutRequestTimeout {
                    get {
                      meteredResource(s"getAllRegistrations.$appName") {
                        authorize(user, "REGISTRATION_DOWNLOAD", s"REGISTRATION_DOWNLOAD_$appName") {
                          ConnektLogger(LogFile.SERVICE).info(s"REGISTRATION_DOWNLOAD for $appName started by ${user.userId}")
                          val dataStream = DeviceDetailsService.getAll(appName).get

                          def chunks = Source.fromIterator(() => dataStream)
                            .grouped(100)
                            .map(d => d.map(_.getJson).mkString(scala.compat.Platform.EOL))
                            .map(HttpEntity.ChunkStreamPart.apply)

                          val response = HttpResponse(entity = HttpEntity.Chunked(MediaTypes.`application/json`, chunks))
                          complete(response)
                        }
                      }
                    }
                  }
              }
            } ~ path("whatsapp" / Segment) {
              (appName: String) =>
                extractTestRequestContext { isTestRequest =>
                  authorize(user, "REGISTRATION", s"REGISTRATION_$appName") {
                    withRequestTimeout(registrationTimeout) {
                      put {
                        meteredResource(s"register.wa.contact.$appName") {
                          entity(as[ContactPayload]) { contact =>
                            if (!isTestRequest) {
                              PhoneNumberHelper.validateNFormatNumber(appName, contact.user_identifier) match {
                                case Some(n) =>
                                  val updatedContact = contact.copy(user_identifier = n, appName = appName)
                                  contactService.enqueueContactEvents(updatedContact)
                                  complete(GenericResponse(StatusCodes.Accepted.intValue, null, Response(s"Contact registration request received for destination : ${contact.user_identifier}", null)))
                                case None =>
                                  ConnektLogger(LogFile.PROCESSORS).error(s"Dropping whatsapp invalid numbers: ${contact.user_identifier}")
                                  complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Dropping whatsapp invalid numbers ${contact.user_identifier}", null)))
                              }
                            } else {
                              complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"DeviceDetails skipped for $contact.user_identifier", contact)))
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
