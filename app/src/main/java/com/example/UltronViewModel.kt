package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.PendingConfirmationRequest
import com.example.data.local.AgentStepEntity
import com.example.data.local.AgentTaskEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.CitationEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.remote.GitHubIssueDto
import com.example.data.remote.GitHubRepoDto
import com.example.developer.DevTaskMode
import com.example.intelligence.ModelTier
import com.example.intelligence.StreamEvent
import com.example.memory.DecryptedMemory
import com.example.research.ResearchReport
import com.example.security.PermissionStatusSnapshot
import com.example.security.RiskLevel
import com.example.security.RiskPolicyLevel
import com.example.security.UserPreferencesSnapshot
import com.example.security.VoiceLanguage
import com.example.vision.VisionTaskMode
import com.example.voice.VoiceForegroundService
import com.example.voice.VoiceState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Streaming(val text: String, val tier: ModelTier) : AiUiState
    data class Success(val text: String, val tier: ModelTier) : AiUiState
    data class Error(val message: String) : AiUiState
}

enum class CoreVisualMode {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ALERT
}

class UltronViewModel(
    private val appContext: Context,
    private val container: UltronContainer,
    val currentUserId: String
) : ViewModel() {

    val preferences: StateFlow<UserPreferencesSnapshot> = container.credentialStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesSnapshot())

    val memories: StateFlow<List<DecryptedMemory>> = container.memoryRepository.observeMemories(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = container.memoryRepository.observeConversations(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeConversationId = MutableStateFlow("conv_primary_$currentUserId")
    val activeConversationId: StateFlow<String> = _activeConversationId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { convId -> container.memoryRepository.observeMessages(convId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val citations: StateFlow<List<CitationEntity>> = container.memoryRepository.observeCitations(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = container.memoryRepository.observeAuditLogs(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentTasks: StateFlow<List<AgentTaskEntity>> = container.agentEngine.observeTasks(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentSteps: StateFlow<List<AgentStepEntity>> = container.agentEngine.observeAllSteps(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingConfirmation: StateFlow<PendingConfirmationRequest?> = container.agentEngine.pendingConfirmation
    val activeAgentTaskId: StateFlow<String?> = container.agentEngine.activeTaskId
    val emergencyStopEngaged: StateFlow<Boolean> = container.riskEngine.emergencyStopEngaged
    val telemetry = container.androidToolExecutor.telemetry
    val windowBrightnessRequest = container.androidToolExecutor.windowBrightnessRequest

    val voiceState: StateFlow<VoiceState> = container.voiceEngine.voiceState
    val partialTranscript: StateFlow<String> = container.voiceEngine.partialTranscript
    val voiceError: StateFlow<String?> = container.voiceEngine.lastError
    val waveformBars: StateFlow<List<Float>> = container.vadEngine.waveformBars
    val isTtsSpeaking: StateFlow<Boolean> = container.ttsEngine.isSpeaking
    val activeTtsVoiceName: StateFlow<String> = container.ttsEngine.activeVoiceName

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow("Cloud Sync Ready")
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    private val _permissionStatus = MutableStateFlow(container.permissionManager.getStatusSnapshot())
    val permissionStatus: StateFlow<PermissionStatusSnapshot> = _permissionStatus.asStateFlow()

    private val _chatToolConfirmation = MutableStateFlow<PendingConfirmationRequest?>(null)
    val chatToolConfirmation: StateFlow<PendingConfirmationRequest?> = _chatToolConfirmation.asStateFlow()

    // Vision state
    private val _visionBitmap = MutableStateFlow<Bitmap?>(null)
    val visionBitmap: StateFlow<Bitmap?> = _visionBitmap.asStateFlow()

    private val _visionDocName = MutableStateFlow<String?>(null)
    val visionDocName: StateFlow<String?> = _visionDocName.asStateFlow()

    private val _visionDocText = MutableStateFlow<String?>(null)
    val visionDocText: StateFlow<String?> = _visionDocText.asStateFlow()

    private val _visionResult = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val visionResult: StateFlow<AiUiState> = _visionResult.asStateFlow()

    // Research & Dev state
    private val _researchResult = MutableStateFlow<ResearchReport?>(null)
    val researchResult: StateFlow<ResearchReport?> = _researchResult.asStateFlow()

    private val _researchState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val researchState: StateFlow<AiUiState> = _researchState.asStateFlow()

    private val _devState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val devState: StateFlow<AiUiState> = _devState.asStateFlow()

    private val _githubRepos = MutableStateFlow<List<GitHubRepoDto>>(emptyList())
    val githubRepos: StateFlow<List<GitHubRepoDto>> = _githubRepos.asStateFlow()

    private val _githubIssues = MutableStateFlow<List<GitHubIssueDto>>(emptyList())
    val githubIssues: StateFlow<List<GitHubIssueDto>> = _githubIssues.asStateFlow()

    private val _githubFilePreview = MutableStateFlow<String?>(null)
    val githubFilePreview: StateFlow<String?> = _githubFilePreview.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _semanticMemoryResults = MutableStateFlow<List<DecryptedMemory>?>(null)
    val semanticMemoryResults: StateFlow<List<DecryptedMemory>?> = _semanticMemoryResults.asStateFlow()

    private var activeChatJob: Job? = null
    private var activeAgentJob: Job? = null

    init {
        refreshSystemState()
        startCloudMemorySync()
    }

    private fun startCloudMemorySync() {
        viewModelScope.launch {
            container.firestoreSync.observeCloudMemories(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Cloud memory sync stream error", root)
                    _cloudSyncStatus.value = "Sync error: ${root.message ?: "Offline"}"
                }
                .collectLatest { cloudDocs ->
                    if (preferences.value.cloudSyncEnabled) {
                        container.memoryRepository.syncFromCloud(currentUserId, cloudDocs)
                        _cloudSyncStatus.value = "Synced (${cloudDocs.size} cloud memories)"
                    }
                }
        }
    }

    fun refreshSystemState() {
        container.androidToolExecutor.refreshTelemetry()
        _permissionStatus.value = container.permissionManager.getStatusSnapshot()
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun computeCoreVisualMode(): CoreVisualMode = when {
        emergencyStopEngaged.value -> CoreVisualMode.ALERT
        isTtsSpeaking.value -> CoreVisualMode.SPEAKING
        voiceState.value == VoiceState.LISTENING || voiceState.value == VoiceState.WAKE_LISTENING -> CoreVisualMode.LISTENING
        _aiState.value is AiUiState.Loading || _aiState.value is AiUiState.Streaming || activeAgentTaskId.value != null -> CoreVisualMode.THINKING
        else -> CoreVisualMode.IDLE
    }

    // --- CONVERSATION & INTELLIGENCE ---
    fun sendPrompt(rawPrompt: String, speakReply: Boolean = preferences.value.ttsEnabled) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return
        if (emergencyStopEngaged.value) {
            _aiState.value = AiUiState.Error("Emergency Stop is engaged. Reset it in Security Command first.")
            return
        }

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            val prefs = preferences.value
            val convId = _activeConversationId.value
            val historySnapshot = activeMessages.value.map { it.role to it.content }

            container.memoryRepository.ensureConversation(
                userId = currentUserId,
                conversationId = convId,
                title = prompt.take(48),
                modelTier = prefs.preferredModelTier,
                languageCode = prefs.voiceLanguage.bcp47Tag,
                preview = prompt,
                syncToCloud = prefs.cloudSyncEnabled
            )
            container.memoryRepository.appendMessage(
                userId = currentUserId,
                conversationId = convId,
                role = "user",
                content = prompt
            )

            _aiState.value = AiUiState.Loading
            val relevantMemories = container.memoryRepository.searchRelevantMemories(currentUserId, prompt, topK = 5)

            container.intelligenceEngine.streamConversation(
                userPrompt = prompt,
                history = historySnapshot,
                memories = relevantMemories,
                language = prefs.voiceLanguage,
                preferredTier = prefs.preferredModelTier,
                toolExecutor = { call ->
                    val eval = container.riskEngine.evaluateTool(
                        call.name,
                        call.args.values.joinToString(", "),
                        prefs.riskPolicy,
                        prefs.confirmWithBiometrics
                    )
                    if (eval.requiresUserConfirmation) {
                        val deferred = CompletableDeferred<Boolean>()
                        _chatToolConfirmation.value = PendingConfirmationRequest(
                            taskId = convId,
                            stepId = "chat_${UUID.randomUUID().toString().take(8)}",
                            evaluation = eval,
                            description = "Ultron Intelligence requests tool ${call.name}(${call.args})",
                            deferred = deferred
                        )
                        val approved = deferred.await()
                        if (!approved) {
                            container.memoryRepository.recordAudit(
                                currentUserId, "USER", call.name, eval.riskLevel, "DENIED",
                                "Denied tool call in chat", prefs.cloudSyncEnabled
                            )
                            return@streamConversation "Action denied by operator."
                        }
                    }
                    val arg1 = call.args["enabled"] ?: call.args["appQuery"] ?: call.args["query"]
                        ?: call.args["percent"] ?: call.args["command"] ?: call.args["section"]
                        ?: call.args["action"] ?: call.args["title"] ?: call.args["stream"] ?: ""
                    val arg2 = call.args["content"] ?: call.args["percent"] ?: ""
                    val result = container.agentEngine.executeRegisteredTool(
                        userId = currentUserId,
                        toolName = call.name,
                        arg1 = arg1,
                        arg2 = arg2,
                        syncToCloud = prefs.cloudSyncEnabled
                    )
                    container.memoryRepository.recordAudit(
                        currentUserId, "AGENT", call.name, eval.riskLevel, "EXECUTED",
                        result.take(300), prefs.cloudSyncEnabled
                    )
                    refreshSystemState()
                    result
                }
            ).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        _aiState.value = AiUiState.Streaming(event.accumulatedText, event.modelTier)
                    }
                    is StreamEvent.ToolRequested -> {
                        _statusBanner.value = "Invoking tools: ${event.calls.joinToString { it.name }}"
                    }
                    is StreamEvent.Completed -> {
                        _aiState.value = AiUiState.Success(event.finalText, event.modelTier)
                        container.memoryRepository.appendMessage(
                            userId = currentUserId,
                            conversationId = convId,
                            role = "model",
                            content = event.finalText,
                            modelUsed = event.modelTier.modelId,
                            verified = event.verified
                        )
                        container.memoryRepository.ensureConversation(
                            userId = currentUserId,
                            conversationId = convId,
                            title = prompt.take(48),
                            modelTier = event.modelTier.modelId,
                            languageCode = prefs.voiceLanguage.bcp47Tag,
                            preview = event.finalText.take(240),
                            syncToCloud = prefs.cloudSyncEnabled
                        )
                        if (speakReply && !emergencyStopEngaged.value) {
                            container.ttsEngine.speak(event.finalText, prefs.voiceLanguage)
                        }
                    }
                    is StreamEvent.Error -> {
                        _aiState.value = AiUiState.Error(event.message)
                    }
                }
            }
        }
    }

    fun startNewConversation() {
        _activeConversationId.value = "conv_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        _aiState.value = AiUiState.Idle
    }

    fun selectConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        _aiState.value = AiUiState.Idle
    }

    fun resolveChatToolConfirmation(approved: Boolean) {
        val req = _chatToolConfirmation.value
        _chatToolConfirmation.value = null
        req?.deferred?.complete(approved)
    }

    // --- VOICE ENGINE ---
    fun toggleVoiceListening(requireWakeWord: Boolean = preferences.value.wakeWordEnabled) {
        if (emergencyStopEngaged.value) {
            _statusBanner.value = "Reset Emergency Stop before enabling Voice Core."
            return
        }
        val current = voiceState.value
        if (current == VoiceState.LISTENING || current == VoiceState.WAKE_LISTENING) {
            stopVoiceSession()
            return
        }

        val prefs = preferences.value
        if (prefs.bargeInEnabled) {
            container.vadEngine.startMonitoring(viewModelScope) {
                if (container.ttsEngine.isSpeaking.value) {
                    container.ttsEngine.stop()
                }
            }
        }
        if (prefs.continuousVoiceEnabled) {
            runCatching { VoiceForegroundService.start(appContext, prefs.voiceLanguage.displayName) }
        }
        container.voiceEngine.startListening(
            language = prefs.voiceLanguage,
            wakeWord = prefs.wakeWord,
            requireWakeWord = requireWakeWord,
            continuous = prefs.continuousVoiceEnabled
        ) { transcript ->
            sendPrompt(transcript, speakReply = prefs.ttsEnabled)
        }
    }

    fun bargeInInterrupt() {
        val prefs = preferences.value
        container.voiceEngine.interruptSpeechAndListen(
            language = prefs.voiceLanguage,
            wakeWord = prefs.wakeWord
        ) { transcript ->
            sendPrompt(transcript, speakReply = prefs.ttsEnabled)
        }
    }

    fun stopVoiceSession() {
        container.voiceEngine.stopAll()
        VoiceForegroundService.stop(appContext)
    }

    fun updateVoiceLanguage(language: VoiceLanguage) {
        viewModelScope.launch {
            val prefs = preferences.value
            container.credentialStore.updateVoiceSettings(
                continuous = prefs.continuousVoiceEnabled,
                bargeIn = prefs.bargeInEnabled,
                ttsEnabled = prefs.ttsEnabled,
                language = language
            )
            container.ttsEngine.configureVoiceForLanguage(language)
        }
    }

    fun updateVoiceToggles(continuous: Boolean, bargeIn: Boolean, ttsEnabled: Boolean) {
        viewModelScope.launch {
            container.credentialStore.updateVoiceSettings(
                continuous = continuous,
                bargeIn = bargeIn,
                ttsEnabled = ttsEnabled,
                language = preferences.value.voiceLanguage
            )
            if (!ttsEnabled) container.ttsEngine.stop()
        }
    }

    fun updateWakeWord(phrase: String, enabled: Boolean) {
        viewModelScope.launch {
            container.credentialStore.updateWakeWord(phrase, enabled)
        }
    }

    // --- AUTONOMOUS AGENT ---
    fun launchAgentGoal(goal: String) {
        if (goal.isBlank()) return
        activeAgentJob?.cancel()
        activeAgentJob = viewModelScope.launch {
            val prefs = preferences.value
            val result = container.agentEngine.executeAutonomousGoal(
                userId = currentUserId,
                goal = goal.trim(),
                riskPolicy = prefs.riskPolicy,
                biometricConfirm = prefs.confirmWithBiometrics,
                syncToCloud = prefs.cloudSyncEnabled
            )
            result.onSuccess { trace ->
                _statusBanner.value = "Agent verified completion."
                if (prefs.ttsEnabled && !emergencyStopEngaged.value) {
                    container.ttsEngine.speak("Autonomous mission verified. ${trace.take(240)}", prefs.voiceLanguage)
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Agent halted."
            }
            refreshSystemState()
        }
    }

    fun resolveAgentConfirmation(approved: Boolean) {
        container.agentEngine.resolveConfirmation(approved)
    }

    fun cancelActiveAgentTask() {
        viewModelScope.launch {
            activeAgentJob?.cancel()
            container.agentEngine.cancelActiveTask(currentUserId, preferences.value.cloudSyncEnabled)
        }
    }

    // --- MEMORY ENGINE ---
    fun saveMemory(
        category: String,
        title: String,
        content: String,
        importance: Int,
        pinned: Boolean,
        existingId: String? = null
    ) {
        viewModelScope.launch {
            val res = container.memoryRepository.saveMemory(
                userId = currentUserId,
                category = category,
                title = title,
                content = content,
                importance = importance,
                pinned = pinned,
                syncToCloud = preferences.value.cloudSyncEnabled,
                existingId = existingId
            )
            res.onSuccess {
                _statusBanner.value = "Encrypted memory '${it.title}' saved."
                container.memoryRepository.recordAudit(
                    currentUserId, "USER", "MEMORY_SAVE", RiskLevel.MEDIUM, "EXECUTED",
                    "Saved ${it.category} memory: ${it.title}", preferences.value.cloudSyncEnabled
                )
            }.onFailure {
                _statusBanner.value = "Memory save error: ${it.localizedMessage}"
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            container.memoryRepository.deleteMemory(currentUserId, memoryId, preferences.value.cloudSyncEnabled)
            container.memoryRepository.recordAudit(
                currentUserId, "USER", "MEMORY_DELETE", RiskLevel.HIGH, "EXECUTED",
                "Deleted memory $memoryId", preferences.value.cloudSyncEnabled
            )
        }
    }

    fun runSemanticSearch(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _semanticMemoryResults.value = null
            } else {
                _semanticMemoryResults.value = container.memoryRepository.searchRelevantMemories(currentUserId, query, topK = 10)
            }
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = container.memoryRepository.exportEncryptedBackupJson(currentUserId)
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open destination file.")
            }.onSuccess {
                _statusBanner.value = "Encrypted Vault backup exported."
                container.memoryRepository.recordAudit(
                    currentUserId, "USER", "VAULT_BACKUP_EXPORT", RiskLevel.MEDIUM, "EXECUTED",
                    "Exported AES-GCM backup archive", preferences.value.cloudSyncEnabled
                )
            }.onFailure {
                _statusBanner.value = "Backup failed: ${it.localizedMessage}"
            }
        }
    }

    fun restoreBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = appContext.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: error("Cannot read backup file.")
                container.memoryRepository.importEncryptedBackupJson(
                    currentUserId,
                    json,
                    preferences.value.cloudSyncEnabled
                ).getOrThrow()
            }.onSuccess { count ->
                _statusBanner.value = "Restored $count encrypted memories."
            }.onFailure {
                _statusBanner.value = "Restore failed: ${it.localizedMessage}"
            }
        }
    }

    // --- VISION STUDIO ---
    fun setCapturedBitmap(bitmap: Bitmap) {
        _visionBitmap.value = bitmap
        _visionDocName.value = null
        _visionDocText.value = null
    }

    fun loadImageFromPicker(uri: Uri) {
        viewModelScope.launch {
            container.visionEngine.loadBitmapFromUri(uri)
                .onSuccess { bmp ->
                    _visionBitmap.value = bmp
                    _visionDocName.value = null
                    _visionDocText.value = null
                }
                .onFailure { err ->
                    _visionResult.value = AiUiState.Error(err.localizedMessage ?: "Failed to decode image.")
                }
        }
    }

    fun loadDocumentFromSaf(uri: Uri) {
        viewModelScope.launch {
            container.visionEngine.loadDocumentFromUri(uri)
                .onSuccess { doc ->
                    _visionDocName.value = doc.fileName
                    _visionDocText.value = doc.extractedText
                    _visionBitmap.value = doc.renderedPageBitmap
                }
                .onFailure { err ->
                    _visionResult.value = AiUiState.Error(err.localizedMessage ?: "Failed to read document.")
                }
        }
    }

    fun analyzeVision(mode: VisionTaskMode, question: String) {
        viewModelScope.launch {
            _visionResult.value = AiUiState.Loading
            val res = container.visionEngine.analyze(
                mode = mode,
                customQuestion = question,
                bitmap = _visionBitmap.value,
                documentText = _visionDocText.value
            )
            res.onSuccess { text ->
                _visionResult.value = AiUiState.Success(text, ModelTier.FLASH)
            }.onFailure { err ->
                _visionResult.value = AiUiState.Error(err.localizedMessage ?: "Vision analysis failed.")
            }
        }
    }

    // --- RESEARCH & DEVELOPER ---
    fun runWebResearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _researchState.value = AiUiState.Loading
            container.researchEngine.searchAndSummarize(currentUserId, query.trim(), preferences.value.cloudSyncEnabled)
                .onSuccess { report ->
                    _researchResult.value = report
                    _researchState.value = AiUiState.Success(report.summary, ModelTier.FLASH)
                }
                .onFailure { err ->
                    _researchState.value = AiUiState.Error(err.localizedMessage ?: "Web search failed.")
                }
        }
    }

    fun analyzeUrl(url: String, focus: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _researchState.value = AiUiState.Loading
            container.researchEngine.analyzeUrl(currentUserId, url.trim(), focus.trim(), preferences.value.cloudSyncEnabled)
                .onSuccess { report ->
                    _researchResult.value = report
                    _researchState.value = AiUiState.Success(report.summary, ModelTier.FLASH)
                }
                .onFailure { err ->
                    _researchState.value = AiUiState.Error(err.localizedMessage ?: "URL analysis failed.")
                }
        }
    }

    fun openUrlInBrowser(url: String) {
        container.researchEngine.openInBrowser(url)
    }

    fun deleteCitation(citationId: String) {
        viewModelScope.launch {
            container.dao.deleteCitation(citationId, currentUserId)
        }
    }

    fun runDeveloperTask(mode: DevTaskMode, input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _devState.value = AiUiState.Loading
            container.developerEngine.runDevTask(mode, input)
                .onSuccess { output -> _devState.value = AiUiState.Success(output, ModelTier.ARGON) }
                .onFailure { err -> _devState.value = AiUiState.Error(err.localizedMessage ?: "Developer task failed.") }
        }
    }

    fun loadGitHubRepos() {
        viewModelScope.launch {
            _devState.value = AiUiState.Loading
            container.developerEngine.fetchMyRepos()
                .onSuccess { repos ->
                    _githubRepos.value = repos
                    _devState.value = AiUiState.Success("Loaded ${repos.size} repositories from GitHub.", ModelTier.LITE)
                }
                .onFailure { err ->
                    _devState.value = AiUiState.Error(err.localizedMessage ?: "GitHub API failed.")
                }
        }
    }

    fun inspectGitHubRepo(fullName: String) {
        val parts = fullName.split("/")
        if (parts.size != 2) return
        viewModelScope.launch {
            container.developerEngine.fetchRepoIssues(parts[0], parts[1])
                .onSuccess { _githubIssues.value = it }
                .onFailure { _statusBanner.value = "Issues fetch error: ${it.localizedMessage}" }

            container.developerEngine.fetchRepoFileText(parts[0], parts[1], "README.md")
                .onSuccess { _githubFilePreview.value = it.take(4000) }
        }
    }

    // --- ANDROID SYSTEM TOOLS & SECURITY ---
    fun toggleTorch(enabled: Boolean) {
        val result = container.androidToolExecutor.toggleFlashlight(enabled)
        _statusBanner.value = result
        viewModelScope.launch {
            container.memoryRepository.recordAudit(
                currentUserId, "USER", "TORCH_TOGGLE", RiskLevel.MEDIUM, "EXECUTED",
                result, preferences.value.cloudSyncEnabled
            )
        }
    }

    fun setMediaVolume(percent: Int) {
        val result = container.androidToolExecutor.setVolume("media", percent)
        _statusBanner.value = result
    }

    fun setBrightness(percent: Int) {
        val result = container.androidToolExecutor.setBrightness(percent)
        _statusBanner.value = result
    }

    fun controlMedia(command: String) {
        _statusBanner.value = container.androidToolExecutor.controlMedia(command)
    }

    fun launchApp(query: String) {
        val result = container.androidToolExecutor.launchApp(query)
        _statusBanner.value = result
    }

    fun openSystemSettings(section: String) {
        container.androidToolExecutor.openSystemSettings(section)
    }

    fun saveGitHubPat(token: String) {
        viewModelScope.launch {
            container.credentialStore.saveGitHubPat(token)
            container.memoryRepository.recordAudit(
                currentUserId, "USER", "GITHUB_PAT_UPDATED", RiskLevel.HIGH, "EXECUTED",
                if (token.isBlank()) "Cleared encrypted GitHub PAT" else "Encrypted & stored GitHub PAT in Keystore",
                preferences.value.cloudSyncEnabled
            )
            _statusBanner.value = if (token.isBlank()) "GitHub PAT cleared." else "GitHub PAT encrypted in AndroidKeyStore."
        }
    }

    fun updateSecurityPolicy(
        biometricLock: Boolean,
        confirmWithBiometrics: Boolean,
        riskPolicy: RiskPolicyLevel,
        cloudSyncEnabled: Boolean
    ) {
        viewModelScope.launch {
            container.credentialStore.updateSecuritySettings(
                biometricLock, confirmWithBiometrics, riskPolicy, cloudSyncEnabled
            )
            container.memoryRepository.recordAudit(
                currentUserId, "USER", "SECURITY_POLICY_UPDATE", RiskLevel.HIGH, "EXECUTED",
                "Policy=${riskPolicy.name}, BiometricLock=$biometricLock, CloudSync=$cloudSyncEnabled",
                cloudSyncEnabled
            )
        }
    }

    fun updateModelTier(tier: String) {
        viewModelScope.launch {
            container.credentialStore.updateModelTier(tier)
        }
    }

    fun acceptAccessibilityDisclosure() {
        viewModelScope.launch {
            container.credentialStore.setAccessibilityConsentAccepted(true)
            appContext.startActivity(container.permissionManager.createAccessibilitySettingsIntent())
        }
    }

    fun engageEmergencyStop() {
        container.riskEngine.engageEmergencyStop()
        activeChatJob?.cancel()
        activeAgentJob?.cancel()
        stopVoiceSession()
        _chatToolConfirmation.value?.deferred?.complete(false)
        _chatToolConfirmation.value = null
        container.agentEngine.resolveConfirmation(false)
        _aiState.value = AiUiState.Error("EMERGENCY STOP ENGAGED — All active operations halted.")
        viewModelScope.launch {
            container.memoryRepository.recordAudit(
                currentUserId, "USER", "EMERGENCY_STOP", RiskLevel.CRITICAL, "STOPPED",
                "Operator engaged global Emergency Stop kill switch",
                preferences.value.cloudSyncEnabled
            )
        }
    }

    fun resetEmergencyStop() {
        container.riskEngine.resetEmergencyStop()
        _aiState.value = AiUiState.Idle
        _statusBanner.value = "Emergency Stop cleared. Systems nominal."
    }

    fun openWriteSettings() = appContext.startActivity(container.permissionManager.createWriteSettingsIntent())
    fun openNotificationListenerSettings() = appContext.startActivity(container.permissionManager.createNotificationListenerSettingsIntent())
    fun openAppDetailsSettings() = appContext.startActivity(container.permissionManager.createAppDetailsIntent())

    override fun onCleared() {
        super.onCleared()
        stopVoiceSession()
    }

    companion object {
        private const val TAG = "UltronViewModel"
    }
}
