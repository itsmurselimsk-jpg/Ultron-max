package com.example.intelligence

import android.graphics.Bitmap
import android.util.Log
import com.example.memory.DecryptedMemory
import com.example.security.VoiceLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class IntelligenceToolCall(
    val name: String,
    val args: Map<String, String>,
    val id: String? = null
)

sealed interface StreamEvent {
    data class Token(val accumulatedText: String, val modelTier: ModelTier) : StreamEvent
    data class ToolRequested(val calls: List<IntelligenceToolCall>) : StreamEvent
    data class Completed(val finalText: String, val modelTier: ModelTier, val verified: Boolean) : StreamEvent
    data class Error(val message: String) : StreamEvent
}

class IntelligenceEngine {

    private val liteModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ModelTier.LITE.modelId,
            systemInstruction = buildSystemInstruction(emptyList(), VoiceLanguage.ENGLISH)
        )
    }

    private fun createConversationalModel(
        tier: ModelTier,
        memories: List<DecryptedMemory>,
        language: VoiceLanguage,
        enableTools: Boolean
    ): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = tier.modelId,
            systemInstruction = buildSystemInstruction(memories, language),
            tools = if (enableTools && tier != ModelTier.LITE) ToolDeclarations.allAndroidAndMemoryTools else null
        )
    }

    private fun buildSystemInstruction(
        memories: List<DecryptedMemory>,
        language: VoiceLanguage
    ): Content = content {
        val memoryContext = if (memories.isEmpty()) {
            "No long-term memories matched this turn."
        } else {
            memories.joinToString("\n") { "- [${it.category}] ${it.title}: ${it.content}" }
        }
        val langHint = when (language) {
            VoiceLanguage.ENGLISH -> "Respond naturally in concise, authoritative English."
            VoiceLanguage.HINDI -> "Respond naturally in Hindi (Devanagari or clear conversational Hindi)."
            VoiceLanguage.HINGLISH -> "Respond naturally in Hinglish (Hindi mixed with English technical terms in Latin script)."
            VoiceLanguage.BANGLA -> "Respond naturally in Bangla (Bengali)."
            VoiceLanguage.BANGLISH -> "Respond naturally in Banglish (Bangla mixed with English in Latin script)."
        }
        text(
            """
            You are ULTRON OMEGA — an advanced, disciplined, secure personal Android AI assistant.
            Persona: Calm, hyper-competent, deep strategic clarity, zero fluff. Never claim capabilities you cannot execute; when calling tools, rely strictly on actual tool results.
            Language Guidance: $langHint
            Relevant Long-Term Encrypted Memories:
            $memoryContext
            """.trimIndent()
        )
    }

    fun streamConversation(
        userPrompt: String,
        history: List<Pair<String, String>>,
        memories: List<DecryptedMemory>,
        language: VoiceLanguage,
        preferredTier: String,
        toolExecutor: suspend (IntelligenceToolCall) -> String
    ): Flow<StreamEvent> = flow {
        val tier = ModelRouter.selectModel(userPrompt, preferredTier, hasMedia = false)
        val model = createConversationalModel(
            tier = tier,
            memories = memories,
            language = language,
            enableTools = true
        )

        val chatHistory = history.takeLast(12).map { (role, text) ->
            content(if (role == "user") "user" else "model") { text(text) }
        }
        val chat = model.startChat(history = chatHistory)

        try {
            val firstResponse = withTimeout(60_000L) {
                if (tier == ModelTier.LITE) {
                    val acc = StringBuilder()
                    model.generateContentStream(
                        content("user") { text(userPrompt) }
                    ).collect { chunk ->
                        val t = chunk.text.orEmpty()
                        if (t.isNotEmpty()) {
                            acc.append(t)
                            emit(StreamEvent.Token(acc.toString(), tier))
                        }
                    }
                    return@withTimeout Pair(acc.toString(), emptyList<FunctionCallPart>())
                } else {
                    val resp = chat.sendMessage(userPrompt)
                    val calls = resp.functionCalls
                    val text = resp.text.orEmpty()
                    if (text.isNotEmpty() && calls.isEmpty()) {
                        emit(StreamEvent.Token(text, tier))
                    }
                    return@withTimeout Pair(text, calls)
                }
            }

            val functionCalls = firstResponse.second
            if (functionCalls.isNotEmpty()) {
                val parsedCalls = functionCalls.map { call ->
                    IntelligenceToolCall(
                        name = call.name,
                        args = call.args.mapValues { (_, v) -> v.toString().trim('"') },
                        id = call.id
                    )
                }
                emit(StreamEvent.ToolRequested(parsedCalls))

                val toolOutputs = mutableListOf<Pair<IntelligenceToolCall, String>>()
                for (call in parsedCalls) {
                    val output = toolExecutor(call)
                    toolOutputs.add(call to output)
                }

                val functionResponseContent = content(role = "function") {
                    toolOutputs.forEach { (call, result) ->
                        part(
                            FunctionResponsePart(
                                name = call.name,
                                response = JsonObject(
                                    mapOf("result" to kotlinx.serialization.json.JsonPrimitive(result))
                                ),
                                id = call.id
                            )
                        )
                    }
                }

                val finalBuilder = StringBuilder()
                withTimeout(60_000L) {
                    chat.sendMessageStream(functionResponseContent).collect { chunk ->
                        val t = chunk.text.orEmpty()
                        if (t.isNotEmpty()) {
                            finalBuilder.append(t)
                            emit(StreamEvent.Token(finalBuilder.toString(), tier))
                        }
                    }
                }
                val finalText = finalBuilder.toString().ifBlank {
                    toolOutputs.joinToString("\n") { "${it.first.name}: ${it.second}" }
                }
                val verified = verifyResponseIntegrity(finalText)
                emit(StreamEvent.Completed(finalText, tier, verified))
            } else {
                val finalText = firstResponse.first
                if (finalText.isBlank()) {
                    emit(StreamEvent.Error("Model returned an empty response."))
                } else {
                    val verified = verifyResponseIntegrity(finalText)
                    emit(StreamEvent.Completed(finalText, tier, verified))
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Intelligence timeout", e)
            emit(StreamEvent.Error("Inference timed out after 60 seconds. Try again or switch to Omega Flash."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Intelligence error", e)
            emit(StreamEvent.Error(e.localizedMessage ?: "Intelligence execution failed."))
        }
    }

    suspend fun generateStructuredJson(
        prompt: String,
        schema: Schema,
        tier: ModelTier = ModelTier.ARGON
    ): Result<String> = retryWithBackoff {
        withTimeout(60_000L) {
            val config = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
            val model = Firebase.ai.generativeModel(
                modelName = tier.modelId,
                generationConfig = config
            )
            val response = model.generateContent(prompt)
            val text = response.text
            if (text.isNullOrBlank()) {
                error("Structured model returned empty JSON.")
            }
            text
        }
    }

    suspend fun analyzeMultimodal(
        prompt: String,
        bitmap: Bitmap?,
        documentText: String? = null
    ): Result<String> = retryWithBackoff {
        withTimeout(60_000L) {
            val model = Firebase.ai.generativeModel(modelName = ModelTier.FLASH.modelId)
            val response = model.generateContent(
                content {
                    text(prompt)
                    if (bitmap != null) {
                        image(bitmap)
                    }
                    if (!documentText.isNullOrBlank()) {
                        text("\n\nDocument Content:\n${documentText.take(24_000)}")
                    }
                }
            )
            response.text?.takeIf { it.isNotBlank() }
                ?: error("Vision/Document model returned an empty response.")
        }
    }

    suspend fun quickClassify(prompt: String): String {
        return try {
            withTimeout(20_000L) {
                liteModel.generateContent(prompt).text.orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    private fun verifyResponseIntegrity(text: String): Boolean {
        return text.isNotBlank() && !text.contains("undefined", ignoreCase = false)
    }

    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 2,
        block: suspend () -> T
    ): Result<T> {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: TimeoutCancellationException) {
                lastError = e
                if (attempt < maxAttempts - 1) delay(600L * (attempt + 1))
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxAttempts - 1) delay(600L * (attempt + 1))
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Operation failed"))
    }

    companion object {
        private const val TAG = "IntelligenceEngine"
    }
}
