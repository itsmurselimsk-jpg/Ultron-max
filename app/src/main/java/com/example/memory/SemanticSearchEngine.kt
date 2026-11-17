package com.example.memory

import kotlin.math.ln
import kotlin.math.sqrt

data class DecryptedMemory(
    val id: String,
    val userId: String,
    val category: String,
    val title: String,
    val content: String,
    val searchKeywords: String,
    val importance: Int,
    val pinned: Boolean,
    val syncedToCloud: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val relevanceScore: Double = 0.0
)

/**
 * Local TF-IDF + cosine similarity semantic search engine over decrypted memories,
 * combining lexical similarity with importance, recency, and pinned weight.
 */
class SemanticSearchEngine {

    fun search(
        query: String,
        memories: List<DecryptedMemory>,
        topK: Int = 6
    ): List<DecryptedMemory> {
        if (memories.isEmpty()) return emptyList()
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) {
            return memories
                .sortedWith(compareByDescending<DecryptedMemory> { it.pinned }.thenByDescending { it.importance }.thenByDescending { it.updatedAt })
                .take(topK)
        }

        val docTokensList = memories.map { mem ->
            tokenize("${mem.title} ${mem.searchKeywords} ${mem.content} ${mem.category}")
        }
        val numDocs = memories.size.toDouble()
        val dfMap = mutableMapOf<String, Int>()
        docTokensList.forEach { tokens ->
            tokens.toSet().forEach { token ->
                dfMap[token] = (dfMap[token] ?: 0) + 1
            }
        }

        val queryVector = buildTfIdfVector(queryTokens, dfMap, numDocs)
        val now = System.currentTimeMillis()

        return memories.mapIndexed { index, memory ->
            val docVector = buildTfIdfVector(docTokensList[index], dfMap, numDocs)
            val cosine = cosineSimilarity(queryVector, docVector)
            val exactBonus = if (
                memory.title.contains(query, ignoreCase = true) ||
                memory.content.contains(query, ignoreCase = true)
            ) 0.35 else 0.0
            val importanceBoost = (memory.importance.coerceIn(1, 10) / 10.0) * 0.18
            val pinnedBoost = if (memory.pinned) 0.15 else 0.0
            val ageDays = ((now - memory.updatedAt).coerceAtLeast(0L) / 86_400_000.0)
            val recencyBoost = (1.0 / (1.0 + ageDays * 0.1)) * 0.10
            val totalScore = cosine + exactBonus + importanceBoost + pinnedBoost + recencyBoost
            memory.copy(relevanceScore = totalScore)
        }
            .sortedByDescending { it.relevanceScore }
            .take(topK)
    }

    fun extractKeywords(title: String, content: String): String {
        return tokenize("$title $content")
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(16)
            .joinToString(" ") { it.key }
            .take(480)
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\u0900-\\u097f\\u0980-\\u09ff\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in STOP_WORDS }
    }

    private fun buildTfIdfVector(
        tokens: List<String>,
        dfMap: Map<String, Int>,
        numDocs: Double
    ): Map<String, Double> {
        if (tokens.isEmpty()) return emptyMap()
        val tfCounts = tokens.groupingBy { it }.eachCount()
        val maxTf = tfCounts.values.maxOrNull()?.toDouble() ?: 1.0
        return tfCounts.mapValues { (term, count) ->
            val tf = 0.5 + 0.5 * (count / maxTf)
            val df = dfMap[term] ?: 1
            val idf = ln((numDocs + 1.0) / (df + 0.5)) + 1.0
            tf * idf
        }
    }

    private fun cosineSimilarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for ((k, v) in a) {
            normA += v * v
            val bv = b[k]
            if (bv != null) dot += v * bv
        }
        for (v in b.values) normB += v * v
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0.0 else dot / denom
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "for", "that", "this", "with", "from", "are", "was",
            "were", "have", "has", "had", "not", "but", "what", "when", "where",
            "which", "who", "will", "would", "can", "could", "should", "into", "about"
        )
    }
}
