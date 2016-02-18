package com.flipkart.connekt.busybees.streams.sources

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.helpers.KafkaConsumerHelper
import com.flipkart.connekt.commons.utils.StringUtils._
import kafka.consumer.ConsumerConnector
import kafka.message.MessageAndMetadata
import kafka.serializer.{Decoder, DefaultDecoder}
import kafka.utils.{ZKStringSerializer, ZkUtils}
import org.I0Itec.zkclient.ZkClient

import scala.collection.JavaConversions._
import scala.reflect.ClassTag

/**
 *
 *
 * @author durga.s
 * @version 1/28/16
 */
class KafkaSource[V: ClassTag](kafkaConsumerHelper: KafkaConsumerHelper, topic: String) extends GraphStage[SourceShape[V]] {

  val out: Outlet[V] = Outlet("KafkaMessageSource.Out")

  def commitOffset(o: Long) = {}

  override def shape: SourceShape[V] = SourceShape(out)

  lazy val zk = new ZkClient(KafkaConsumerHelper.zkPath, 5000, 5000, ZKStringSerializer)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    var kafkaConsumerConnector: ConsumerConnector = null
    var iterator: Iterator[MessageAndMetadata[Array[Byte], V]] = null

    private def getTopicPartitionCount(topic: String): Int = try {
      ZkUtils.getPartitionsForTopics(zk, Seq(topic)).get(topic).size
    } catch {
      case e: Exception =>
        ConnektLogger(LogFile.PROCESSORS).error(s"KafkaSource ZK Error", e)
        6
    }

    private def initIterator(kafkaConnector: ConsumerConnector): Iterator[MessageAndMetadata[Array[Byte], V]] = {

      val threadCount = Math.max(1, getTopicPartitionCount(topic) / 4) // TODO : Change this factor based on number of readers
      ConnektLogger(LogFile.PROCESSORS).info(s"KafkaSource Init Topic[$topic], Readers[$threadCount]")

      kafkaConsumerConnector.commitOffsets
      val consumerStreams = kafkaConnector.createMessageStreams[Array[Byte], V](Map[String, Int](topic -> threadCount), new DefaultDecoder(), new MessageDecoder[V]())
      val streams = consumerStreams.get(topic)
      streams match {
        case Some(s) =>
          s.map(_.iterator().asInstanceOf[java.util.Iterator[MessageAndMetadata[Array[Byte], V]]].toIterator).foldLeft(Iterator.empty.asInstanceOf[Iterator[MessageAndMetadata[Array[Byte], V]]])(_ ++ _)
        case None =>
          throw new Exception(s"No KafkaStreams for topic: $topic")
      }
    }

    private def createKafkaConsumer(): Unit = {
      ConnektLogger(LogFile.PROCESSORS).info(s"KafkaSource::createKafkaConsumer")

      kafkaConsumerConnector = kafkaConsumerHelper.getConnector
      iterator = initIterator(kafkaConsumerConnector)
    }

    setHandler(out, new OutHandler {
      override def onPull(): Unit = try {
        ConnektLogger(LogFile.PROCESSORS).info("KafkaFlow:: OnPull")
        val m = iterator.next()
        ConnektLogger(LogFile.PROCESSORS).info(s"KafkaSource::OnPull:: ${m.message().toString}")
        commitOffset(m.offset) //TODO
        push(out, m.message())
      } catch {
        case e: Exception =>
          ConnektLogger(LogFile.PROCESSORS).error(s"Kafka iteration error: ${e.getMessage}", e)
          kafkaConsumerHelper.returnConnector(kafkaConsumerConnector)
          createKafkaConsumer()
      }
    })

    override def preStart(): Unit = {
      createKafkaConsumer()
      super.preStart()
    }

  }
}

class MessageDecoder[T: ClassTag](implicit tag: ClassTag[T]) extends Decoder[T] {
  override def fromBytes(bytes: Array[Byte]): T = objMapper.readValue(bytes.getString, tag.runtimeClass).asInstanceOf[T]
}


