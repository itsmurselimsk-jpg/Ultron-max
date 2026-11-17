package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun sanitizeDocId(rawId: String): String =
        rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "doc_1" }

    fun observeCloudMemories(userId: String): Flow<List<MemoryCloudDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user memory observation." }
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MemoryCloudDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertMemory(
        memoryId: String,
        category: String,
        title: String,
        encryptedContent: String,
        searchKeywords: String,
        importance: Int,
        pinned: Boolean,
        isUpdate: Boolean
    ): Result<Unit> {
        val docId = sanitizeDocId(memoryId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memories/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("memories").document(docId)
            docPath = docRef.path

            val safeCategory = if (category in setOf("FACT", "PREFERENCE", "EPISODIC", "INSTRUCTION")) {
                category
            } else {
                "FACT"
            }
            val safeTitle = title.trim().take(160).ifBlank { "Memory" }
            val safeEncrypted = encryptedContent.take(20000)
            val safeKeywords = searchKeywords.take(500)
            val safeImportance = importance.coerceIn(1, 10).toLong()

            if (isUpdate) {
                val existingSnap = docRef.get().await()
                if (existingSnap.exists()) {
                    docRef.update(
                        mapOf(
                            "category" to safeCategory,
                            "title" to safeTitle,
                            "encryptedContent" to safeEncrypted,
                            "searchKeywords" to safeKeywords,
                            "importance" to safeImportance,
                            "pinned" to pinned,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return Result.success(Unit)
                }
            }

            val payload = mapOf(
                "userId" to uid,
                "category" to safeCategory,
                "title" to safeTitle,
                "encryptedContent" to safeEncrypted,
                "searchKeywords" to safeKeywords,
                "importance" to safeImportance,
                "pinned" to pinned,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isUpdate) OperationType.UPDATE else OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        val docId = sanitizeDocId(memoryId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memories/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("memories").document(docId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertConversation(
        conversationId: String,
        title: String,
        modelTier: String,
        languageCode: String,
        lastMessagePreview: String
    ): Result<Unit> {
        val docId = sanitizeDocId(conversationId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("conversations").document(docId)
            docPath = docRef.path
            val safeTitle = title.trim().take(160).ifBlank { "Ultron Session" }
            val safeTier = modelTier.take(60).ifBlank { "gemini-3.8-flash" }
            val safeLang = languageCode.take(16).ifBlank { "en-US" }
            val safePreview = lastMessagePreview.take(1000)

            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "title" to safeTitle,
                        "modelTier" to safeTier,
                        "languageCode" to safeLang,
                        "lastMessagePreview" to safePreview,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to safeTitle,
                        "modelTier" to safeTier,
                        "languageCode" to safeLang,
                        "lastMessagePreview" to safePreview,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCitation(
        citationId: String,
        query: String,
        title: String,
        url: String,
        snippet: String,
        summary: String
    ): Result<Unit> {
        val docId = sanitizeDocId(citationId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/citations/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("citations").document(docId)
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "query" to query.trim().take(400).ifBlank { "Research" },
                    "title" to title.trim().take(240).ifBlank { "Source" },
                    "url" to url.trim().take(1000).ifBlank { "https://google.com" },
                    "snippet" to snippet.take(2000),
                    "summary" to summary.take(4000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun appendAuditLog(
        logId: String,
        actor: String,
        action: String,
        riskLevel: String,
        outcome: String,
        details: String,
        integrityHash: String
    ): Result<Unit> {
        val docId = sanitizeDocId(logId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/audit_logs/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("audit_logs").document(docId)
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "actor" to if (actor in setOf("USER", "AGENT", "SYSTEM")) actor else "SYSTEM",
                    "action" to action.take(120).ifBlank { "EVENT" },
                    "riskLevel" to if (riskLevel in setOf("LOW", "MEDIUM", "HIGH", "CRITICAL")) riskLevel else "LOW",
                    "outcome" to if (outcome in setOf("APPROVED", "DENIED", "EXECUTED", "BLOCKED", "STOPPED")) outcome else "EXECUTED",
                    "details" to details.take(1000),
                    "integrityHash" to integrityHash.take(128).ifBlank { "0" },
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }
}
