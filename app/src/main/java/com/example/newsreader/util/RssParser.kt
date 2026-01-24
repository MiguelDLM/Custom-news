package com.example.newsreader.util

import android.util.Xml
import com.example.newsreader.domain.model.Article
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class RssParser {

    suspend fun parse(urlString: String): List<Article> {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == 200) {
                connection.inputStream.use { inputStream ->
                    parseFeed(inputStream)
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseFeed(inputStream: InputStream): List<Article> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        val articles = mutableListOf<Article>()
        var currentTitle: String? = null
        var currentLink: String? = null
        var currentDescription: String? = null
        var currentPubDate: String? = null
        var currentImageUrl: String? = null
        var isInsideItem = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name.equals("item", ignoreCase = true)) {
                        isInsideItem = true
                        currentTitle = null
                        currentLink = null
                        currentDescription = null
                        currentPubDate = null
                        currentImageUrl = null
                    } else if (isInsideItem) {
                        when (name.lowercase()) {
                            "title" -> currentTitle = readText(parser)
                            "link" -> currentLink = readText(parser)
                            "description" -> {
                                currentDescription = readText(parser)
                                // Try to extract image from description if not found yet
                                if (currentImageUrl == null) {
                                    currentImageUrl = extractImageFromHtml(currentDescription)
                                }
                            }
                            "pubdate" -> currentPubDate = readText(parser)
                            "media:content", "enclosure" -> {
                                // Try to get url attribute
                                val url = parser.getAttributeValue(null, "url")
                                val type = parser.getAttributeValue(null, "type")
                                if (currentImageUrl == null && url != null && (type?.startsWith("image") == true || isImageExtension(url))) {
                                    currentImageUrl = url
                                }
                            }
                            "media:thumbnail" -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (currentImageUrl == null && url != null) {
                                    currentImageUrl = url
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name.equals("item", ignoreCase = true)) {
                        isInsideItem = false
                        if (currentTitle != null && currentLink != null) {
                            articles.add(
                                Article(
                                    title = currentTitle,
                                    link = currentLink,
                                    description = cleanHtml(currentDescription),
                                    pubDate = currentPubDate,
                                    imageUrl = currentImageUrl
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun extractImageFromHtml(html: String?): String? {
        if (html == null) return null
        val matcher = Pattern.compile("src=\"([^\"]+)\"").matcher(html)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    private fun isImageExtension(url: String): Boolean {
        return url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".jpeg") || url.endsWith(".webp")
    }

    private fun cleanHtml(html: String?): String? {
        // Robust HTML stripping and entity decoding
        if (html == null) return null
        var text = html.replace(Regex("<script.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace(Regex("<style.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace(Regex("<.*?>"), "")
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return text.trim()
    }
}
