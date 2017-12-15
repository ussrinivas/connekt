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

import java.util.function.Consumer

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}
import com.flipkart.concord.transformer.TURLTransformer
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.CompressionUtils._
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import scala.collection.JavaConverters._

case class URLMessageTracker(@JsonProperty("v") version: Int, @JsonProperty("c") channel: String, @JsonInclude(Include.NON_NULL) @JsonProperty("u") url: String, @JsonProperty("i") messageId: String, @JsonInclude(Include.NON_NULL) @JsonProperty("n") linkName: String, @JsonProperty("d") destination: String, @JsonProperty("ct") clientId: String, @JsonInclude(Include.NON_NULL) @JsonProperty("ctx") contextId: Option[String], @JsonProperty("a") appName: String)

object TrackingService extends Instrumented {

  private val urlRegex = """(?i)\b(https?)://[-A-Za-z0-9+&@#/%?=~_|!:,.;\[\]]*[-A-Za-z0-9+&@#/%=~_|\[\]]""".r
  private val baseURL = "/t/%s/"

  def getAllUrls(message: String): List[String] = {
    urlRegex.findAllIn(message).toList
  }

  sealed case class TrackedURL(domain: String, originalURL: String, action: String, tracker: URLMessageTracker) {
    def toURL: String = {
      Try_("http://" + domain + baseURL.format(action) + tracker.copy(url = originalURL).getJson.compress.get).getOrElse(originalURL)
    }
  }

  sealed case class TrackerOptions(domain: String, channel: Channel, messageId: String, contextId: Option[String], destination: String, clientId: String, appName: String) {
    def toMap: Map[String, AnyRef] = this.asMap.asInstanceOf[Map[String, AnyRef]]
  }

  @Timed("trackText")
  def trackText(txt: String, trackerOptions: TrackerOptions, urlTransformer: TURLTransformer): String = {
    var message = txt
    val finalURLs = (for (url <- getAllUrls(message)) yield {
      val deepLinkedUrl: String = profile(s"${trackerOptions.appName}.${trackerOptions.channel}.deeplink")(urlTransformer.deeplink(url, trackerOptions.toMap).get)
      val trackedURL = TrackedURL(
        domain = trackerOptions.domain,
        originalURL = deepLinkedUrl,
        action = "click",
        tracker = URLMessageTracker(
          version = 1,
          channel = trackerOptions.channel.toString,
          url = deepLinkedUrl,
          linkName = null,
          messageId = trackerOptions.messageId,
          destination = trackerOptions.destination,
          clientId = trackerOptions.clientId,
          contextId = trackerOptions.contextId,
          appName = trackerOptions.appName
        )
      ).toURL
      url -> urlTransformer.shorten(trackedURL).getOrElse(trackedURL)
    }).toMap
    finalURLs.foreach { case (originalURL, shortUrl) =>
      message = message.replaceAllLiterally(originalURL, shortUrl).toString
    }
    message
  }

  @Timed("trackHTML")
  def trackHTML(html: String, trackerOptions: TrackerOptions, urlTransformer: TURLTransformer): String = {

    val out = Jsoup.parse(html)
    val links = out.select("a")

    links.forEach(new Consumer[Element] {
      override def accept(link: Element): Unit = {
        val seq = processATag(link, trackerOptions, urlTransformer)
        link.attr("href", seq)
      }
    })

    out.body().append(getMailOpenTracker(trackerOptions))
    out.toString
  }


  private def processATag(tag: Element, trackerOptions: TrackerOptions, urlTransformer: TURLTransformer): String = {
    val allElements = tag.getAllElements.asScala

    val lName = allElements.find(p => p.hasAttr("lname")).map(_.attr("lname")).getOrElse("-")

    val url = allElements.map { element =>
      if (!element.attr("href").startsWith("mailto")) {
        val originalUrl = element.attr("href")
        val url: String = profile(s"${trackerOptions.appName}.${trackerOptions.channel}.deeplink")(urlTransformer.deeplink(originalUrl, trackerOptions.toMap).get) //.getOrElse(originalUrl)

        val trackedUrl = TrackedURL(
          domain = trackerOptions.domain,
          originalURL = url,
          action = "click",
          tracker = URLMessageTracker(
            version = 1,
            channel = trackerOptions.channel.toString,
            url = url,
            linkName = lName,
            messageId = trackerOptions.messageId,
            destination = trackerOptions.destination,
            clientId = trackerOptions.clientId,
            contextId = trackerOptions.contextId,
            appName = trackerOptions.appName
          )
        ).toURL
        trackedUrl
      }
      else
        element.attr("href")
    }.head
    url
  }

  def getMailOpenTracker(trackerOptions: TrackerOptions): String = {

    val url = TrackedURL(
      domain = trackerOptions.domain,
      originalURL = null,
      action = "open",
      tracker = URLMessageTracker(
        version = 1,
        channel = trackerOptions.channel.toString,
        url = null,
        linkName = null,
        messageId = trackerOptions.messageId,
        destination = trackerOptions.destination,
        clientId = trackerOptions.clientId,
        contextId = trackerOptions.contextId,
        appName = trackerOptions.appName
      )
    ).toURL
    val openTrackingImage = s"""<img style='width:1px;height:1px;' src="$url" /> """
    openTrackingImage
  }

}
