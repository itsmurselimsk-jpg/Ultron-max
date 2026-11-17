package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.security.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceState {
    IDLE,
    WAKE_LISTENING,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

sealed interface VoiceRecoveryDecision {
    data class Retry(val delayMs: Long, val message: String) : VoiceRecoveryDecision
    data class Fail(val message: String) : VoiceRecoveryDecision
    data object SilentRearm : VoiceRecoveryDecision
}

class VoiceEngine(
    private val context: Context,
    val vadEngine: AudioVadEngine,
    val ttsEngine: DeepMaleTtsEngine
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingRestartRunnable: Runnable? = null
    private var watchdogRunnable: Runnable? = null

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var activeLanguage: VoiceLanguage = VoiceLanguage.ENGLISH
    private var activeWakeWord: String = "ultron"
    private var requireWakeWordInSession: Boolean = false
    private var wakeWordOnlyMode: Boolean = false
    private var continuousMode: Boolean = false
    private var consecutiveErrorCount: Int = 0
    private var onFinalUtterance: ((String) -> Unit)? = null

    val isSpeechRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun syncTtsSpeakingState(isSpeaking: Boolean) {
        if (isSpeaking) {
            _voiceState.value = VoiceState.SPEAKING
        } else if (_voiceState.value == VoiceState.SPEAKING) {
            if (continuousMode) {
                resumeContinuousAfterTtsIfEnabled()
            } else {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    fun startListening(
        language: VoiceLanguage,
        wakeWord: String,
        requireWakeWord: Boolean,
        continuous: Boolean,
        onTranscriptReady: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _voiceState.value = VoiceState.ERROR
            _lastError.value = "Microphone permission is required for voice control."
            return
        }
        if (!isSpeechRecognitionAvailable) {
            _voiceState.value = VoiceState.ERROR
            _lastError.value = "Android SpeechRecognizer service is unavailable on this device."
            return
        }

        cancelScheduledRunnables()
        activeLanguage = language
        activeWakeWord = wakeWord.trim().lowercase().ifBlank { "ultron" }
        requireWakeWordInSession = requireWakeWord
        wakeWordOnlyMode = requireWakeWord
        continuousMode = continuous
        consecutiveErrorCount = 0
        onFinalUtterance = onTranscriptReady
        _lastError.value = null

        mainHandler.post {
            launchRecognizerInternal()
        }
    }

    private fun cancelScheduledRunnables() {
        pendingRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRestartRunnable = null
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    private fun scheduleRestart(delayMs: Long) {
        cancelScheduledRunnables()
        val runnable = Runnable {
            pendingRestartRunnable = null
            if (continuousMode) {
                launchRecognizerInternal()
            }
        }
        pendingRestartRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun armWatchdog() {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        val wd = Runnable {
            watchdogRunnable = null
            if (_voiceState.value == VoiceState.LISTENING ||
                _voiceState.value == VoiceState.WAKE_LISTENING ||
                _voiceState.value == VoiceState.PROCESSING
            ) {
                runCatching { speechRecognizer?.cancel() }
                if (continuousMode && consecutiveErrorCount < MAX_CONSECUTIVE_ERRORS) {
                    consecutiveErrorCount++
                    scheduleRestart(backoffDelayForAttempt(consecutiveErrorCount))
                } else {
                    _voiceState.value = VoiceState.IDLE
                    _lastError.value = "Voice session timed out waiting for audio."
                }
            }
        }
        watchdogRunnable = wd
        mainHandler.postDelayed(wd, RECOGNITION_WATCHDOG_MS)
    }

    private fun launchRecognizerInternal() {
        cancelScheduledRunnables()
        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        consecutiveErrorCount = 0
                        _voiceState.value = if (wakeWordOnlyMode) VoiceState.WAKE_LISTENING else VoiceState.LISTENING
                        armWatchdog()
                    }

                    override fun onBeginningOfSpeech() {
                        armWatchdog()
                        if (ttsEngine.isSpeaking.value) {
                            ttsEngine.stop()
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _voiceState.value = VoiceState.PROCESSING
                        armWatchdog()
                    }

                    override fun onError(error: Int) {
                        cancelScheduledRunnables()
                        consecutiveErrorCount++
                        when (val decision = classifyRecoveryDecision(error, continuousMode, consecutiveErrorCount)) {
                            is VoiceRecoveryDecision.SilentRearm -> {
                                consecutiveErrorCount = 0
                                _voiceState.value = if (wakeWordOnlyMode) VoiceState.WAKE_LISTENING else VoiceState.LISTENING
                                scheduleRestart(350L)
                            }
                            is VoiceRecoveryDecision.Retry -> {
                                _lastError.value = decision.message
                                _voiceState.value = if (wakeWordOnlyMode) VoiceState.WAKE_LISTENING else VoiceState.LISTENING
                                scheduleRestart(decision.delayMs)
                            }
                            is VoiceRecoveryDecision.Fail -> {
                                continuousMode = false
                                _voiceState.value = if (error == SpeechRecognizer.ERROR_CLIENT) VoiceState.IDLE else VoiceState.ERROR
                                if (error != SpeechRecognizer.ERROR_CLIENT) {
                                    _lastError.value = decision.message
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        cancelScheduledRunnables()
                        consecutiveErrorCount = 0
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val spoken = matches.firstOrNull().orEmpty().trim()
                        _partialTranscript.value = ""
                        if (spoken.isBlank()) {
                            handlePostUtterance(dispatched = false)
                            return
                        }

                        val extracted = extractWakeWordCommand(spoken, activeWakeWord, wakeWordOnlyMode)
                        if (extracted != null) {
                            onFinalUtterance?.invoke(extracted)
                            handlePostUtterance(dispatched = true)
                        } else {
                            handlePostUtterance(dispatched = false)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                            if (wakeWordOnlyMode && partial.lowercase().contains(activeWakeWord) && ttsEngine.isSpeaking.value) {
                                ttsEngine.stop()
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLanguage.bcp47Tag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, activeLanguage.bcp47Tag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        }.onFailure { err ->
            cancelScheduledRunnables()
            _voiceState.value = VoiceState.ERROR
            _lastError.value = err.localizedMessage ?: "Failed to start SpeechRecognizer."
        }
    }

    private fun handlePostUtterance(dispatched: Boolean) {
        if (continuousMode && !dispatched) {
            wakeWordOnlyMode = requireWakeWordInSession
            scheduleRestart(300L)
        } else if (!continuousMode) {
            _voiceState.value = VoiceState.IDLE
        } else {
            wakeWordOnlyMode = requireWakeWordInSession
            _voiceState.value = VoiceState.PROCESSING
        }
    }

    fun resumeContinuousAfterTtsIfEnabled() {
        if (continuousMode) {
            wakeWordOnlyMode = requireWakeWordInSession
            mainHandler.post { launchRecognizerInternal() }
        } else {
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun interruptSpeechAndListen(
        language: VoiceLanguage,
        wakeWord: String,
        onTranscriptReady: (String) -> Unit
    ) {
        ttsEngine.stop()
        startListening(
            language = language,
            wakeWord = wakeWord,
            requireWakeWord = false,
            continuous = continuousMode,
            onTranscriptReady = onTranscriptReady
        )
    }

    fun stopAll() {
        continuousMode = false
        wakeWordOnlyMode = false
        requireWakeWordInSession = false
        consecutiveErrorCount = 0
        cancelScheduledRunnables()
        _partialTranscript.value = ""
        ttsEngine.stop()
        vadEngine.stopMonitoring()
        mainHandler.post {
            cancelScheduledRunnables()
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            _voiceState.value = VoiceState.IDLE
        }
    }

    companion object {
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val RECOGNITION_WATCHDOG_MS = 18_000L

        fun backoffDelayForAttempt(attempt: Int): Long = when (attempt.coerceAtLeast(1)) {
            1 -> 450L
            2 -> 1000L
            else -> 2000L
        }

        fun extractWakeWordCommand(
            spoken: String,
            wakeWord: String,
            requireWakeWord: Boolean
        ): String? {
            val trimmed = spoken.trim()
            if (trimmed.isBlank()) return null
            if (!requireWakeWord) return trimmed
            val cleanWake = wakeWord.trim().lowercase().ifBlank { "ultron" }
            if (!trimmed.lowercase().contains(cleanWake)) return null
            val stripped = trimmed.replace(
                Regex("(?i).*?\\b${Regex.escape(cleanWake)}\\b[,:\\s]*"),
                ""
            ).trim()
            return stripped.ifBlank { "Ultron, ready for orders." }
        }

        fun classifyRecoveryDecision(
            errorCode: Int,
            continuous: Boolean,
            attemptCount: Int
        ): VoiceRecoveryDecision {
            val msg = mapRecognizerError(errorCode)
            if (errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                return VoiceRecoveryDecision.Fail(msg)
            }
            if (continuous && (errorCode == SpeechRecognizer.ERROR_NO_MATCH || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                return VoiceRecoveryDecision.SilentRearm
            }
            val isTransient = errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                errorCode == SpeechRecognizer.ERROR_NETWORK ||
                errorCode == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                errorCode == SpeechRecognizer.ERROR_AUDIO
            if (continuous && isTransient && attemptCount <= MAX_CONSECUTIVE_ERRORS) {
                return VoiceRecoveryDecision.Retry(
                    delayMs = backoffDelayForAttempt(attemptCount),
                    message = "$msg Retrying ($attemptCount/$MAX_CONSECUTIVE_ERRORS)..."
                )
            }
            return VoiceRecoveryDecision.Fail(msg)
        }

        fun mapRecognizerError(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio capture error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network error."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
            else -> "Speech recognition error ($code)."
        }
    }
}
