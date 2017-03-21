package com.flipkart.connekt.commons.dao

import akka.http.scaladsl.util.FastFuture
import com.aerospike.client.Value.StringValue
import com.aerospike.client.async.AsyncClient
import com.aerospike.client.listener.{DeleteListener, RecordListener, WriteListener}
import com.aerospike.client.policy.WritePolicy
import com.aerospike.client._
import com.aerospike.client.cdt.{ListOperation, MapOperation, MapPolicy, MapReturnType}
import com.flipkart.connekt.commons.connections.ConnectionProvider
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.ConnektConfig

import collection.JavaConverters._
import collection.JavaConverters._
import scala.concurrent.{Await, Future, Promise}

import scala.concurrent.duration._

trait AeroSpikeDao extends Instrumented {

  lazy val timeout: Int = 500 //ConnektConfig.getInt("connections.aerospike.timeout").getOrElse(500)

  protected def addRow(key: Key, bin: Bin, ttl: Option[Long] = None)(implicit client: AsyncClient): Future[Boolean] = {
    val policy = new WritePolicy()
    policy.timeout = timeout
    ttl.foreach(millis => policy.expiration = (millis / 1000).toInt)
    val promise = Promise[Boolean]()
    client.put(policy, new AeroSpikeWriteHandler(promise), key, bin)
    promise.future
  }

  protected def addMapRow(key: Key, binName: String, values: Map[String, String], ttl: Option[Long] = None)(implicit client: AsyncClient): Future[Record] = {
    val policy = new WritePolicy()
    policy.timeout = timeout
    ttl.foreach(millis => policy.expiration = (millis / 1000).toInt)

    val data: Map[Value, Value] = values.map { case (k, v) => (new StringValue(k), new StringValue(v)) }
    val promise = Promise[Record]()
    val record = client.operate(policy, key, MapOperation.putItems(MapPolicy.Default, binName, data.asJava))

    println(record)

    promise.future

    FastFuture.successful(new Record(Map.empty[String,AnyRef].asJava,0,0))
  }

  protected def deleteMapRowItems(key: Key, binName: String, keys: List[String])(implicit client: AsyncClient): Future[Record] = {
    val policy = new WritePolicy()
    policy.timeout = timeout

    val _keys: List[Value] = keys.map(new StringValue(_))

    val promise = Promise[Record]()
    client.operate(policy, new AeroSpikeRecordHandler(promise), key, MapOperation.removeByKeyList(binName, _keys.asJava, MapReturnType.COUNT))
    promise.future
  }

  protected def getRow(key: Key)(implicit client: AsyncClient): Future[Record] = {
    val policy = new WritePolicy()
    policy.timeout = timeout
    val promise = Promise[Record]()
    client.get(policy, new AeroSpikeRecordHandler(promise), key)
    promise.future
  }

  protected def deleteRow(key: Key)(implicit client: AsyncClient): Future[Boolean] = {
    val policy = new WritePolicy()
    policy.timeout = timeout
    val promise = Promise[Boolean]()
    client.delete(policy, new AeroSpikeDeleteHandler(promise), key)
    promise.future
  }


}


private class AeroSpikeWriteHandler(promise: Promise[Boolean]) extends WriteListener {
  override def onFailure(exception: AerospikeException): Unit = promise.failure(exception)

  override def onSuccess(key: Key): Unit = promise.success(true)
}

private class AeroSpikeRecordHandler(promise: Promise[Record]) extends RecordListener {
  override def onFailure(exception: AerospikeException): Unit = promise.failure(exception)

  override def onSuccess(key: Key, record: Record): Unit = promise.success(record)
}

private class AeroSpikeDeleteHandler(promise: Promise[Boolean]) extends DeleteListener {
  override def onFailure(exception: AerospikeException): Unit = promise.failure(exception)

  override def onSuccess(key: Key, existed: Boolean): Unit = promise.success(true)
}



object AeroSpikeTest extends App with AeroSpikeDao {



  private val namespace: String = "connekt"
  private val binName:String = "queue"
  private val setName:String = "pull"


  val nodes = List("10.34.73.220","10.32.129.110")
  implicit val aeroSpikeClient = new ConnectionProvider().createAeroSpikeConnection(nodes)

  val keySimple = new Key(namespace, setName, "kinshukSimple")
  val bin = new Bin("d", "lorem-ipsum")
  val resSimple = Await.result(addRow(keySimple, bin ), 30.seconds )
  println(resSimple)

  println( Await.result(getRow(keySimple),30.seconds) )



  val key = new Key(namespace, setName, "kinshuk1")
  val data = Map("key" -> "value")
  val res = Await.result(addMapRow(key, binName, data ), 30.seconds )
  println(res)

  val res2 = Await.result(getRow(key),30.seconds)
  println(res2)



}
