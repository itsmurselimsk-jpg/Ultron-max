package com.example.research

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.example.data.remote.UrlContentFetcher
import com.example.intelligence.IntelligenceEngine
import com.example.intelligence.ModelTier
import com.example.memory.MemoryRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class ResearchSource(
    val title: String,
    val url: String
)

data class ResearchReport(
    val query: String,
    val summary: String,
    val searchQueriesUsed: List<String>,
    val sources: List<ResearchSource>
)

class ResearchEngine(
    private val context: Context,
    private val urlFetcher: UrlContentFetcher,
    private val intelligenceEngine: IntelligenceEngine,
    private val memoryRepository: MemoryRepository
) {
    private val searchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ModelTier.FLASH.modelId,
            tools = listOf(Tool.googleSearch())
        )
    }

    suspend fun searchAndSummarize(
        userId: String,
        query: String,
        syncToCloud: Boolean
    ): Result<ResearchReport> {
        return try {
            val response = withTimeout(60_000L) {
                searchModel.generateContent(
                    "Research thoroughly using Google Search grounding and provide a concise, cited executive summary for:\n$query"
                )
            }
            val summaryText = response.text?.takeIf { it.isNotBlank() }
                ?: error("Research model returned an empty summary.")

            val candidate = response.candidates.firstOrNull()
            val grounding = candidate?.groundingMetadata
            val webQueries = grounding?.webSearchQueries.orEmpty()
            val sources = grounding?.groundingChunks.orEmpty()
                .mapNotNull { chunk ->
                    val web = chunk.web
                    val uri = web?.uri
                    val title = web?.title
                    if (!uri.isNullOrBlank()) {
                        ResearchSource(
                            title = title?.takeIf { it.isNotBlank() } ?: uri,
                            url = uri
                        )
                    } else {
                        null
                    }
                }
                .distinctBy { it.url }

            sources.take(6).forEach { source ->
                memoryRepository.saveCitation(
                    userId = userId,
                    query = query,
                    title = source.title,
                    url = source.url,
                    snippet = webQueries.joinToString(", ").ifBlank { "Google Search Grounded Source" },
                    summary = summaryText.take(1500),
                    syncToCloud = syncToCloud
                )
            }

            Result.success(
                ResearchReport(
                    query = query,
                    summary = summaryText,
                    searchQueriesUsed = webQueries,
                    sources = sources
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Research timed out", e)
            Result.failure(Exception("Web research timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Research failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeUrl(
        userId: String,
        rawUrl: String,
        question: String,
        syncToCloud: Boolean
    ): Result<ResearchReport> {
        val fetched = urlFetcher.fetchReadableText(rawUrl).getOrElse { return Result.failure(it) }
        val prompt = buildString {
            append("Analyze this extracted webpage content from ${fetched.url} (${fetched.title}).\n")
            if (question.isNotBlank()) {
                append("Specific focus: $question\n")
            }
            append("Provide key insights, technical details, and verifiable takeaways.")
        }
        val analysis = intelligenceEngine.analyzeMultimodal(
            prompt = prompt,
            bitmap = null,
            documentText = fetched.extractedText
        ).getOrElse { return Result.failure(it) }

        memoryRepository.saveCitation(
            userId = userId,
            query = question.ifBlank { "URL Analysis" },
            title = fetched.title,
            url = fetched.url,
            snippet = fetched.extractedText.take(400),
            summary = analysis.take(1500),
            syncToCloud = syncToCloud
        )

        return Result.success(
            ResearchReport(
                query = fetched.url,
                summary = analysis,
                searchQueriesUsed = emptyList(),
                sources = listOf(ResearchSource(title = fetched.title, url = fetched.url))
            )
        )
    }

    fun openInBrowser(url: String) {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        runCatching {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(normalized))
        }
    }

    companion object {
        private const val TAG = "ResearchEngine"
    }
}
