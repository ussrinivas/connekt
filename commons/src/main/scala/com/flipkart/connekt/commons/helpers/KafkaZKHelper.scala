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
package com.flipkart.connekt.commons.helpers

import com.typesafe.config.Config
import kafka.utils.{ZKGroupTopicDirs, ZkUtils}

import scala.collection.mutable

trait KafkaZKHelper {

  private val zkClients : mutable.HashMap[String,ZkUtils] = mutable.HashMap[String,ZkUtils]()

  def getClient(zkPath:String) = zkClients.getOrElseUpdate(zkPath,ZkUtils(zkUrl = zkPath, sessionTimeout = 5000, connectionTimeout = 5000, isZkSecurityEnabled = false))

  def offsets(topic: String, groupId: String, zkPath: String): Map[Int, (Long, String)] = {

    val zkClient =  getClient(zkPath)
    val partitions =  zkClient.getPartitionsForTopics(List(topic))

    partitions.flatMap(topicAndPart => {
      val topicDirs = new ZKGroupTopicDirs(groupId, topic)
      topicAndPart._2.map(partitionId => {
        val zkPath = s"${topicDirs.consumerOffsetDir}/$partitionId"
        val ownerPath = s"${topicDirs.consumerOwnerDir}/$partitionId"
        val owner = zkClient.readDataMaybeNull(ownerPath)._1.getOrElse("No owner")
        val checkPoint = zkClient.readDataMaybeNull(zkPath)._1.map(_.toLong).getOrElse(0L)
        partitionId -> Tuple2(checkPoint, owner)
      })
    }).toMap
  }

  def zkPath(kafkaConfig: Config): String = kafkaConfig.getString("zookeeper.connect")

}
