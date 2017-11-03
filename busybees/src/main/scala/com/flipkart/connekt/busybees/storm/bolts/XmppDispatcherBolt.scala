//package com.flipkart.connekt.busybees.storm.bolts
package com.flipkart.connekt.busybees.xmpp

import java.util
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocketFactory

import com.flipkart.connekt.busybees.models.GCMRequestTracker
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.busybees.xmpp._
import com.flipkart.connekt.busybees.xmpp.Internal.{ConnectionClosed, GCM_ELEMENT_NAME, GOOGLE_NAMESPACE}
import com.flipkart.connekt.busybees.xmpp.XmppConnectionActor.{PendingUpstreamMessage, SendXmppOutStreamRequest}
import com.flipkart.connekt.commons.core.Wrappers.Try_
import com.flipkart.connekt.commons.entities.{GoogleCredential, MobilePlatform}
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.iomodels._
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.services.{ConnektConfig, KeyChainManager}
import com.flipkart.connekt.commons.utils.StringUtils._
import com.google.common.cache._
import org.apache.storm.task.{OutputCollector, TopologyContext}
import org.apache.storm.topology.base.{BaseBasicBolt, BaseRichBolt}
import org.apache.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import org.apache.storm.tuple.{Fields, Tuple, TupleImpl, Values}
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.{ConnectionConfiguration, ConnectionListener, ReconnectionManager, StanzaListener, XMPPConnection}
import org.jivesoftware.smack.filter.StanzaFilter
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.provider.{ExtensionElementProvider, ProviderManager}
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.tcp.{XMPPTCPConnection, XMPPTCPConnectionConfiguration}
import org.jivesoftware.smackx.ping.PingManager
import org.xmlpull.v1.XmlPullParser

import scala.util.{Failure, Success, Try}

/**
  * Created by saurabh.mimani on 27/10/17.
  */
class XmppDispatcherBolt extends BaseRichBolt {
  private val xmppHost: String = ConnektConfig.get("fcm.ccs.hostname").getOrElse("fcm-xmpp.googleapis.com")
  private val xmppPort: Int = ConnektConfig.getInt("fcm.ccs.port").getOrElse(5235)
  val pendingAckCount = new AtomicInteger(0)
  private val pingInterval = ConnektConfig.getInt("sys.ping.interval").getOrElse(30)
  private val xmppDebug = ConnektConfig.getBoolean("fcm.xmpp.debug").getOrElse(false)
  var connection: XMPPTCPConnection = _
  val archivedConnections = new scala.collection.mutable.HashSet[XMPPTCPConnection]()
  var _collector: OutputCollector = _

  private val removalListener = new RemovalListener[String, (SendXmppOutStreamRequest, Long)]() {
    def onRemoval(removal: RemovalNotification[String, (SendXmppOutStreamRequest, Long)]) {
      if (removal.getCause != RemovalCause.EXPLICIT) {
        val messageData: SendXmppOutStreamRequest = removal.getValue._1
        ConnektLogger(LogFile.CLIENTS).error(s"XmppConnectionActor RemoveListener:Cache timed out with tracker id ${removal.getKey}")
        messageData.responsePromise.failure(new XmppNeverAckException(removal.getKey))
        // TODO: How to publish this metrics
//        timer(s"$appId.lost").update(System.currentTimeMillis() - removal.getValue._2, TimeUnit.MILLISECONDS)
        pendingAckCount.decrementAndGet()
      }
    }
  }

  private val messageDataCache = CacheBuilder.newBuilder()
    .expireAfterWrite(2, TimeUnit.MINUTES)
    .removalListener(removalListener)
    .maximumSize(4096)
    .build()
    .asInstanceOf[Cache[String, (SendXmppOutStreamRequest, Long)]]

  override def prepare(stormConf: util.Map[_, _], context: TopologyContext, collector: OutputCollector): Unit = {
    super.prepare(stormConf, context, collector)
    _collector = collector
    getConnection()
  }

  override def cleanup(): Unit = {
    super.cleanup()
    if (connection != null && connection.isConnected) {
      connection.disconnect()
      connection.instantShutdown()
    }
  }

  override def finalize(): Unit = {
    super.finalize()
    if (connection != null && connection.isConnected) {
      connection.disconnect()
      connection.instantShutdown()
    }
  }

  override def execute(input: Tuple): Unit = {
    val xmppRequest = input.asInstanceOf[TupleImpl].get("gcmXmppRequest").asInstanceOf[FCMXmppRequest]
    val xmppRequestTracker = input.asInstanceOf[TupleImpl].get("gcmXmppRequestTracker").asInstanceOf[GCMRequestTracker]

    processSendRequest(xmppRequest)

    if(MobilePlatform.ANDROID.toString.equalsIgnoreCase(platform)) {
      _collector.emit(new Values(connektRequest))
    }
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer): Unit = {
    declarer.declare(new Fields("filteredMessage"))
  }

