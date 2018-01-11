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
package com.flipkart.connekt.commons.services

import com.flipkart.connekt.commons.entities.AppUser
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.iomodels.ConnektRequest

import scala.util.Try

trait TMessageService extends TService {

  def saveRequest(request: ConnektRequest, requestBucket: String): Try[String]

  def bulkPersist(requests: List[ConnektRequest]): List[String]

  def getRequestBucket(request: ConnektRequest, client: AppUser): String

  def assignClientChannelTopic(channel: Channel, clientUserId: String): String

  def getClientChannelTopic(channel: Channel, clientUserId: String): String

  def enqueueRequest(request: ConnektRequest, requestBucket: String)

  def getRequestInfo(id: String): Try[Option[ConnektRequest]]

  def getRequestInfo(ids: List[String]): Try[List[ConnektRequest]]

  def addClientTopic(topicName: String, numPartitions: Int, replicationFactor: Int = 1): Try[Unit]

  def partitionEstimate(qpsBound: Int): Int

  def getKafkaTopicNames(channel: Channel): Try[Seq[String]]

  def getTopicNames(channel: Channel, platform: Option[String]): Try[Seq[String]]
}
