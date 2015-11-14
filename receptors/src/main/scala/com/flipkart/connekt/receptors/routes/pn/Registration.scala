package com.flipkart.connekt.receptors.routes.pn

import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.entities.DeviceDetails
import com.flipkart.connekt.commons.iomodels.GenericResponse
import com.flipkart.connekt.receptors.routes.BaseHandler

import scala.collection.immutable.Seq
import scala.util.{Failure, Success}

/**
 *
 *
 * @author durga.s
 * @version 11/20/15
 */
class Registration (implicit _am: ActorMaterializer) extends BaseHandler {

  implicit val am: ActorMaterializer = _am

  val register =
    sniffHeaders { headers =>
      isAuthenticated(Some(headers)) {
        pathPrefix("push") {
          pathPrefix("device") {
            pathPrefix("registration") {
              path("save") {
                (post | put) {
                  entity(as[DeviceDetails]) { d =>
                    def save = DaoFactory.getDeviceDetailsDao.saveDeviceDetails(d)
                    async(save) {
                      case Success(t) =>
                        complete(respond[GenericResponse](
                          StatusCodes.Created, Seq.empty[HttpHeader],
                          GenericResponse("DeviceDetails registered for %s".format(d.deviceId), null, null, StatusCodes.Created.intValue)
                        ))
                      case Failure(e) =>
                        complete(respond[GenericResponse](
                          StatusCodes.InternalServerError, Seq.empty[HttpHeader],
                          GenericResponse("DeviceDetails registration failed for %s".format(d.deviceId), e.getMessage, null , StatusCodes.InternalServerError.intValue)
                        ))
                    }
                  }
                }
              } ~
                path("fetch" / Segment / Segment) {
                  (appName: String, deviceId: String) =>
                    get {
                      def fetch = DaoFactory.getDeviceDetailsDao.fetchDeviceDetails(appName, deviceId)
                      async(fetch) {
                        case Success(resultOption) =>
                          resultOption match {
                            case Some(deviceDetails) =>
                              complete(respond[GenericResponse](
                                StatusCodes.OK, Seq.empty[HttpHeader],
                                GenericResponse("DeviceDetails fetched for app: %s %s".format(appName, deviceId), null, deviceDetails, StatusCodes.OK.intValue)
                              ))
                            case None =>
                              complete(respond[GenericResponse](
                                StatusCodes.OK, Seq.empty[HttpHeader],
                                GenericResponse("No DeviceDetails found for app:%s %s".format(appName, deviceId), null, null, StatusCodes.OK.intValue)
                              ))

                          }
                        case Failure(error) =>
                          complete(respond[GenericResponse](
                            StatusCodes.InternalServerError, Seq.empty[HttpHeader],
                            GenericResponse("Fetching DeviceDetails failed for app:%s %s".format(appName, deviceId), error.getMessage, null, StatusCodes.InternalServerError.intValue)
                          ))
                      }
                    }
                }
            }
          }
        }
      }
    }
}

