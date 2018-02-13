package com.flipkart.connekt.busybees.streams.flows.dispatchers

import akka.actor.ActorSystem
import akka.stream.{Attributes, FanOutShape2, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic}
import com.flipkart.connekt.busybees.streams.flows.APNorFCMLogic
import com.flipkart.connekt.commons.iomodels.ConnektRequest

class APNorFCMDiverger(implicit actorSystem:ActorSystem) extends GraphStage[FanOutShape2[ConnektRequest,ConnektRequest,ConnektRequest]] {

  val in = Inlet[ConnektRequest]("APNorFCMDiverger.in")
  val out0 = Outlet[ConnektRequest]("APNorFCMDiverger.APN")
  val out1 = Outlet[ConnektRequest]("APNorFCMDiverger.FCM")

  override def shape = new FanOutShape2[ConnektRequest,ConnektRequest,ConnektRequest](in, out0, out1)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new APNorFCMLogic(shape)
}
