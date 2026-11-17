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

class VoiceEngine(
    private val context: Context,
    val vadEngine: AudioVadEngine,
    val ttsEngine: DeepMaleTtsEngine
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var activeLanguage: VoiceLanguage = VoiceLanguage.ENGLISH
    private var activeWakeWord: String = "ultron"
    private var wakeWordOnlyMode: Boolean = false
    private var continuousMode: Boolean = false
    private var onFinalUtterance: ((String) -> Unit)? = null

    val isSpeechRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

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

        activeLanguage = language
        activeWakeWord = wakeWord.trim().lowercase().ifBlank { "ultron" }
        wakeWordOnlyMode = requireWakeWord
        continuousMode = continuous
        onFinalUtterance = onTranscriptReady
        _lastError.value = null

        mainHandler.post {
            launchRecognizerInternal()
        }
    }

    private fun launchRecognizerInternal() {
        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = if (wakeWordOnlyMode) VoiceState.WAKE_LISTENING else VoiceState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {
                        if (ttsEngine.isSpeaking.value) {
                            ttsEngine.stop()
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _voiceState.value = VoiceState.PROCESSING
                    }

                    override fun onError(error: Int) {
                        val msg = mapRecognizerError(error)
                        if (continuousMode && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                            _voiceState.value = if (wakeWordOnlyMode) VoiceState.WAKE_LISTENING else VoiceState.LISTENING
                            mainHandler.postDelayed({
                                if (continuousMode) launchRecognizerInternal()
                            }, 350L)
                        } else {
                            _voiceState.value = VoiceState.IDLE
                            if (error != SpeechRecognizer.ERROR_CLIENT) {
                                _lastError.value = msg
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val spoken = matches.firstOrNull().orEmpty().trim()
                        _partialTranscript.value = ""
                        if (spoken.isBlank()) {
                            handlePostUtterance(dispatched = false)
                            return
                        }

                        if (wakeWordOnlyMode) {
                            val lower = spoken.lowercase()
                            if (lower.contains(activeWakeWord)) {
                                val stripped = spoken.replace(
                                    Regex("(?i).*?\\b${Regex.escape(activeWakeWord)}\\b[,:\\s]*"),
                                    ""
                                ).trim()
                                val command = stripped.ifBlank { "Ultron, ready for orders." }
                                wakeWordOnlyMode = false
                                onFinalUtterance?.invoke(command)
                                handlePostUtterance(dispatched = true)
                            } else {
                                handlePostUtterance(dispatched = false)
                            }
                        } else {
                            onFinalUtterance?.invoke(spoken)
                            handlePostUtterance(dispatched = true)
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
            _voiceState.value = VoiceState.ERROR
            _lastError.value = err.localizedMessage ?: "Failed to start SpeechRecognizer."
        }
    }

    private fun handlePostUtterance(dispatched: Boolean) {
        if (continuousMode && !dispatched) {
            mainHandler.postDelayed({
                if (continuousMode) launchRecognizerInternal()
            }, 300L)
        } else if (!continuousMode) {
            _voiceState.value = VoiceState.IDLE
        } else {
            _voiceState.value = VoiceState.PROCESSING
        }
    }

    fun resumeContinuousAfterTtsIfEnabled() {
        if (continuousMode) {
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
        _partialTranscript.value = ""
        ttsEngine.stop()
        vadEngine.stopMonitoring()
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            _voiceState.value = VoiceState.IDLE
        }
    }

    private fun mapRecognizerError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio capture error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network error."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
        else -> "Speech recognition error ($code)."
    }
}
