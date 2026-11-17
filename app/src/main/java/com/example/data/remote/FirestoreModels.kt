package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class MemoryCloudDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "FACT",
    val title: String = "",
    val encryptedContent: String = "",
    val searchKeywords: String = "",
    val importance: Long = 5L,
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ConversationCloudDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val modelTier: String = "gemini-3.8-flash",
    val languageCode: String = "en-US",
    val lastMessagePreview: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CitationCloudDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val query: String = "",
    val title: String = "",
    val url: String = "",
    val snippet: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditSummaryCloudDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actor: String = "SYSTEM",
    val action: String = "",
    val riskLevel: String = "LOW",
    val outcome: String = "EXECUTED",
    val details: String = "",
    val integrityHash: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
