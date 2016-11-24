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
package com.flipkart.connekt.busybees.streams.topologies

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl._
import com.flipkart.connekt.busybees.BusyBeesBoot
import com.flipkart.connekt.busybees.streams.ConnektTopology
import com.flipkart.connekt.busybees.streams.flows.dispatchers._
import com.flipkart.connekt.busybees.streams.flows.formaters._
import com.flipkart.connekt.busybees.streams.flows.profilers.TimedFlowOps._
import com.flipkart.connekt.busybees.streams.flows.reponsehandlers._
import com.flipkart.connekt.busybees.streams.flows.transformers.{EmailProviderPrepare, EmailProviderResponseFormatter}
import com.flipkart.connekt.busybees.streams.flows.{ChooseProvider, FlowMetrics, RenderFlow}
import com.flipkart.connekt.busybees.streams.sources.KafkaSource
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.Channel
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile, ServiceFactory}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.sync.SyncType.SyncType
import com.flipkart.connekt.commons.sync.{SyncDelegate, SyncManager, SyncType}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.typesafe.config.Config

import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise

class EmailTopology(kafkaConsumerConfig: Config) extends ConnektTopology[EmailCallbackEvent] with SyncDelegate {

  SyncManager.get().addObserver(this, List(SyncType.CLIENT_QUEUE_CREATE))

  override def onUpdate(_type: SyncType, args: List[AnyRef]): Any = {
    _type match {
      case SyncType.CLIENT_QUEUE_CREATE => Try_ {
        ConnektLogger(LogFile.SERVICE).info(s"EmailTopology Restart for CLIENT_QUEUE_CREATE Client: ${args.head}, New Topic: ${args.last} ")
        restart
      }
      case _ =>
    }
  }

  private def createMergedSource(checkpointGroup: CheckPointGroup, topics: Seq[String]): Source[ConnektRequest, NotUsed] = Source.fromGraph(GraphDSL.create() { implicit b =>

    val groupId = kafkaConsumerConfig.getString("group.id")
    ConnektLogger(LogFile.PROCESSORS).info(s"EMAIL:: Creating composite source for topics: ${topics.toString()}")

    val merge = b.add(Merge[ConnektRequest](topics.size))

    for (portNum <- 0 until merge.n) {
      val p = Promise[String]()
      val consumerGroup = s"${groupId}_$checkpointGroup"
      new KafkaSource[ConnektRequest](kafkaConsumerConfig, topic = topics(portNum), consumerGroup)(p.future) ~> merge.in(portNum)
      sourceSwitches += p
    }

    SourceShape(merge.out)
  })

  override def sources: Map[CheckPointGroup, Source[ConnektRequest, NotUsed]] = {
    List(Channel.EMAIL).flatMap {value =>
      ServiceFactory.getEmailMessageService.getTopicNames(Channel.EMAIL, None).get match {
        case platformTopics if platformTopics.nonEmpty => Option(value.toString -> createMergedSource(value, platformTopics))
        case _ => None
      }
    }.toMap
  }


  override def sink: Sink[EmailCallbackEvent, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit b =>

    val metrics = b.add(new FlowMetrics[EmailCallbackEvent](Channel.EMAIL).flow)
    metrics ~> Sink.ignore

    SinkShape(metrics.in)
  })

  override def shutdown() = {
    /* terminate in top-down approach from all Source(s) */
    sourceSwitches.foreach(_.success("EmailTopology signal source shutdown"))
  }

  def emailHTTPTransformFlow = Flow.fromGraph(GraphDSL.create() { implicit b =>

    val render = b.add(new RenderFlow().flow)
    val fmtEmailParallelism = ConnektConfig.getInt("topology.email.formatter.parallelism").get
    val fmtEmail = b.add(new EmailChannelFormatter(fmtEmailParallelism)(ioDispatcher).flow)

    val providerPicker = b.add(new ChooseProvider[EmailPayloadEnvelope](Channel.EMAIL).flow)
    val providerHttpPrepare = b.add(new EmailProviderPrepare().flow)
    val emailPoolFlow = b.add(HttpDispatcher.emailPoolClientFlow.timedAs("emailRTT"))
    val providerResponseFormatter = b.add(new EmailProviderResponseFormatter()(ioMat, ec).flow)
    val emailResponseHandle = b.add(new EmailResponseHandler().flow)

    render.out ~>  fmtEmail ~> providerPicker ~> providerHttpPrepare ~> emailPoolFlow ~> providerResponseFormatter ~> emailResponseHandle

    FlowShape(render.in, emailResponseHandle.out)
  })

  override def transformers: Map[CheckPointGroup, Flow[ConnektRequest, EmailCallbackEvent, NotUsed]] = {
    Map(Channel.EMAIL.toString -> emailHTTPTransformFlow)
  }
}
