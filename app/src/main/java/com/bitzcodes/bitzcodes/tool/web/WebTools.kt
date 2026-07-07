package com.bitzcodes.bitzcodes.tool.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class WebTools {

    suspend fun searchWeb(query: String): String = withContext(Dispatchers.IO) {
        try {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            
            val doc = Jsoup.connect(searchUrl)
                .userAgent(userAgent)
                .timeout(10000)
                .get()

            val results = doc.select(".result")
            if (results.isEmpty()) {
                return@withContext "No search results found for query: '$query'."
            }

            val sb = StringBuilder()
            sb.append("Search results for '$query':\n\n")

            // Get up to 5 results
            results.take(5).forEachIndexed { index, element ->
                val titleElem = element.selectFirst(".result__title .result__a")
                val title = titleElem?.text() ?: "No Title"
                val rawUrl = titleElem?.attr("href") ?: ""
                
                // Clean DuckDuckGo redirect URL if present
                val url = if (rawUrl.startsWith("//duckduckgo.com/y.js")) {
                    // Extract actual URL from query parameters if needed, or keep it
                    "https:$rawUrl"
                } else {
                    rawUrl
                }

                val snippet = element.selectFirst(".result__snippet")?.text() ?: "No description available."
                
                sb.append("${index + 1}. **$title**\n")
                sb.append("   URL: $url\n")
                sb.append("   Snippet: $snippet\n\n")
            }

            sb.toString()
        } catch (e: Exception) {
            "Error performing web search: ${e.message}"
        }
    }

    suspend fun readUrl(url: String): String = withContext(Dispatchers.IO) {
        try {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            val doc = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(10000)
                .followRedirects(true)
                .get()

            val title = doc.title()
            
            // Try to extract main content text
            val body = doc.body()
            
            // Remove scripts, styles, navs, footers for cleaner text
            body.select("script, style, nav, footer, iframe, header").remove()
            
            val mainText = body.text()
            
            val truncatedText = if (mainText.length > 8000) {
                mainText.take(8000) + "\n\n[Content truncated due to length limits...]"
            } else {
                mainText
            }

            buildString {
                append("Title: $title\n")
                append("URL: $url\n")
                append("Content:\n\n")
                append(truncatedText)
            }
        } catch (e: Exception) {
            "Error reading url '$url': ${e.message}"
        }
    }
}
