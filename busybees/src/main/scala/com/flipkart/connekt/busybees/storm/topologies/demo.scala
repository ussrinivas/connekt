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
package com.flipkart.connekt.busybees.storm.topologies

import com.flipkart.connekt.busybees.storm.bolts.AndroidFilterBolt
import org.apache.storm.{Config, LocalCluster, StormSubmitter}
import org.apache.storm.kafka.spout.{KafkaSpout, KafkaSpoutConfig}
import org.apache.storm.topology.TopologyBuilder

/**
  * Created by saurabh.mimani on 27/10/17.
  */
object demo {


  def main(args: Array[String]): Unit = {

  val env = "local"
  val builder = new TopologyBuilder

  val bootStrapServers = "localhost:9092"
  val topic = "t1"
  val consumerGroupId = "1"
  val spoutConf =  KafkaSpoutConfig.builder(bootStrapServers, topic)
    .setGroupId(consumerGroupId)
    .setOffsetCommitPeriodMs(10000)
    .setFirstPollOffsetStrategy(KafkaSpoutConfig.FirstPollOffsetStrategy.UNCOMMITTED_LATEST)
    .setMaxUncommittedOffsets(1000000)
    .build()

  builder.setSpout("kafka_spout", new KafkaSpout(spoutConf), 1)
  builder.setBolt("androidFilter", new AndroidFilterBolt,1).shuffleGrouping("kafka-spout")

  val conf = new Config()
  conf.setDebug(true)


  if ("PRODUCTION" == env) {
    conf.setNumWorkers(3)
    StormSubmitter.submitTopology("sendAndroidPN", conf, builder.createTopology())
  }
  else {
    conf.setMaxTaskParallelism(3)
    val cluster = new LocalCluster
    cluster.submitTopology("sendAndroidPN", conf, builder.createTopology())
    Thread.sleep(10000)
    cluster.shutdown()
  }
}
}
