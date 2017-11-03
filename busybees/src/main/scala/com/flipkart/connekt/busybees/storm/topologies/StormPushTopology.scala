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

import com.flipkart.connekt.busybees.BusyBeesBoot
import com.flipkart.connekt.busybees.BusyBeesBoot.{apiVersion, configServiceHost, configServicePort}
import com.flipkart.connekt.busybees.storm.bolts._
import com.flipkart.connekt.busybees.streams.flows.formaters.AndroidXmppChannelFormatter
import com.flipkart.connekt.busybees.xmpp.XmppDispatcherBolt
import com.flipkart.connekt.commons.connections.ConnectionProvider
import com.flipkart.connekt.commons.dao.DaoFactory
import com.flipkart.connekt.commons.services.{ConnektConfig, DeviceDetailsService}
import com.flipkart.connekt.commons.utils.ConfigUtils
import org.apache.storm.{Config, LocalCluster, StormSubmitter}
import org.apache.storm.kafka.spout.{KafkaSpout, KafkaSpoutConfig}
import org.apache.storm.topology.TopologyBuilder

/**
  * Created by saurabh.mimani on 27/10/17.
  */
object StormPushTopology {

  def main(args: Array[String]): Unit = {
    setup()
    val env = "local"
    val builder = new TopologyBuilder

    val bootStrapServers = "10.32.230.105:9092,10.33.249.164:9092,10.33.205.205:9092"
    val topic = "push_8e494f43126ade96ce7320ad8ccfc709"
    val consumerGroupId = "ckt_android"
    val spoutConf = KafkaSpoutConfig.builder(bootStrapServers, topic)
      .setGroupId(consumerGroupId)
      .setOffsetCommitPeriodMs(10000)
      .setFirstPollOffsetStrategy(KafkaSpoutConfig.FirstPollOffsetStrategy.UNCOMMITTED_EARLIEST)
      .setMaxUncommittedOffsets(1000000)
      .build()

    builder.setSpout("kafkaSpout", new KafkaSpout(spoutConf), 1)
    builder.setBolt("androidFilter", new AndroidFilterBolt, 1).shuffleGrouping("kafkaSpout")
    builder.setBolt("ConnectionChooserBolt", new ChannelConnectionChooserBolt, 1).shuffleGrouping("androidFilter")

    builder.setBolt("xmppFormatterBolt", new AndroidXmppChannelFormatterBolt, 1).shuffleGrouping("ConnectionChooserBolt", "xmpp")
    builder.setBolt("httpFormatterBolt", new AndroidHttpChannelFormatterBolt, 1).shuffleGrouping("ConnectionChooserBolt", "http")

    builder.setBolt("simplifyRequestBolt", new SimplifyRequestBolt, 1).shuffleGrouping("httpFormatterBolt")
    builder.setBolt("gcmHttpDispatcherPrepareBolt", new GCMHttpDispatcherPrepareBolt, 1).shuffleGrouping("simplifyRequestBolt")
    builder.setBolt("firewallRequestTransformerBolt", new FirewallRequestTransformerBolt, 1).shuffleGrouping("gcmHttpDispatcherPrepareBolt")
    builder.setBolt("httpDispatcherBolt", new HttpDispatcherBolt, 1).shuffleGrouping("firewallRequestTransformerBolt")
    builder.setBolt("gcmResponseHandlerBolt", new GCMResponseHandlerBolt, 1).shuffleGrouping("httpDispatcherBolt")

    builder.setBolt("simplifyRequestBolt", new SimplifyRequestBolt, 1).shuffleGrouping("xmppFormatterBolt")
    builder.setBolt("xmppDispatcherBolt", new XmppDispatcherBolt, 1).shuffleGrouping("simplifyRequestBolt")


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
      //    Thread.sleep(10000)
      //    cluster.shutdown()
    }
  }

  def setup(): Unit = {
    BusyBeesBoot.start()
  }
}
