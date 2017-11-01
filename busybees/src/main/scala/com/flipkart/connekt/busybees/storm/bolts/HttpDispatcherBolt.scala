package com.flipkart.connekt.busybees.storm.bolts

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.flipkart.connekt.busybees.storm.models.{HttpRequestAndTracker, HttpResponseAndTracker}
import org.apache.storm.topology.base.BaseBasicBolt
import org.apache.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import org.apache.storm.tuple.{Fields, Tuple, TupleImpl, Values}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

class HttpDispatcherBolt extends BaseBasicBolt {
  override def execute(input: Tuple, collector: BasicOutputCollector): Unit = {
    implicit val httpSystem: ActorSystem = ActorSystem("http-out")
    implicit val httpMat: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = httpSystem.dispatcher

    val hRT = input.asInstanceOf[TupleImpl].get("firewallRequestTransformeredRequest").asInstanceOf[HttpRequestAndTracker]
    val responseEntity = Http().singleRequest(hRT.httpRequest)
    responseEntity.onComplete {
      case Success(response) => collector.emit(new Values(HttpResponseAndTracker(response, hRT.requestTracker)))
      case Failure(f) => println("Failed " + f.getCause)
    }
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer): Unit = {
    declarer.declare(new Fields("httpDispatchedRequest"))
  }
}
