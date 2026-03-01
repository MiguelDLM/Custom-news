package com.museovirtualnacional.strogoff.util

import android.util.Xml
import com.museovirtualnacional.strogoff.domain.model.Article
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class RssParser {

    suspend fun parse(urlString: String): List<Article> {
        var currentUrl = urlString
        var redirectCount = 0
        var connection: HttpURLConnection? = null
        var responseCode = 0

        while (redirectCount < 5) {
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "NewsReader/1.0 (Mobile)")
            connection.instanceFollowRedirects = false // Disable auto-redirect to handle protocol changes manually
            connection.connect()

            responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                responseCode == HttpURLConnection.HTTP_SEE_OTHER || 
                responseCode == 307 || 
                responseCode == 308) {
                
                val newUrl = connection.getHeaderField("Location")
                if (newUrl == null) break
                currentUrl = newUrl
                redirectCount++
            } else {
                break
            }
        }

        if (connection == null || responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("Unexpected response code: $responseCode")
        }

        val contentType = connection.contentType
        // Basic check for HTML content which usually means it's not a feed
        if (contentType != null && contentType.contains("text/html")) {
             // Peek at content to see if it's really HTML or just misconfigured XML
             // But usually text/html is a webpage.
             // We can read the first few bytes to check for <?xml or <rss or <feed
        }

        connection.inputStream.use { inputStream ->
            return parseFeed(inputStream)
        }
    }

    private fun parseFeed(inputStream: InputStream): List<Article> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        // Ensure this is an RSS/Atom XML by checking the first START_TAG
        var rootChecked = false
        var eventType = parser.eventType
        val articles = mutableListOf<Article>()
        var currentTitle: String? = null
        var currentLink: String? = null
        var currentDescription: String? = null
        var currentPubDate: String? = null
        var currentImageUrl: String? = null
        var isInsideItem = false
        // Treat both RSS (<item>) and Atom (<entry>) as items

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            if (!rootChecked && eventType == XmlPullParser.START_TAG) {
                val root = name?.lowercase()
                if (root != "rss" && root != "feed" && root != "rdf") {
                    // It might be HTML if root is "html"
                    if (root == "html") {
                        throw IllegalArgumentException("URL returned HTML content, not a valid RSS/Atom feed.")
                    }
                    throw IllegalArgumentException("Not an RSS/Atom feed (root element: $root)")
                }
                rootChecked = true
            }
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                        isInsideItem = true
                        currentTitle = null
                        currentLink = null
                        currentDescription = null
                        currentPubDate = null
                        currentImageUrl = null
                    } else if (isInsideItem) {
                        when (name.lowercase()) {
                            "title" -> currentTitle = readText(parser)
                            // link in RSS is text content, in Atom it's often an element with href attribute
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null) {
                                    currentLink = href
                                } else {
                                    val text = readText(parser)
                                    if (text.isNotBlank()) currentLink = text
                                }
                            }
                            // Atom may use summary or content
                            "description", "summary", "content", "content:encoded" -> {
                                val text = readText(parser)
                                if (currentDescription == null || (name == "content:encoded" && text.length > (currentDescription?.length ?: 0))) {
                                    currentDescription = text
                                }
                                if (currentImageUrl == null) {
                                    currentImageUrl = extractImageFromHtml(currentDescription)
                                }
                            }
                            // pubDate in RSS, updated in Atom
                            "pubdate", "updated", "dc:date" -> currentPubDate = readText(parser)
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
                    if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
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
