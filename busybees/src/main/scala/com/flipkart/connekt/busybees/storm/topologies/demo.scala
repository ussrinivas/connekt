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

import com.flipkart.connekt.busybees.BusyBeesBoot.{apiVersion, configServiceHost, configServicePort}
import com.flipkart.connekt.busybees.storm.bolts.{AndroidFilterBolt, AndroidHttpChannelFormatterBolt, AndroidXmppChannelFormatterBolt, ChannelConnectionChooserBolt}
import com.flipkart.connekt.busybees.streams.flows.formaters.AndroidXmppChannelFormatter
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
object demo {


  def main(args: Array[String]): Unit = {
    setup()
    val env = "local"
    val builder = new TopologyBuilder

    val bootStrapServers = "localhost:9092"
    val topic = "test"
    val consumerGroupId = "1"
    val spoutConf = KafkaSpoutConfig.builder(bootStrapServers, topic)
      .setGroupId(consumerGroupId)
      .setOffsetCommitPeriodMs(10000)
      .setFirstPollOffsetStrategy(KafkaSpoutConfig.FirstPollOffsetStrategy.UNCOMMITTED_LATEST)
      .setMaxUncommittedOffsets(1000000)
      .build()

    builder.setSpout("kafkaSpout", new KafkaSpout(spoutConf), 1)
    builder.setBolt("androidFilter", new AndroidFilterBolt, 1).shuffleGrouping("kafkaSpout")
    builder.setBolt("ConnectionChooserBolt", new ChannelConnectionChooserBolt, 1).shuffleGrouping("androidFilter")

    builder.setBolt("xmppFormatterBolt", new AndroidXmppChannelFormatterBolt, 1).shuffleGrouping("ConnectionChooserBolt", "xmpp")
    builder.setBolt("httpFormatterBolt", new AndroidHttpChannelFormatterBolt, 1).shuffleGrouping("ConnectionChooserBolt", "http")


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
    val applicationConfigFile = ConfigUtils.getSystemProperty("busybees.appConfigurationFile").getOrElse("busybees-config.json")
    ConnektConfig(configServiceHost, configServicePort, apiVersion)(Seq("fk-connekt-root", "fk-connekt-".concat(ConfigUtils.getConfEnvironment), "fk-connekt-busybees", "fk-connekt-busybees-akka-nm"))(applicationConfigFile)

//    DaoFactory.setUpConnectionProvider(new ConnectionProvider)
//    val hConfig = ConnektConfig.getConfig("connections.hbase")
//    DaoFactory.initHTableDaoFactory(hConfig.get)
//    DeviceDetailsService.bootstrap()

  }
}
