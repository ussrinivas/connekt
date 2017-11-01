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

import akka.stream.scaladsl.Flow
import com.flipkart.connekt.commons.entities.MobilePlatform
import com.flipkart.connekt.commons.iomodels.{ConnektRequest, PNRequestInfo}
import org.apache.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import org.apache.storm.topology.base.BaseBasicBolt
import org.apache.storm.tuple.{Fields, Tuple, TupleImpl, Values}
import com.flipkart.connekt.commons.utils.StringUtils._

/**
  * Created by saurabh.mimani on 27/10/17.
  */
class AndroidFilterBolt extends BaseBasicBolt {

  override def execute(input: Tuple, collector: BasicOutputCollector): Unit = {
    val connektRequestStr = input.asInstanceOf[TupleImpl].get("value").asInstanceOf[String]
    val connektRequest = connektRequestStr.getObj[ConnektRequest]
    val platform = connektRequest.channelInfo.asInstanceOf[PNRequestInfo].platform.toLowerCase
    if(MobilePlatform.ANDROID.toString.equalsIgnoreCase(platform)){
      collector.emit(new Values(connektRequest))
    }
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer): Unit = {
    declarer.declare(new Fields("filteredMessage"))
  }
}
