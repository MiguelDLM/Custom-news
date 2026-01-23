package com.example.newsreader.util

import android.util.Xml
import com.example.newsreader.domain.model.Article
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

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
                    } else if (isInsideItem) {
                        when (name.lowercase()) {
                            "title" -> currentTitle = readText(parser)
                            "link" -> currentLink = readText(parser)
                            "description" -> currentDescription = readText(parser)
                            "pubdate" -> currentPubDate = readText(parser)
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
                                    description = currentDescription,
                                    pubDate = currentPubDate
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
}
