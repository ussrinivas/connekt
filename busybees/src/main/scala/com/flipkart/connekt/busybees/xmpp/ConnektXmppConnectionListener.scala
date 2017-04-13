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
package com.flipkart.connekt.busybees.xmpp

import akka.actor.ActorRef
import com.flipkart.connekt.busybees.xmpp.Internal.ConnectionClosed
import com.flipkart.connekt.commons.factories.{ConnektLogger, LogFile}
import com.flipkart.connekt.commons.metrics.Instrumented
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.{ConnectionListener, XMPPConnection}

private [xmpp] class ConnektXmppConnectionListener(appId:String, conn:XMPPTCPConnection, actorRef: ActorRef) extends ConnectionListener with Instrumented{

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
    actorRef ! ConnectionClosed(conn)
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
    actorRef ! ConnectionClosed(conn)
  }
}
