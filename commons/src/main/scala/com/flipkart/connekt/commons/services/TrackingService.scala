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

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}
import com.flipkart.concord.transformer.TURLTransformer
import com.flipkart.connekt.commons.core.Wrappers._
import com.flipkart.connekt.commons.entities.Channel.Channel
import com.flipkart.connekt.commons.metrics.Instrumented
import com.flipkart.connekt.commons.utils.CompressionUtils._
import com.flipkart.connekt.commons.utils.StringUtils._
import com.flipkart.metrics.Timed
import net.htmlparser.jericho._

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
    val source: Source = new Source(html)
    val out = new OutputDocument(source)

    val tags = source.getAllTags.asScala
    var pos: Int = 0

    // append tracking info for all a tag hrefs - only a tag is being rewritten right now!
    var hasBodyTagEnd: Boolean = false
    tags.foreach(tag => {
      if (tag.getTagType == StartTagType.NORMAL) {
        if (tag.getName == HTMLElementName.A || tag.getName == HTMLElementName.AREA) {
          val seq = processATag(tag.getElement, trackerOptions, urlTransformer)
          out.replace(tag, seq)
          reEncodeTextSegment(source, out, pos, tag.getBegin)
        }
      }

      if (tag.getTagType == EndTagType.NORMAL && tag.getName == HTMLElementName.BODY) {
        hasBodyTagEnd = true
        val seq = getMailOpenTracker(trackerOptions) + " </body>"
        out.replace(tag, seq)
      }
      pos = tag.getEnd
    })

    reEncodeTextSegment(source, out, pos, source.getEnd)

    if (!hasBodyTagEnd) {
      //wrap inside a body
      "<body>" + out.toString + getMailOpenTracker(trackerOptions) + " </body>"
    } else
      out.toString
  }

  private def reEncodeTextSegment(source: Source, out: OutputDocument, begin: Int, end: Int) {
    if (begin < end) {
      val textSegment = new Segment(source, begin, end)
      val decodedText = CharacterReference.decode(textSegment)
      out.replace(textSegment, CharacterReference.encode(decodedText))
    }
  }

  private def processATag(tag: Element, trackerOptions: TrackerOptions, urlTransformer: TURLTransformer): CharSequence = {
    val attrMap = tag.getStartTag.getAttributes.asScala.toList.map(attr => attr.getKey -> attr.getValue).toMap
    val lName: String = attrMap.getOrElse("lname", "-")

    val sb = new StringBuffer("<" + tag.getName)
    attrMap.foreach { case (name, value) =>
      if (name.equalsIgnoreCase("href") && !attrMap("href").startsWith("mailto")) {
        val originalUrl = attrMap("href")
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
        sb.append(s""" $name = "$trackedUrl"  """)
      }
      else
        sb.append(s""" $name = "$value"  """)
    }
    sb.append(" >")
    sb
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
