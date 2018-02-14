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
package com.flipkart.connekt.firefly.topology

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source}
import com.flipkart.connekt.busybees.streams.flows.FlowMetrics
import com.flipkart.connekt.busybees.streams.flows.profilers.TimedFlowOps._
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels.{Constants, ContactPayload, ContactPayloads}
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.sync.{SyncManager, SyncType}
import com.flipkart.connekt.firefly.flows.dispatchers.{HttpDispatcher, WAContactHttpDispatcherPrepare}
import com.flipkart.connekt.firefly.flows.responsehandlers.WAContactResponseHandler
import com.flipkart.connekt.firefly.models.FlowResponseStatus
import com.typesafe.config.Config

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class WAContactTopology(kafkaConsumerConfig: Config) extends CustomTopology[ContactPayloads, FlowResponseStatus] {

  private val waContactSize: Int = ConnektConfig.getInt("wa.contact.batch.size").getOrElse(1000)
  private val waContactTimeLimit: Int = ConnektConfig.getInt("wa.contact.wait.time.limit.sec").getOrElse(2)
  private final val waContactQueue = Constants.WAConstants.WA_CONTACT_QUEUE.toString
  private lazy val waContactNewRegistrationTopic = ConnektConfig.getString("wa.contact.new.registration.topic.name").get

  override def topologyName: String = Constants.WAConstants.WHATSAPP_CONTACTS.toString

  override def sources: Map[CheckPointGroup, Source[ContactPayloads, NotUsed]] = {
    val waKafkaThrottle = ConnektConfig.getOrElse("wa.contact.throttle.rps", 2)

    val enabledTopology = ListBuffer[String]()
    SyncManager.getNodeData(SyncManager.getBucketNodePath + "/" + SyncType.WA_CONTACT_TOPOLOGY_UPDATE).map(_.message.head) match {
      case Some("stop") =>
        ConnektLogger(LogFile.SERVICE).info(s"WaContact Topology stopped by admin.")
      case _ =>
        enabledTopology += Constants.WAConstants.WHATSAPP_CONTACTS.toString
    }
    enabledTopology.flatMap { channel =>
      Map(channel -> createMergedSource[ContactPayload]("wa_check_contact_topology", Seq(waContactNewRegistrationTopic, waContactQueue), kafkaConsumerConfig)
        .groupedWithin(waContactSize, waContactTimeLimit.second)
        .throttle(waKafkaThrottle, 1.second, waKafkaThrottle, ThrottleMode.Shaping)
        .via(Flow[Seq[ContactPayload]].map {
          ContactPayloads
        }))
    }.toMap
  }

  def waContactTransformFlow: Flow[ContactPayloads, FlowResponseStatus, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b =>

    val dispatcherPrepFlow = b.add(new WAContactHttpDispatcherPrepare().flow)
    val httpCachedClient = b.add(HttpDispatcher.insecureHttpFlow.timedAs("waContactRTT"))
    val waContactResponseFormatter = b.add(new WAContactResponseHandler().flow)

    dispatcherPrepFlow ~> httpCachedClient ~> waContactResponseFormatter

    FlowShape(dispatcherPrepFlow.in, waContactResponseFormatter.out)
  })

  def sink: Sink[FlowResponseStatus, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit b =>
    val metrics = b.add(new FlowMetrics[FlowResponseStatus]("wa.contact").flow)
    metrics ~> Sink.ignore
    SinkShape(metrics.in)
  })

  override def transformers: Map[CheckPointGroup, Flow[ContactPayloads, FlowResponseStatus, NotUsed]] = Map(Constants.WAConstants.WHATSAPP_CONTACTS.toString -> waContactTransformFlow)
}
