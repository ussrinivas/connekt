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
package com.flipkart.connekt.busybees.storm.bolts

import java.util.Random


import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PNRequestInfo}
import com.flipkart.connekt.commons.services.ConnektConfig
import org.apache.storm.topology.base.BaseBasicBolt
import org.apache.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import org.apache.storm.tuple.{Fields, Tuple, Values}
import com.flipkart.connekt.commons.utils.StringUtils._


/**
  * Created by saurabh.mimani on 27/10/17.
  */
class NotificationQueueRecorderBolt extends BaseBasicBolt {
  private lazy val xmppClients = ConnektConfig.getList[String]("android.protocol.xmpp-clients")

  override def execute(input: Tuple, collector: BasicOutputCollector): Unit = {
    val connektRequest = input.getString(0).getObj[ConnektRequest]
    val pnInfo = connektRequest.channelInfo.asInstanceOf[PNRequestInfo]
    val protocol = if (pnInfo.deviceIds.size > 2 || !xmppClients.contains(connektRequest.clientId)) AndroidProtocols.http else chooseProtocol
    protocol match {
      case AndroidProtocols.http => collector.emit(AndroidProtocols.http, new Values(connektRequest))
      case AndroidProtocols.xmpp => collector.emit(AndroidProtocols.xmpp, new Values(connektRequest))
    }
  }

  private lazy val xmppShare: Int = ConnektConfig.getInt("android.protocol.xmpp-share").getOrElse(100)
  private lazy val randomGenerator = new Random()
  private def chooseProtocol = if ( randomGenerator.nextInt(100) < xmppShare ) AndroidProtocols.xmpp else AndroidProtocols.http

  override def declareOutputFields(declarer: OutputFieldsDeclarer): Unit = {
    declarer.declareStream(AndroidProtocols.http, new Fields("ChannelConnection"))
    declarer.declareStream(AndroidProtocols.xmpp, new Fields("ChannelConnection"))
  }
}
