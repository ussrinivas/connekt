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
package com.flipkart.connekt.firefly.flows

import akka.stream.scaladsl.Flow
import com.flipkart.connekt.commons.metrics.Instrumented

import scala.concurrent.Future

private[firefly] abstract class MapFlowStage[In, Out] extends Instrumented {

  protected val stageName: String = this.getClass.getSimpleName

  val map: In => List[Out]

  def flow = Flow[In].mapConcat(map).named(stageName)

}

private[firefly] abstract class MapAsyncFlowStage[In, Out](parallelism: Int) extends Instrumented{

  protected val stageName: String = this.getClass.getSimpleName

  val map: In => Future[List[Out]]

  def flow = Flow[In].mapAsyncUnordered(parallelism)(map).mapConcat(identity).named(stageName)
}
