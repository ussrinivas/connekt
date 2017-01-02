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
package com.flipkart.connekt.busybees.streams.flows

import com.flipkart.concord.transformer.TURLTransformer
import com.flipkart.connekt.busybees.streams.errors.ConnektStageException
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.helpers.ConnektRequestHelper._
import com.flipkart.connekt.commons.iomodels.MessageStatus.InternalStatus
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.TrackingService.TrackerOptions
import com.flipkart.connekt.commons.services.{ConnektConfig, TrackingService}
import com.flipkart.connekt.commons.utils.IdentityURLTransformer
import com.flipkart.connekt.commons.utils.StringUtils._

import scala.concurrent.{ExecutionContextExecutor, Future}


class TrackingFlow(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends MapAsyncFlowStage[ConnektRequest, ConnektRequest](parallelism) with Instrumented {

  lazy private val projectConfigService = ServiceFactory.getUserProjectConfigService
  lazy private val defaultTrackingDomain = ConnektConfig.getString("tracking.default.domain").get

  override val map: (ConnektRequest) => Future[List[ConnektRequest]] = input => Future(profile("map") {
    try {
      ConnektLogger(LogFile.PROCESSORS).debug("TrackingFlow received message: {}", supplier(input.id))
      ConnektLogger(LogFile.PROCESSORS).trace("TrackingFlow received message: {}", supplier(input.getJson))

      val transformerClassName = projectConfigService.getProjectConfiguration(input.appName, s"tracking-classname-${input.channel.toLowerCase}").get.map(_.value).getOrElse(classOf[IdentityURLTransformer].getName)
      val transformer: TURLTransformer = Class.forName(transformerClassName).newInstance().asInstanceOf[TURLTransformer]

      val appDomain = projectConfigService.getProjectConfiguration(input.appName, "tracking-domain").get.map(_.value).getOrElse(defaultTrackingDomain)

      //identify payload and rewrite them with tracking.
      val updatedChannelData = input.channelData match {
        case cData: EmailRequestData =>

          /**
            * I don't know how to individually track each recipient. Assuming simple email,
            * open/click are tracked against the first `to` address
            */
          val destination = input.channelInfo.asInstanceOf[EmailRequestInfo].to.head.address

          val trackerOptions = TrackerOptions(domain = appDomain,
            channel = Channel.EMAIL,
            messageId = input.id,
            contextId = input.contextId,
            destination = destination,
            clientId = input.clientId,
            appName = input.appName)

          EmailRequestData(subject = cData.subject,
            html = TrackingService.trackHTML(cData.html, trackerOptions, transformer),
            text = TrackingService.trackText(cData.text, trackerOptions, transformer)
          )

        case sData: SmsRequestData =>
          val destination = input.channelInfo.asInstanceOf[SmsRequestInfo].receivers.head
          val trackerOptions = TrackerOptions(domain = appDomain,
            channel = Channel.SMS,
            messageId = input.id,
            contextId = input.contextId,
            destination = destination,
            clientId = input.clientId,
            appName = input.appName)

          SmsRequestData(body = TrackingService.trackText(sData.body, trackerOptions, transformer))

        case unsupportedChannel =>
          ConnektLogger(LogFile.PROCESSORS).trace("TrackingFlow non-supported channel skipping for messageId: {}", supplier(input.id))
          input.channelData
      }

      List(input.copy(channelData = updatedChannelData))
    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"TrackingFlow error", e)
        throw new ConnektStageException(input.id, input.clientId, input.destinations, InternalStatus.TrackingFailure, input.appName, input.channel, input.contextId.orEmpty, input.meta ++ input.stencilId.map("stencilId" -> _).toMap, s"TrackingFlow-${e.getMessage}", e)
    }
  })(ec)


}
