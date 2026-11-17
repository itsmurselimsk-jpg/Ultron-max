package com.example.data.remote

import com.example.security.ToolInputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class FetchedUrlDocument(
    val url: String,
    val title: String,
    val extractedText: String
)

class UrlContentFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchReadableText(rawUrl: String): Result<FetchedUrlDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = ToolInputValidator.validatePublicHttpUrl(rawUrl).getOrThrow()

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "UltronMax-Research/2.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                val finalHost = response.request.url.host
                if (ToolInputValidator.isPrivateOrLocalHost(finalHost)) {
                    error("Blocked redirect to private or loopback host '$finalHost'.")
                }
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${response.message}")
                }
                val bodyString = response.body?.string().orEmpty()
                val titleMatch = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(bodyString)
                val title = titleMatch?.groupValues?.getOrNull(1)
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    .orEmpty()
                    .ifBlank { normalizedUrl }

                val cleanedText = bodyString
                    .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                    .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&nbsp;", " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(12_000)

                FetchedUrlDocument(
                    url = normalizedUrl,
                    title = title,
                    extractedText = cleanedText
                )
            }
        }
    }
}
