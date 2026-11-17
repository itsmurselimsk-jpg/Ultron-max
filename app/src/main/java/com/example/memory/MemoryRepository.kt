package com.example.memory

import com.example.data.local.AuditLogEntity
import com.example.data.local.CitationEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.MessageEntity
import com.example.data.local.UltronDao
import com.example.data.remote.FirestoreSyncRepository
import com.example.security.CryptoManager
import com.example.security.RiskLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MemoryRepository(
    private val dao: UltronDao,
    private val cryptoManager: CryptoManager,
    private val semanticSearchEngine: SemanticSearchEngine,
    private val firestoreSync: FirestoreSyncRepository?
) {

    fun observeMemories(userId: String): Flow<List<DecryptedMemory>> =
        dao.observeMemories(userId).map { entities ->
            entities.map { entity -> entity.toDecrypted() }
        }

    fun observeConversations(userId: String): Flow<List<ConversationEntity>> =
        dao.observeConversations(userId)

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(conversationId)

    fun observeCitations(userId: String): Flow<List<CitationEntity>> =
        dao.observeCitations(userId)

    fun observeAuditLogs(userId: String): Flow<List<AuditLogEntity>> =
        dao.observeAuditLogs(userId)

    suspend fun searchRelevantMemories(
        userId: String,
        query: String,
        topK: Int = 5
    ): List<DecryptedMemory> {
        val all = dao.getMemoriesOnce(userId).map { it.toDecrypted() }
        return semanticSearchEngine.search(query, all, topK)
    }

    suspend fun saveMemory(
        userId: String,
        category: String,
        title: String,
        content: String,
        importance: Int = 6,
        pinned: Boolean = false,
        syncToCloud: Boolean = true,
        existingId: String? = null
    ): Result<DecryptedMemory> = try {
        val now = System.currentTimeMillis()
        val id = existingId?.takeIf { it.isNotBlank() } ?: "mem_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val safeTitle = title.trim().take(160).ifBlank { "Untitled Memory" }
        val safeCategory = if (category in setOf("FACT", "PREFERENCE", "EPISODIC", "INSTRUCTION")) category else "FACT"
        val encrypted = cryptoManager.encrypt(content.trim())
        val keywords = semanticSearchEngine.extractKeywords(safeTitle, content)

        var synced = false
        if (syncToCloud && firestoreSync != null) {
            val cloudResult = firestoreSync.upsertMemory(
                memoryId = id,
                category = safeCategory,
                title = safeTitle,
                encryptedContent = encrypted,
                searchKeywords = keywords,
                importance = importance.coerceIn(1, 10),
                pinned = pinned,
                isUpdate = existingId != null
            )
            synced = cloudResult.isSuccess
        }

        val entity = MemoryEntity(
            id = id,
            userId = userId,
            category = safeCategory,
            title = safeTitle,
            encryptedContent = encrypted,
            searchKeywords = keywords,
            importance = importance.coerceIn(1, 10),
            pinned = pinned,
            syncedToCloud = synced,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertMemory(entity)
        Result.success(entity.toDecrypted())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteMemory(
        userId: String,
        memoryId: String,
        syncToCloud: Boolean = true
    ): Result<Unit> = try {
        dao.deleteMemory(memoryId, userId)
        if (syncToCloud && firestoreSync != null) {
            firestoreSync.deleteMemory(memoryId)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun syncFromCloud(userId: String, cloudDocs: List<com.example.data.remote.MemoryCloudDoc>) {
        if (cloudDocs.isEmpty()) return
        val localExisting = dao.getMemoriesOnce(userId).associateBy { it.id }
        val merged = cloudDocs.map { doc ->
            val local = localExisting[doc.id]
            val cloudUpdated = doc.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
            val cloudCreated = doc.createdAt?.toDate()?.time ?: cloudUpdated
            if (local != null && local.updatedAt > cloudUpdated) {
                local
            } else {
                MemoryEntity(
                    id = doc.id,
                    userId = userId,
                    category = doc.category,
                    title = doc.title,
                    encryptedContent = doc.encryptedContent,
                    searchKeywords = doc.searchKeywords,
                    importance = doc.importance.toInt().coerceIn(1, 10),
                    pinned = doc.pinned,
                    syncedToCloud = true,
                    createdAt = cloudCreated,
                    updatedAt = cloudUpdated
                )
            }
        }
        dao.upsertMemories(merged)
    }

    suspend fun ensureConversation(
        userId: String,
        conversationId: String,
        title: String,
        modelTier: String,
        languageCode: String,
        preview: String,
        syncToCloud: Boolean
    ) {
        val now = System.currentTimeMillis()
        dao.upsertConversation(
            ConversationEntity(
                id = conversationId,
                userId = userId,
                title = title.take(160),
                modelTier = modelTier,
                languageCode = languageCode,
                lastMessagePreview = preview.take(300),
                createdAt = now,
                updatedAt = now
            )
        )
        if (syncToCloud && firestoreSync != null) {
            firestoreSync.upsertConversation(
                conversationId = conversationId,
                title = title,
                modelTier = modelTier,
                languageCode = languageCode,
                lastMessagePreview = preview
            )
        }
    }

    suspend fun appendMessage(
        userId: String,
        conversationId: String,
        role: String,
        content: String,
        modelUsed: String = "",
        citationsJson: String = "[]",
        verified: Boolean = true
    ): MessageEntity {
        val msg = MessageEntity(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            conversationId = conversationId,
            userId = userId,
            role = role,
            content = content,
            modelUsed = modelUsed,
            citationsJson = citationsJson,
            verified = verified,
            createdAt = System.currentTimeMillis()
        )
        dao.insertMessage(msg)
        return msg
    }

    suspend fun saveCitation(
        userId: String,
        query: String,
        title: String,
        url: String,
        snippet: String,
        summary: String,
        syncToCloud: Boolean
    ): CitationEntity {
        val id = "cit_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val entity = CitationEntity(
            id = id,
            userId = userId,
            query = query.take(400),
            title = title.take(240),
            url = url.take(1000),
            snippet = snippet.take(2000),
            summary = summary.take(4000),
            createdAt = System.currentTimeMillis()
        )
        dao.upsertCitation(entity)
        if (syncToCloud && firestoreSync != null) {
            firestoreSync.saveCitation(id, entity.query, entity.title, entity.url, entity.snippet, entity.summary)
        }
        return entity
    }

    suspend fun recordAudit(
        userId: String,
        actor: String,
        action: String,
        riskLevel: RiskLevel,
        outcome: String,
        details: String,
        syncToCloud: Boolean
    ) {
        val prevHash = dao.getLatestAuditLog(userId)?.integrityHash ?: "GENESIS_OMEGA"
        val now = System.currentTimeMillis()
        val id = "aud_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val hashInput = "$prevHash|$userId|$actor|$action|${riskLevel.name}|$outcome|$details|$now"
        val hash = cryptoManager.sha256Hex(hashInput)
        val entry = AuditLogEntity(
            id = id,
            userId = userId,
            actor = actor,
            action = action,
            riskLevel = riskLevel.name,
            outcome = outcome,
            details = details.take(1000),
            integrityHash = hash,
            createdAt = now
        )
        dao.insertAuditLog(entry)
        if (syncToCloud && firestoreSync != null) {
            firestoreSync.appendAuditLog(id, actor, action, riskLevel.name, outcome, details, hash)
        }
    }

    suspend fun exportEncryptedBackupJson(userId: String): String {
        val memories = dao.getMemoriesOnce(userId).map { it.toDecrypted() }
        val plainArray = JSONArray()
        memories.forEach { mem ->
            plainArray.put(
                JSONObject().apply {
                    put("id", mem.id)
                    put("category", mem.category)
                    put("title", mem.title)
                    put("content", mem.content)
                    put("importance", mem.importance)
                    put("pinned", mem.pinned)
                    put("createdAt", mem.createdAt)
                }
            )
        }
        val wrapped = JSONObject().apply {
            put("format", "ULTRON_OMEGA_ENCRYPTED_BACKUP_V1")
            put("exportedAt", System.currentTimeMillis())
            put("count", memories.size)
            put("ciphertext", cryptoManager.encrypt(plainArray.toString()))
        }
        return wrapped.toString(2)
    }

    suspend fun importEncryptedBackupJson(
        userId: String,
        backupJson: String,
        syncToCloud: Boolean
    ): Result<Int> = try {
        val root = JSONObject(backupJson)
        val ciphertext = root.getString("ciphertext")
        val decryptedArray = JSONArray(cryptoManager.decrypt(ciphertext))
        var imported = 0
        for (i in 0 until decryptedArray.length()) {
            val item = decryptedArray.getJSONObject(i)
            saveMemory(
                userId = userId,
                category = item.optString("category", "FACT"),
                title = item.optString("title", "Restored Memory"),
                content = item.optString("content", ""),
                importance = item.optInt("importance", 5),
                pinned = item.optBoolean("pinned", false),
                syncToCloud = syncToCloud,
                existingId = item.optString("id").takeIf { it.isNotBlank() }
            )
            imported++
        }
        Result.success(imported)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun MemoryEntity.toDecrypted(): DecryptedMemory {
        val plain = runCatching { cryptoManager.decrypt(encryptedContent) }
            .getOrElse { "[Encrypted with another device key]" }
        return DecryptedMemory(
            id = id,
            userId = userId,
            category = category,
            title = title,
            content = plain,
            searchKeywords = searchKeywords,
            importance = importance,
            pinned = pinned,
            syncedToCloud = syncedToCloud,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