  private def processSendRequest(xmppRequest: SendXmppOutStreamRequest) = {
    sendXmppStanza(xmppRequest.request.pnPayload) match {
      case Success(_) =>
        messageDataCache.put(xmppRequest.request.pnPayload.message_id, (xmppRequest, System.currentTimeMillis()))
        pendingAckCount.incrementAndGet()

//        if (pendingAckCount.get() >= maxPendingAckCount) {
//          become(busy)
//          parent ! ConnectionBusy
//          ConnektLogger(LogFile.CLIENTS).debug(s"XmppConnectionActor for $appId turned busy")
//        } else {
//          parent ! FreeConnectionAvailable
//        }
      case Failure(ex) =>
        xmppRequest.responsePromise.failure(ex)
        _collector.ack()
        parent ! FreeConnectionAvailable
    }
  }

  private def sendXmppStanza(payload: AnyRef): Try[Unit] = {
    val xmppPayloadString = payload.getJson
    val stanza = new GcmXmppPacketExtension(xmppPayloadString)

    try {
      ConnektLogger(LogFile.CLIENTS).trace("XmppConnectionActor Send {}", xmppPayloadString )
      val xmppConnection = getConnection()
      if(xmppConnection.isSmEnabled()) {
        Try_#("xmppConnection issue: ") {
          xmppConnection.addStanzaIdAcknowledgedListener(stanza.getStanzaId(), new StanzaListener(){
            override def processPacket(packet:Stanza): Unit ={
              // Extract the GCM message from the packet.
              val packetExtension:GcmXmppPacketExtension = packet.getExtension(Internal.GOOGLE_NAMESPACE).asInstanceOf[GcmXmppPacketExtension]

              Try (packetExtension.json.getObj[XmppResponse]) match {
                case Success(downStreamMsg:XmppDownstreamResponse) =>
                  ConnektLogger(LogFile.CLIENTS).debug("De Serialised to downstream:" + downStreamMsg)
                  handleDownStreamMsg(downStreamMsg)
                case Success(upstreamMsg:XmppUpstreamResponse) =>
                  ConnektLogger(LogFile.CLIENTS).debug("De Serialised to upstream:" + upstreamMsg)
                  handleUpStreamMsg(upstreamMsg)
                case Failure(thrown) =>
                  ConnektLogger(LogFile.CLIENTS).error("Failed to down/upstream:" + packetExtension.json, thrown)
                case _ =>
                  ConnektLogger(LogFile.CLIENTS).error("StanzaListener Unhandled Match:" + packetExtension.json)
              }
            }
          })
        }
      }
      xmppConnection.sendStanza(stanza)
      Success(Unit)
    } catch {
      case ex: Throwable =>
        ConnektLogger(LogFile.CLIENTS).error("Send Exception during send to GCM. jsonRequest : " + xmppPayloadString, ex)
        Failure(ex)
    }
  }

  private def handleDownStreamMsg(downStreamMsg:XmppDownstreamResponse) : Unit = {
    downStreamMsg match {
      case ack: XmppAck => processAck(ack)
      case nack: XmppNack => processNack(nack)
      case control: XmppControl if control.controlType.equalsIgnoreCase("CONNECTION_DRAINING") =>
          processConnectionDraining()
          connection = createConnection()
      case _ =>
    }
  }

  private def processAck(ack: XmppAck) = {
    val (outStreamRequest, sentTime) = messageDataCache.getIfPresent(ack.messageId)
    if (outStreamRequest != null) {
      messageDataCache.invalidate(ack.messageId)
      pendingAckCount.decrementAndGet()
      outStreamRequest.responsePromise.success(ack)
      //      TODO: Add Timer here
      //      timer(s"$appId.ack").update(System.currentTimeMillis() - sentTime, TimeUnit.MILLISECONDS)
    }
  }

  private def processNack(nack: XmppNack) = {
    val (outStreamRequest, sentTime) = messageDataCache.getIfPresent(nack.messageId)
    if (outStreamRequest != null) {
      messageDataCache.invalidate(nack.messageId)
      pendingAckCount.decrementAndGet()
      outStreamRequest.responsePromise.failure(new XmppNackException(nack))
      //      TODO: Add Timer here
      //      timer(s"$appId.nack").update(System.currentTimeMillis() - sentTime, TimeUnit.MILLISECONDS)
    }
  }

  private def processConnectionDraining() = {
    ConnektLogger(LogFile.CLIENTS).warn(s"Received ConnectionDraining") //TODO: Add $appId here ?
    ReconnectionManager.getInstanceFor(connection).disableAutomaticReconnection()
    archivedConnections.add(connection)
  }

  private def handleUpStreamMsg(upStreamMsg:XmppUpstreamResponse) : Unit = {

  }

  private def getGoogleCredential() : GoogleCredential = {
    KeyChainManager.getGoogleCredential("retailapp")
  }

  private def getConnection(): XMPPTCPConnection ={
    if(connection == null || !connection.isConnected){
      connection = createConnection()
    }
    connection
  }


  private def createConnection(): XMPPTCPConnection = {
    val googleCredential = getGoogleCredential()
    val connection = new XMPPTCPConnection(XMPPTCPConnectionConfiguration.builder()
      .setHost(xmppHost)
      .setPort(xmppPort)
      .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
      .setSocketFactory(SSLSocketFactory.getDefault)
      .setServiceName(googleCredential.projectId)
      .setSendPresence(false)
      .setDebuggerEnabled(xmppDebug)
      .build())

    ConnektLogger(LogFile.CLIENTS).debug(s"Configuring XMPPConnection")
    Roster.getInstanceFor(connection).setRosterLoadedAtLogin(false)
    PingManager.getInstanceFor(connection).setPingInterval(pingInterval)

    connection.addConnectionListener(new StormConnektXmppConnectionListener(googleCredential.projectId, connection, archivedConnections))

    ProviderManager.addExtensionProvider(GCM_ELEMENT_NAME, GOOGLE_NAMESPACE,
      new ExtensionElementProvider[GcmXmppPacketExtension]() {
        override def parse(parser: XmlPullParser, initialDepth: Int): GcmXmppPacketExtension = {
          val json: String = parser.nextText()
          new GcmXmppPacketExtension(json)
        }
      }
    )

    val stanzaFilter = new StanzaFilter() {
      override def accept(stanza: Stanza): Boolean = stanza.hasExtension(GCM_ELEMENT_NAME, GOOGLE_NAMESPACE)
    }
    //    TODO: SHould we have this or addStanzaIdAcknowledgedListener
    //    val stanzaListener = new ConnektStanzaListener(self, stageLogicRef)
    //    connection.addAsyncStanzaListener(stanzaListener, stanzaFilter)

    ReconnectionManager.getInstanceFor(connection).enableAutomaticReconnection()
    makeConnection(connection, googleCredential)
    connection
  }

  private def makeConnection(connection:XMPPTCPConnection, googleCredential: GoogleCredential): Unit ={
    try {
      connection.connect()

      connection.login(googleCredential.projectId + "@gcm.googleapis.com", googleCredential.apiKey)
    }
    catch {
      case ex: Exception =>
        //TODO how to handle
        ConnektLogger(LogFile.CLIENTS).error("Unable to connect:", ex)
        throw ex
    }
  }
}

