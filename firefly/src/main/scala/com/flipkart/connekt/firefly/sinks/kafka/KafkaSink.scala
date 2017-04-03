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
package com.flipkart.connekt.firefly.sinks.kafka

import java.util.Properties

import akka.stream.scaladsl.Sink
import com.flipkart.connekt.commons.entities.SubscriptionEvent
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.utils.StringUtils._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

class KafkaSink(topic: String, brokers: String) {

  private val props = new Properties()
  props.put("bootstrap.servers", brokers)
  props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

  private val producer = new KafkaProducer[String, String](props)

  def getKafkaSink = Sink.foreach[SubscriptionEvent](e => {
    ConnektLogger(LogFile.SERVICE).info(s"KafkaSink event written to $topic at $brokers")
    ConnektLogger(LogFile.SERVICE).trace(s"KafkaSink event written to $topic at $brokers : {}", supplier(e.payload.toString))

    val data: ProducerRecord[String, String] = new ProducerRecord(topic, e.payload.toString)
    producer.send(data)
  })

}
