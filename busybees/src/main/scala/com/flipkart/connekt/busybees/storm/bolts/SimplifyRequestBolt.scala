package com.flipkart.connekt.busybees.storm.bolts

import com.flipkart.connekt.commons.iomodels.GCMPayloadEnvelope
import org.apache.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import org.apache.storm.topology.base.BaseBasicBolt
import org.apache.storm.tuple.{Fields, Tuple, TupleImpl, Values}

class SimplifyRequestBolt extends BaseBasicBolt {
  override def execute(input: Tuple, collector: BasicOutputCollector): Unit = {
    val messages = input.asInstanceOf[TupleImpl].get("formattedRequest").asInstanceOf[List[GCMPayloadEnvelope]]
    messages.foreach(message => collector.emit(new Values(message)))
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer): Unit =  declarer.declare(new Fields("simplifyRequest"))
}
