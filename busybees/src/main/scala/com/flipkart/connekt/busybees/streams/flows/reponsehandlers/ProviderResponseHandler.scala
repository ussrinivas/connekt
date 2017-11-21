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
package com.flipkart.connekt.busybees.streams.flows.reponsehandlers

import com.flipkart.connekt.busybees.streams.flows.MapAsyncFlowStage
import com.flipkart.connekt.commons.iomodels._

trait ProviderResponseHandler

abstract class PNProviderResponseErrorHandler[I, O1](parallelism: Int = 128) extends MapAsyncFlowStage[I, Either[O1, PNCallbackEvent]](parallelism) with ProviderResponseHandler

abstract class PNProviderResponseHandler[I](parallelism: Int = 128) extends MapAsyncFlowStage[I, PNCallbackEvent](parallelism) with ProviderResponseHandler

abstract class WAProviderResponseHandler[I](parallelism: Int = 128) extends MapAsyncFlowStage[I, WACallbackEvent](parallelism) with ProviderResponseHandler

abstract class WAMediaProviderResponseHandler[I](parallelism: Int = 128) extends MapAsyncFlowStage[I, ConnektRequest](parallelism) with ProviderResponseHandler

abstract class EmailProviderResponseHandler[I, O1](parallelism: Int = 128) extends MapAsyncFlowStage[I, Either[O1, EmailCallbackEvent]](parallelism) with ProviderResponseHandler

abstract class SmsProviderResponseHandler[I, O1](parallelism: Int = 128) extends MapAsyncFlowStage[I, Either[O1, SmsCallbackEvent]](parallelism) with ProviderResponseHandler
