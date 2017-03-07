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
package com.flipkart.connekt.commons.utils

import kafka.consumer.ConsumerTimeoutException

import scala.collection.{AbstractIterator, Iterator}

object CollectionUtils {

  implicit class iteratorUtils[T](iterators: List[Iterator[T]]) {
    def merge = new AbstractIterator[T] {

      private var inUseItrIndex = 0
      private val size = iterators.size

      private def unsafeHasNext(index: Int) = try { iterators(index).hasNext } catch { case _: ConsumerTimeoutException => false }

      override def hasNext: Boolean = {
        if (iterators.nonEmpty) {
          inUseItrIndex = (1 + inUseItrIndex) % size

          unsafeHasNext(inUseItrIndex) || {
            def r(stopIdx: Int): Boolean = {
              inUseItrIndex = (1 + inUseItrIndex) % size

              if (stopIdx != inUseItrIndex) {
                unsafeHasNext(inUseItrIndex) || r(stopIdx)
              } else false
            }

            r(inUseItrIndex)
          }
        } else false
      }

      override def next(): T = {
        if(iterators.nonEmpty)
          iterators(inUseItrIndex).next()
        else
          Iterator.empty.next()
      }
    }
  }

}
