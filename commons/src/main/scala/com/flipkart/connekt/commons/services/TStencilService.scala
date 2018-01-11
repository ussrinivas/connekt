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

import com.fasterxml.jackson.databind.node.ObjectNode
import com.flipkart.connekt.commons.entities.{Bucket, Stencil, StencilsEnsemble}
import scala.util.Try

trait TStencilService extends TService {

  def checkStencil(stencil: Stencil): Try[Boolean]

  def materialize(stencil: Stencil, req: ObjectNode, logRef: Option[String] = None): AnyRef

  def add(id: String, stencils: List[Stencil]): Try[Unit]

  def delete(id: String): Try[Unit]

  def update(id: String, stencils: List[Stencil]): Try[Unit]

  def updateWithIdentity(id: String, prevName: String, stencils: List[Stencil]): Try[Unit]

  def get(id: String, version: Option[String] = None): List[Stencil]

  def getStencilsByName(name: String, version: Option[String] = None): List[Stencil]

  def getBucket(name: String): Option[Bucket]

  def addBucket(bucket: Bucket): Try[Unit]

  def getStencilsEnsemble(id: String): Option[StencilsEnsemble]

  def getStencilsEnsembleByName(name: String): Option[StencilsEnsemble]

  def addStencilComponents(stencilComponents: StencilsEnsemble): Try[Unit]

  def getAllEnsemble(): List[StencilsEnsemble]
}
