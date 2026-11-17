package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.security.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Configures Android TextToSpeech with a deep natural male voice profile
 * (lower pitch 0.78f, authoritative speech rate 0.94f, male voice selection).
 */
class DeepMaleTtsEngine(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var initialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeVoiceName = MutableStateFlow("Calibrating...")
    val activeVoiceName: StateFlow<String> = _activeVoiceName.asStateFlow()

    init {
        runCatching {
            tts = TextToSpeech(appContext, this)
        }.onFailure {
            Log.w(TAG, "TextToSpeech engine unavailable: ${it.message}")
            _activeVoiceName.value = "TTS Unavailable"
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            initialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })
            configureVoiceForLanguage(VoiceLanguage.ENGLISH)
        } else {
            _activeVoiceName.value = "TTS Init Failed ($status)"
        }
    }

    fun configureVoiceForLanguage(language: VoiceLanguage) {
        val engine = tts ?: return
        if (!initialized) return

        val targetLocale = when (language) {
            VoiceLanguage.ENGLISH -> Locale.US
            VoiceLanguage.HINDI, VoiceLanguage.HINGLISH -> Locale.forLanguageTag("hi-IN")
            VoiceLanguage.BANGLA, VoiceLanguage.BANGLISH -> Locale.forLanguageTag("bn-IN")
        }

        val langResult = engine.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }

        // Deep natural male profile
        engine.setPitch(0.78f)
        engine.setSpeechRate(0.94f)

        val preferredVoice = selectDeepMaleVoice(engine.voices, targetLocale)
        if (preferredVoice != null) {
            engine.voice = preferredVoice
            _activeVoiceName.value = "${preferredVoice.name} (Deep Male)"
        } else {
            _activeVoiceName.value = "${targetLocale.displayLanguage} Deep Profile"
        }
    }

    private fun selectDeepMaleVoice(voices: Set<Voice>?, targetLocale: Locale): Voice? {
        if (voices.isNullOrEmpty()) return null
        val localeMatches = voices.filter {
            it.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                !it.isNetworkConnectionRequired
        }.ifEmpty {
            voices.filter { it.locale.language.equals(targetLocale.language, ignoreCase = true) }
        }

        val maleHints = listOf("male", "iom", "iol", "sfg", "tpd", "en-us-x-iom", "en-us-x-iol", "en-gb-x-gbd", "hi-in-x-hie")
        return localeMatches.firstOrNull { voice ->
            val lower = voice.name.lowercase()
            maleHints.any { lower.contains(it) }
        } ?: localeMatches.firstOrNull()
    }

    fun speak(text: String, language: VoiceLanguage, flush: Boolean = true) {
        val engine = tts ?: return
        if (!initialized || text.isBlank()) return

        configureVoiceForLanguage(language)
        val cleanText = text
            .replace(Regex("[*#`>]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3500)

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle()
        engine.speak(cleanText, queueMode, params, "ultron_${UUID.randomUUID()}")
    }

    fun stop() {
        runCatching { tts?.stop() }
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        initialized = false
    }

    companion object {
        private const val TAG = "DeepMaleTtsEngine"
    }
}
