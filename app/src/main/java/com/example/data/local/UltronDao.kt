package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UltronDao {

    // Memories
    @Query("SELECT * FROM memories WHERE userId = :userId ORDER BY pinned DESC, updatedAt DESC")
    fun observeMemories(userId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE userId = :userId ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getMemoriesOnce(userId: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemories(memories: List<MemoryEntity>)

    @Query("DELETE FROM memories WHERE id = :id AND userId = :userId")
    suspend fun deleteMemory(id: String, userId: String)

    @Query("SELECT COUNT(*) FROM memories WHERE userId = :userId")
    suspend fun countMemoriesForUser(userId: String): Int

    @Query("SELECT COUNT(*) FROM memories WHERE userId = :userId AND pinned = 1")
    suspend fun countPinnedMemoriesForUser(userId: String): Int

    // Conversations & Messages
    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeConversations(userId: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :conversationId AND userId = :userId")
    suspend fun deleteConversation(conversationId: String, userId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    // Agent Tasks & Steps
    @Query("SELECT * FROM agent_tasks WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeAgentTasks(userId: String): Flow<List<AgentTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentTask(task: AgentTaskEntity)

    @Query("SELECT * FROM agent_steps WHERE taskId = :taskId ORDER BY stepOrder ASC")
    fun observeAgentSteps(taskId: String): Flow<List<AgentStepEntity>>

    @Query("SELECT * FROM agent_steps WHERE taskId IN (SELECT id FROM agent_tasks WHERE userId = :userId) ORDER BY stepOrder ASC")
    fun observeAllStepsForUser(userId: String): Flow<List<AgentStepEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentSteps(steps: List<AgentStepEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentStep(step: AgentStepEntity)

    // Citations
    @Query("SELECT * FROM citations WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeCitations(userId: String): Flow<List<CitationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCitation(citation: CitationEntity)

    @Query("DELETE FROM citations WHERE id = :id AND userId = :userId")
    suspend fun deleteCitation(id: String, userId: String)

    // Security Audit Logs
    @Query("SELECT * FROM audit_logs WHERE userId = :userId ORDER BY createdAt DESC LIMIT 200")
    fun observeAuditLogs(userId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE userId = :userId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestAuditLog(userId: String): AuditLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)
}
