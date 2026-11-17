package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val category: String, // FACT, PREFERENCE, EPISODIC, INSTRUCTION
    val title: String,
    val encryptedContent: String,
    val searchKeywords: String,
    val importance: Int = 5,
    val pinned: Boolean = false,
    val syncedToCloud: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val modelTier: String,
    val languageCode: String,
    val lastMessagePreview: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val userId: String,
    val role: String, // user, model, system, tool
    val content: String,
    val modelUsed: String = "",
    val citationsJson: String = "[]",
    val verified: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_tasks")
data class AgentTaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val goal: String,
    val status: String, // PLANNING, RUNNING, AWAITING_CONFIRMATION, VERIFIED, FAILED, CANCELLED
    val planSummary: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "agent_steps",
    foreignKeys = [
        ForeignKey(
            entity = AgentTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class AgentStepEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val stepOrder: Int,
    val agentRole: String,
    val toolName: String,
    val argumentsJson: String,
    val riskLevel: String,
    val status: String, // PENDING, AWAITING_CONFIRMATION, RUNNING, VERIFIED, FAILED, CANCELLED
    val output: String = "",
    val verificationNotes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "citations")
data class CitationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val query: String,
    val title: String,
    val url: String,
    val snippet: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val actor: String, // USER, AGENT, SYSTEM
    val action: String,
    val riskLevel: String, // LOW, MEDIUM, HIGH, CRITICAL
    val outcome: String, // APPROVED, DENIED, EXECUTED, BLOCKED, STOPPED
    val details: String,
    val integrityHash: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "briefing_history",
    indices = [Index("userId")]
)
data class BriefingHistoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)
