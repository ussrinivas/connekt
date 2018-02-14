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
package com.flipkart.connekt.receptors.service

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{HttpChallenge, `WWW-Authenticate`}
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.flipkart.connekt.commons.entities.exception.ServiceException
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{GenericResponse, Response}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.receptors.directives.{AccessLogDirective, CORSDirectives, IdempotentRequestFailedRejection}
import com.flipkart.connekt.receptors.routes.{BaseJsonHandler, RouteRegistry}
import com.flipkart.connekt.receptors.wire.ResponseUtils._
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}


object ReceptorsServer extends BaseJsonHandler with AccessLogDirective with CORSDirectives {

  implicit var system: ActorSystem = _
  implicit var mat: ActorMaterializer = _
  implicit var ec: ExecutionContextExecutor = _

  private val bindHost = ConnektConfig.getString("receptors.bindHost").getOrElse("0.0.0.0")
  private val bindPort = ConnektConfig.getInt("receptors.bindPort").getOrElse(28000)

  var httpService: Future[ServerBinding] = null

  val basicAuthChallenge = HttpChallenge("Basic", "Connekt")
  val apiAuthChallenge = HttpChallenge("API-Key", "Connekt")

  implicit def rejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case AuthenticationFailedRejection(cause, _) => logTimedRequestResult {
        complete(
          GenericResponse(StatusCodes.Unauthorized.intValue, null, Response("Authentication Failed, Please Contact connekt-dev@flipkart.com", null))
            .respondWithHeaders(scala.collection.immutable.Seq(`WWW-Authenticate`(basicAuthChallenge, apiAuthChallenge)))
        )
      }
      case AuthorizationFailedRejection => logTimedRequestResult {
        complete(GenericResponse(StatusCodes.Forbidden.intValue, null, Response("UnAuthorised Access, Please Contact connekt-dev@flipkart.com", null)))
      }
      case MalformedRequestContentRejection(msg, _) => logTimedRequestResult {
        complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response("Malformed Content, Unable to Process Request", Map("debug" -> msg))))
      }
      case TokenAuthenticationFailedRejection(msg) => logTimedRequestResult {
        complete(GenericResponse(StatusCodes.Forbidden.intValue, null, Response("Secure Code Validation Failed.", msg)))
      }
      case IdempotentRequestFailedRejection(requestId) => logTimedRequestResult {
        complete(GenericResponse(StatusCodes.NotAcceptable.intValue, null, Response(s"Dropping idempotent message with requestId `$requestId`", Map("requestId" -> requestId))))
      }
    }.handleAll[MethodRejection] { methodRejections =>
    val names = methodRejections.map(_.supported.name)
    complete(GenericResponse(StatusCodes.MethodNotAllowed.intValue, null, Response(s"Can't do that! Supported: ${names mkString " or "}!", null)))
  }.handleNotFound {
    logTimedRequestResult {
      complete(GenericResponse(StatusCodes.NotFound.intValue, null, Response("Oh man, what you are looking for is long gone.", null)))
    }
  }.result()

  implicit def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case rejection: IllegalArgumentException => logTimedRequestResult {
      complete(GenericResponse(StatusCodes.BadRequest.intValue, null, Response("Malformed Content, Unable to Process Request", Map("debug" -> rejection.getMessage))))
    }
    case se: ServiceException => logTimedRequestResult {
      complete(GenericResponse(se.statusCode.intValue(), null, Response(se.exceptionType.toString, Map("debug" -> se.getMessage))))
    }
    case e: Throwable =>
      val errorUID: String = UUID.randomUUID.getLeastSignificantBits.abs.toString
      ConnektLogger(LogFile.SERVICE).error(s"API ERROR # -- $errorUID  --  Reason [ ${e.getMessage} ]", e)
      val response = Map("message" -> ("Server Error # " + errorUID), "reason" -> e.getMessage)
      logTimedRequestResult {
        complete(GenericResponse(StatusCodes.InternalServerError.intValue, null, Response("Internal Server Error", response)))
      }
  }

  lazy val allRoutes = cors {
    new RouteRegistry().allRoutes
  }

  def routeWithLogging = ConnektConfig.getString("http.request.log").getOrElse("true").toBoolean match {
    case true => logTimedRequestResult(allRoutes)
    case false => allRoutes
  }

  def apply(overrideAkkaConf: Config) = {
    val baseAkkaConf = ConfigFactory.load("application.conf")
    val akkaConf = overrideAkkaConf.withFallback(baseAkkaConf)

    system = ActorSystem("ckt-receptors", akkaConf)
    mat = ActorMaterializer.create(system)
    ec = system.dispatcher

    httpService = Http().bindAndHandle(routeWithLogging, bindHost, bindPort)

  }

  def shutdown() = {
    httpService.flatMap(_.unbind()).onComplete(_ => {
      ConnektLogger(LogFile.SERVICE).info("receptor server unbinding complete")

      /**
        * Shutdown  will sometimes throw error message like
        *
        * 2016-05-12 01:48:34,514 ERROR ActorSystemImpl [ckt-receptors-akka.actor.default-dispatcher-6] Outgoing response stream error
        * akka.stream.AbruptTerminationException: Processor actor [Actor[akka://ckt-receptors/user/StreamSupervisor-0/flow-2-0-unknown-operation#-100715831]] terminated abruptly
        * [ERROR] [05/12/2016 01:48:34.501] [ckt-receptors-akka.actor.default-dispatcher-11] [akka.actor.ActorSystemImpl(ckt-receptors)] Outgoing response stream error (akka.stream.AbruptTerminationException)
        *
        * But this can be ignored. Ref : https://github.com/akka/akka/issues/18747
        */
      system.terminate()
    })
  }

}
