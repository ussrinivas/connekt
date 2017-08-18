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
package com.flipkart.connekt.receptors.routes.stencils

import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.flipkart.connekt.commons.entities.{Bucket, Stencil, StencilsEnsemble}
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.sync.{SyncManager, SyncMessage, SyncType}
import com.flipkart.connekt.commons.utils.StringUtils
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.connekt.receptors.routes.BaseJsonHandler
import com.flipkart.connekt.receptors.wire.ResponseUtils._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}


class StencilsRoute(implicit am: ActorMaterializer) extends BaseJsonHandler {

  private lazy val stencilService = ServiceFactory.getStencilService

  val route =
    authenticate {
      user =>
        pathPrefix("v1") {
          pathPrefix("stencils") {
            path("bucket" / Segment) {
              (name: String) =>
                post {
                  authorize(user, "ADMIN_BUCKET") {
                    val bucket = new Bucket(StringUtils.generateRandomStr(5), name)
                    stencilService.addBucket(bucket) match {
                      case Success(x) =>
                        complete(GenericResponse(StatusCodes.Created.intValue, null, Response(s"StencilBucket created for name: ${bucket.name}", Map("id" -> bucket.id))))
                      case Failure(e) =>
                        complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"StencilBucket creation failed for name: ${bucket.name}", e)))
                    }
                  }
                } ~ get {
                  authorize(user, "ADMIN_BUCKET", "READ_BUCKET", s"READ_BUCKET_$name", s"STENCIL_GET_$name") {
                    stencilService.getBucket(name) match {
                      case Some(bucket) =>
                        val stencils =  stencilService.getStencilsByBucket(name).map(s => s.id -> s.toInfo).toMap.values.toSeq.sortWith(_.lastUpdatedTS > _.lastUpdatedTS)
                        complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"StencilBucket found for name: ${bucket.name}", Map("bucket" -> bucket, "contents" -> stencils))))
                      case _ =>
                        complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"StencilBucket not found for name: $name}", null)))
                    }
                  }
                }
            } ~ pathPrefix("components" / "registry") {
              pathEndOrSingleSlash {
                post {
                  meteredResource("addStencilComponents") {
                    authorize(user, "ADMIN_ENSEMBLE") {
                      entity(as[StencilsEnsemble]) { obj =>
                        obj.validate()
                        val id = StringUtils.generateRandomStr(4)
                        val stencilComponents = new StencilsEnsemble()
                        stencilComponents.id = id
                        stencilComponents.components = obj.components.toLowerCase.replaceAll("\\s", "")
                        stencilComponents.name = obj.name.toUpperCase.trim
                        stencilComponents.createdBy = user.userId
                        stencilComponents.updatedBy = user.userId
                        stencilService.getStencilsEnsembleByName(stencilComponents.name) match {
                          case Some(_) =>
                            complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Stencil components with `name` ${stencilComponents.name} already exists.", null)))
                          case None =>
                            stencilService.addStencilComponents(stencilComponents) match {
                              case Success(sten) =>
                                complete(GenericResponse(StatusCodes.Created.intValue, null, Response(s"Stencil components registered with id: $id", Map("StencilComponents" -> stencilComponents))))
                              case Failure(e) =>
                                complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Error in Stencil components for id: $id, e: ${e.getMessage}", null)))
                            }
                        }
                      }
                    }
                  }
                } ~ get {
                  val data = stencilService.getAllEnsemble().map(ensemble => ensemble.name -> ensemble.components.split(",")).toMap
                  complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"All Ensembles fetched", data)))
                }
              } ~ path(Segment) {
                (name: String) =>
                  put {
                    meteredResource("stencilComponentsUpdate") {
                      authorize(user, "ADMIN_ENSEMBLE") {
                        entity(as[StencilsEnsemble]) { obj =>
                          stencilService.getStencilsEnsembleByName(name) match {
                            case Some(originalComponents) =>
                              val stencilComponents = new StencilsEnsemble()
                              stencilComponents.id = originalComponents.id
                              stencilComponents.components = obj.components.toLowerCase.replaceAll("\\s", "")
                              stencilComponents.name = name.toUpperCase.trim
                              stencilComponents.updatedBy = user.userId
                              stencilComponents.creationTS = originalComponents.creationTS
                              stencilService.addStencilComponents(stencilComponents) match {
                                case Success(sten) =>
                                  complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Stencil components updated with name: $name", Map("stencilComponents" -> stencilComponents))))
                                case Failure(e) =>
                                  complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Error in Stencil components for name: $name, e: ${e.getMessage}", null)))
                              }
                            case None =>
                              complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"Stencil components not found with name: $name", null)))
                          }
                        }
                      }
                    }
                  } ~ get {
                    stencilService.getStencilsEnsembleByName(name) match {
                      case Some(stencilComponents) =>
                        complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Stencil components fetched for name: $name", Map("stencilComponents" -> stencilComponents))))
                      case None =>
                        complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Stencil components not found for name: $name", null)))
                    }
                  }
              } ~ path(Segment / "touch") {
                (id: String) =>
                  post {
                    SyncManager.get().publish(SyncMessage(SyncType.STENCIL_COMPONENTS_UPDATE, List(id)))
                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Triggered  Change for stencil components: $id", null)))
                  }
              }
            } ~ pathPrefix(Segment) {
              (id: String) =>
                path("preview") {
                  parameters('v ?) { version =>
                    post {
                      meteredResource("stencilPreview") {
                        entity(as[ObjectNode]) { entity =>
                          stencilService.get(id, version) match {
                            case stencils if stencils.nonEmpty =>
                              val bucketIds = stencils.head.bucket.split(",")
                              authorize(user, bucketIds.map("STENCIL_PREVIEW_" + _): _*) {
                                val preview = stencils.map(stencil => {
                                  stencil.component -> stencilService.materialize(stencil, entity)
                                }).toMap
                                complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Stencils fetched for id: $id / version: ${version.getOrElse("HEAD")}", preview)))
                              }
                            case _ =>
                              complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response(s"Stencil not found for id: $id / version: ${version.getOrElse("HEAD")}", null)))
                          }
                        }
                      }
                    }
                  }
                } ~ pathPrefix(Segment) {
                  (version: String) =>
                    path(Segment / "touch") {
                      (component: String) =>
                        post {
                          meteredResource("stencilTouch") {
                            SyncManager.get().publish(SyncMessage(SyncType.STENCIL_CHANGE, List(id, version)))
                            SyncManager.get().publish(SyncMessage(SyncType.STENCIL_FABRIC_CHANGE, List(stencilService.fabricCacheKey(id, component, version))))
                            complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Triggered  Change for client: $id", null)))
                          }
                        }
                    }
                } ~ pathEndOrSingleSlash {
                  put {
                    meteredResource("stencilUpdate") {
                      stencilService.get(id) match {
                        case stencils if stencils.nonEmpty =>
                          authorize(user, stencils.head.bucket.split(",").map("STENCIL_MOD_" + _): _*) {
                            entity(as[ObjectNode]) { obj =>
                              val stencilName = obj.get("name").asText()
                              val components = obj.get("components").asInstanceOf[ArrayNode].elements().asScala
                              val bucket = obj.get("bucket").asText()
                              val stencilType = obj.get("type").asText()

                              require(stencils.head.`type`.equalsIgnoreCase(stencilType), "stencil type cannot be changed")

                              val bucketIds = bucket.split(",").flatMap(stencilService.getBucket(_).map(_.name.toUpperCase))

                              val stencilsUpdate = components.map(c => {
                                val stencil = c.toString.getObj[Stencil]
                                stencil.bucket = bucketIds.mkString(",")
                                stencil.id = id
                                stencil.createdBy = stencils.head.createdBy
                                stencil.updatedBy = user.userId
                                stencil.`type` = stencilType
                                stencil.name = stencilName
                                stencil.creationTS = stencils.head.creationTS
                                stencil.lastUpdatedTS = new Date(System.currentTimeMillis())
                                require(stencilService.checkStencil(stencil).getOrElse(false), s"stencil component ${stencil.component} not valid")
                                stencil
                              }).toList

                              if (stencils.head.name.equals(stencilName)) {
                                stencilService.update(id, stencilsUpdate) match {
                                  case Success(_) =>
                                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Stencil updated for id: $id", null)))
                                  case _ =>
                                    complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Error in Stencil for id: $id", null)))
                                }
                              } else {
                                // If stencil name is changed, deleting old stencil and creating new
                                stencilService.updateWithIdentity(id, stencils.head.name, stencilsUpdate) match {
                                  case Success(_) =>
                                    complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Stencil updated for id: $id", null)))
                                  case _ =>
                                    complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Error in Stencil for id: $id", null)))
                                }
                              }
                            }
                          }
                        case _ =>
                          complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Stencil not found for id: $id", null)))
                      }
                    }
                  } ~ get {
                    parameters('v ?) { version =>
                      meteredResource("stencilGet") {
                        stencilService.get(id, version) match {
                          case stencils if stencils.nonEmpty =>
                            authorize(user, stencils.head.bucket.split(",").map("STENCIL_GET_" + _): _*) {
                              val result = stencils.head.asMap.filterKeys(!List("component", "engineFabric", "engine").contains(_)) ++ Map("components" -> stencils)
                              complete(GenericResponse(StatusCodes.OK.intValue, Map("id" -> id, "version" -> version.getOrElse("HEAD")), Response(null, result)))
                            }
                          case _ =>
                            complete(GenericResponse(StatusCodes.BadRequest.intValue, Map("id" -> id, "version" -> version.getOrElse("HEAD")), Response(s"Stencil not found", null)))
                        }
                      }
                    }
                  } ~ delete {
                    stencilService.get(id) match {
                      case stencils if stencils.nonEmpty =>
                        authorize(user, stencils.head.bucket.split(",").map("STENCIL_MOD_" + _): _*) {
                          stencilService.delete(id).get
                          complete(GenericResponse(StatusCodes.OK.intValue, null, Response(s"Stencil deleted id: $id", null)))
                        }
                      case _ =>
                        complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response(s"Stencil not found for id: $id", null)))
                    }
                  }
                }
            } ~ pathEndOrSingleSlash {
              post {
                meteredResource("stencilAdd") {
                  entity(as[ObjectNode]) { obj =>
                    val bucket = obj.get("bucket").asText()
                    val bucketIds = bucket.split(",").flatMap(stencilService.getBucket(_).map(_.name.toUpperCase))
                    authorize(user, bucketIds.map("STENCIL_MOD_" + _): _*) {
                      val stencilName = obj.get("name").asText()
                      val stencilType = obj.get("type").asText()
                      val stencilId = "STN" + StringUtils.generateRandomStr(6)
                      val components = obj.get("components").asInstanceOf[ArrayNode].elements().asScala
                      val stencils = components.map(c => {
                        val stencil = c.toString.getObj[Stencil]
                        stencil.bucket = bucketIds.mkString(",")
                        stencil.id = stencilId
                        stencil.`type` = stencilType
                        stencil.createdBy = user.userId
                        stencil.updatedBy = user.userId
                        stencil.version = 1
                        stencil.name = stencilName
                        stencil.creationTS = new Date()
                        stencil.lastUpdatedTS = new Date()
                        require(stencilService.checkStencil(stencil).getOrElse(false), s"stencil component ${stencil.component} not valid")
                        stencil
                      }).toList

                      stencilService.add(stencilId, stencils) match {
                        case Success(_) =>
                          complete(GenericResponse(StatusCodes.Created.intValue, null, Response(s"Stencil registered with id: $stencilId", Map("id" -> stencilId))))
                        case Failure(e) =>
                          complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response("Error in Stencil.", e.getMessage)))
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