private [xmpp] class StormConnektXmppConnectionListener(appId:String, conn:XMPPTCPConnection, archivedConnections:scala.collection.mutable.HashSet[XMPPTCPConnection]) extends ConnectionListener with Instrumented{

  override def connected(connection: XMPPConnection): Unit = {
    counter(s"$appId.connected").inc()
    ConnektLogger(LogFile.CLIENTS).debug(s"XMPPConnectionListener($appId) -> connected : ${conn.getStreamId}")
  }

  override def authenticated(connection: XMPPConnection, resumed: Boolean): Unit = {
    ConnektLogger(LogFile.CLIENTS).info(s"XMPPConnectionListener($appId) -> authenticated: ${conn.getStreamId}")
  }

  override def connectionClosed(): Unit = {
    counter(s"$appId.closed").inc()
    ConnektLogger(LogFile.CLIENTS).error(s"XMPPConnectionListener($appId) -> connectionClosed ${conn.getStreamId}")
    archivedConnections.remove(conn)
  }

  override def connectionClosedOnError(e: Exception): Unit = {
    counter(s"$appId.closed").inc()
    ConnektLogger(LogFile.CLIENTS).error(s"XMPPConnectionListener($appId) -> connectionClosedOnError: ${conn.getStreamId} ${e.getMessage}", e)
  }

  override def reconnectionSuccessful(): Unit = {
    counter(s"$appId.reconnectionSuccessful").inc()
    ConnektLogger(LogFile.CLIENTS).debug(s"XMPPConnectionListener($appId) -> reConnectionSuccess: ${conn.getStreamId}")
  }

  override def reconnectingIn(seconds: Int): Unit = {
    ConnektLogger(LogFile.CLIENTS).debug(s"XMPPConnectionListener($appId) -> reConnectingIn: ${conn.getStreamId} $seconds")
  }

  override def reconnectionFailed(e: Exception): Unit = {
    counter(s"$appId.reconnectionFailed").inc()
    ConnektLogger(LogFile.CLIENTS).error(s"XMPPConnectionListener($appId) -> reConnectionFailure: ${conn.getStreamId} ${e.getMessage}", e)
    archivedConnections.remove(conn)
  }
}
