package com.example

import android.content.Context
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import com.example.voice.AudioVadEngine
import com.example.voice.DeepMaleTtsEngine
import com.example.voice.VoiceEngine
import com.example.voice.VoiceRecoveryDecision
import com.example.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UltronVoiceRecoveryTest {

    @Test
    fun `extractWakeWordCommand enforces wake word filtering cleanly`() {
        assertEquals(
            "check battery status",
            VoiceEngine.extractWakeWordCommand("Hey Ultron, check battery status", "ultron", requireWakeWord = true)
        )
        assertEquals(
            "Ultron, ready for orders.",
            VoiceEngine.extractWakeWordCommand("Ultron", "ultron", requireWakeWord = true)
        )
        assertNull(
            VoiceEngine.extractWakeWordCommand("Please turn on the lights", "ultron", requireWakeWord = true)
        )
        assertEquals(
            "Please turn on the lights",
            VoiceEngine.extractWakeWordCommand("Please turn on the lights", "ultron", requireWakeWord = false)
        )
    }

    @Test
    fun `classifyRecoveryDecision applies bounded exponential backoff and fails safely`() {
        val retry1 = VoiceEngine.classifyRecoveryDecision(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, continuous = true, attemptCount = 1)
        val retry2 = VoiceEngine.classifyRecoveryDecision(SpeechRecognizer.ERROR_NETWORK, continuous = true, attemptCount = 2)
        val retry3 = VoiceEngine.classifyRecoveryDecision(SpeechRecognizer.ERROR_AUDIO, continuous = true, attemptCount = 3)
        val fail4 = VoiceEngine.classifyRecoveryDecision(SpeechRecognizer.ERROR_NETWORK_TIMEOUT, continuous = true, attemptCount = 4)

        assertTrue(retry1 is VoiceRecoveryDecision.Retry && retry1.delayMs == 450L)
        assertTrue(retry2 is VoiceRecoveryDecision.Retry && retry2.delayMs == 1000L)
        assertTrue(retry3 is VoiceRecoveryDecision.Retry && retry3.delayMs == 2000L)
        assertTrue(fail4 is VoiceRecoveryDecision.Fail)

        val rearm = VoiceEngine.classifyRecoveryDecision(SpeechRecognizer.ERROR_NO_MATCH, continuous = true, attemptCount = 1)
        assertEquals(VoiceRecoveryDecision.SilentRearm, rearm)

        val permFail = VoiceEngine.classifyRecoveryDecision(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, continuous = true, attemptCount = 1)
        assertTrue(permFail is VoiceRecoveryDecision.Fail)
    }

    @Test
    fun `syncTtsSpeakingState transitions cleanly between SPEAKING and IDLE`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tts = DeepMaleTtsEngine(context)
        val voice = VoiceEngine(context, AudioVadEngine(context), tts)

        assertEquals(VoiceState.IDLE, voice.voiceState.value)
        voice.syncTtsSpeakingState(true)
        assertEquals(VoiceState.SPEAKING, voice.voiceState.value)
        voice.syncTtsSpeakingState(false)
        assertEquals(VoiceState.IDLE, voice.voiceState.value)
        tts.shutdown()
    }
}
