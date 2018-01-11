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
import com.fasterxml.jackson.databind.node.ObjectNode

import scala.concurrent.{ExecutionContextExecutor, Future}


abstract class TrackingFlow(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends MapAsyncFlowStage[ConnektRequest, ConnektRequest](parallelism) with Instrumented {

  lazy private val projectConfigService = ServiceFactory.getUserProjectConfigService
  lazy private val defaultTrackingDomain = ConnektConfig.getString("tracking.default.domain").get

  def transformChannelData(request:ConnektRequest, trackingDomain:String, transformer: TURLTransformer ) : ChannelRequestData
  def transformChannelDataModel(request:ConnektRequest, trackingDomain:String, transformer: TURLTransformer ) : ObjectNode = {
    request.channelDataModel
  }

  override val map: (ConnektRequest) => Future[List[ConnektRequest]] = input => Future(profile("map") {
    try {
      ConnektLogger(LogFile.PROCESSORS).debug("TrackingFlow received message: {}", supplier(input.id))
      ConnektLogger(LogFile.PROCESSORS).trace("TrackingFlow received message: {}", supplier(input.getJson))

      val transformerClassName = projectConfigService.getProjectConfiguration(input.appName, s"tracking-classname-${input.channel.toLowerCase}").get.map(_.value).getOrElse(classOf[IdentityURLTransformer].getName)
      val transformer: TURLTransformer = Class.forName(transformerClassName).newInstance().asInstanceOf[TURLTransformer]

      val appDomain = projectConfigService.getProjectConfiguration(input.appName, "tracking-domain").get.map(_.value).getOrElse(defaultTrackingDomain)
      val trackingEnabled = projectConfigService.getProjectConfiguration(input.appName, s"tracking-enabled").get.map(_.value).getOrElse("true").toBoolean

      if(trackingEnabled) {
        List(input.copy(
          channelData = transformChannelData(input, appDomain, transformer),
          channelDataModel = transformChannelDataModel(input, appDomain, transformer)
        ))
      } else {
        ConnektLogger(LogFile.PROCESSORS).trace("TrackingFlow skipping since disabled for messageId: {}", supplier(input.id))
        List(input)
      }

    } catch {
      case e: Throwable =>
        ConnektLogger(LogFile.PROCESSORS).error(s"TrackingFlow error", e)
        throw ConnektStageException(input.id, input.clientId, input.destinations, InternalStatus.TrackingFailure, input.appName, input.channel, input.contextId.orEmpty, input.meta ++ input.stencilId.map("stencilId" -> _).toMap, s"TrackingFlow-${e.getMessage}", e)
    }
  })(ec)

}

class EmailTrackingFlow(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends TrackingFlow(parallelism)(ec) {

  override def transformChannelData(input: ConnektRequest, trackingDomain:String, transformer: TURLTransformer ): ChannelRequestData = {

        /**
          * I don't know how to individually track each recipient. Assuming simple email,
          * open/click are tracked against the first `to` address
          */
        val destination = input.channelInfo.asInstanceOf[EmailRequestInfo].to.head.address
        val cData = input.channelData.asInstanceOf[EmailRequestData]

        val trackerOptions = TrackerOptions(domain = trackingDomain,
          channel = Channel.EMAIL,
          messageId = input.id,
          contextId = input.contextId,
          destination = destination,
          clientId = input.clientId,
          appName = input.appName)

        EmailRequestData(subject = cData.subject,
          html = TrackingService.trackHTML(cData.html, trackerOptions, transformer),
          text = TrackingService.trackText(cData.text, trackerOptions, transformer),
          attachments = cData.attachments
        )
  }
}


class SMSTrackingFlow(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends TrackingFlow(parallelism)(ec) {

  override def transformChannelData(input: ConnektRequest, trackingDomain:String, transformer: TURLTransformer ): ChannelRequestData = {

    /**
      * I don't know how to individually track each recipient. Assuming simple sms,
      * open/click are tracked against the first `to` address
      */
    val destination = input.channelInfo.asInstanceOf[SmsRequestInfo].receivers.head
    val sData = input.channelData.asInstanceOf[SmsRequestData]

    val trackerOptions = TrackerOptions(domain = trackingDomain,
      channel = Channel.SMS,
      messageId = input.id,
      contextId = input.contextId,
      destination = destination,
      clientId = input.clientId,
      appName = input.appName)

    SmsRequestData(body = TrackingService.trackText(sData.body, trackerOptions, transformer))

  }
}

class WATrackingFlow(parallelism: Int)(implicit ec: ExecutionContextExecutor) extends TrackingFlow(parallelism)(ec) {
  override def transformChannelDataModel(input: ConnektRequest, trackingDomain:String, transformer: TURLTransformer ): ObjectNode = {
    val destination = input.channelInfo.asInstanceOf[WARequestInfo].destinations.head
    val trackerOptions = TrackerOptions(domain = trackingDomain,
      channel = Channel.WA,
      messageId = input.id,
      contextId = input.contextId,
      destination = destination,
      clientId = input.clientId,
      appName = input.appName)

    input.channelDataModel match {
      case dataModel:ObjectNode =>
        TrackingService.trackText(dataModel.toString, trackerOptions, transformer).getObj[ObjectNode]
      case _ => input.channelDataModel
    }
  }

  override def transformChannelData(input: ConnektRequest, trackingDomain:String, transformer: TURLTransformer ): ChannelRequestData = {
    val destination = input.channelInfo.asInstanceOf[WARequestInfo].destinations.head
    val waData = input.channelData.asInstanceOf[WARequestData]

    val trackerOptions = TrackerOptions(domain = trackingDomain,
      channel = Channel.WA,
      messageId = input.id,
      contextId = input.contextId,
      destination = destination,
      clientId = input.clientId,
      appName = input.appName)

    val attachment = waData.attachment.map(attachment => {
      attachment.copy(
        caption = attachment.caption.map(caption => {
          TrackingService.trackText(caption, trackerOptions, transformer)
        }),
        name = s"${input.id}_${attachment.name}"
      )
    })
    waData.copy(
      attachment = attachment,
      message = waData.message.map(TrackingService.trackText(_, trackerOptions, transformer))
    )
  }

}

