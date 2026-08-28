package com.chatbridge.kakao.data.news

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class RssItem(val title: String, val link: String, val source: String, val publishedAt: Long)

/** Minimal RSS 2.0 <item> reader — only pulls the four fields the app actually uses. */
object RssParser {

    fun parse(input: InputStream): List<RssItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val items = mutableListOf<RssItem>()
        var title = ""
        var link = ""
        var pubDate = ""
        var source = ""
        var inItem = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = ""; link = ""; pubDate = ""; source = ""
                    }
                    "title" -> if (inItem) title = parser.nextTextSafely()
                    "link" -> if (inItem) link = parser.nextTextSafely()
                    "pubDate" -> if (inItem) pubDate = parser.nextTextSafely()
                    "source" -> if (inItem) source = parser.nextTextSafely()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    if (title.isNotBlank() && link.isNotBlank()) {
                        items += RssItem(
                            title = title,
                            link = link,
                            source = source.ifBlank { extractSourceFromTitle(title) },
                            publishedAt = parseRfc822(pubDate)
                        )
                    }
                    inItem = false
                }
            }
            eventType = parser.next()
        }
        return items
    }

    // Google News titles are formatted "Headline - Publisher"; fall back to that when <source> is absent.
    private fun extractSourceFromTitle(title: String): String {
        val idx = title.lastIndexOf(" - ")
        return if (idx > 0) title.substring(idx + 3) else ""
    }

    private fun parseRfc822(value: String): Long =
        try {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time
                ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

    private fun XmlPullParser.nextTextSafely(): String =
        try { this.nextText().trim() } catch (e: Exception) { "" }
}
