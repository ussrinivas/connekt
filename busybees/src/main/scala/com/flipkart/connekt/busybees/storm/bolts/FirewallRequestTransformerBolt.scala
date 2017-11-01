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

import akka.http.scaladsl.model._
import com.flipkart.connekt.busybees.storm.models.HttpRequestAndTracker
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.Stencil
import com.flipkart.connekt.commons.factories.ServiceFactory
import com.flipkart.connekt.commons.services.ConnektConfig
import com.flipkart.connekt.commons.utils.StringUtils._
import org.apache.storm.topology.base.BaseBasicBolt
import org.apache.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import org.apache.storm.tuple.{Fields, Tuple, TupleImpl, Values}

class FirewallRequestTransformerBolt extends BaseBasicBolt {

  private val firewallStencilId: Option[String] = ConnektConfig.getString("sys.firewall.stencil.id")

  override def execute(input: Tuple, collector: BasicOutputCollector): Unit = {

    val stencilService = ServiceFactory.getStencilService
    val uriTransformer: Option[Stencil] = firewallStencilId.flatMap(stencilService.get(_).headOption)

    val hRT = input.asInstanceOf[TupleImpl].get("gcmHttpDispatcherPreparedRequest").asInstanceOf[HttpRequestAndTracker]
    val request = hRT.httpRequest
    val tracker = hRT.requestTracker

    val updatedRequest = Try_ {
      uriTransformer match {
        case None => request
        case Some(stn) =>
          request.copy(
            uri = Uri(stencilService.materialize(stn, Map("uri" -> request.uri.toString).getJsonNode).asInstanceOf[String])
          )
      }
    }.getOrElse(request)

    collector.emit(new Values(HttpRequestAndTracker(updatedRequest, tracker)))
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer): Unit = {
    declarer.declare(new Fields("firewallRequestTransformeredRequest"))
  }

}
