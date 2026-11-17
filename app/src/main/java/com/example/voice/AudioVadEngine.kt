package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Real-time AudioRecord PCM sampler providing live RMS amplitude,
 * 24-band waveform levels for the Compose UI, and Voice Activity Detection (VAD)
 * for speech interruption (barge-in).
 */
class AudioVadEngine(private val context: Context) {

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _waveformBars = MutableStateFlow(List(24) { 0.08f })
    val waveformBars: StateFlow<List<Float>> = _waveformBars.asStateFlow()

    private val _voiceActive = MutableStateFlow(false)
    val voiceActive: StateFlow<Boolean> = _voiceActive.asStateFlow()

    private var captureJob: Job? = null

    fun startMonitoring(
        scope: CoroutineScope,
        bargeInThreshold: Float = 0.24f,
        onBargeInDetected: () -> Unit = {}
    ) {
        if (captureJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16_000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2
                )
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize on this device.")
                    return@launch
                }

                audioRecord.startRecording()
                val buffer = ShortArray(minBuffer / 2)
                var consecutiveLoudFrames = 0

                while (isActive) {
                    val readCount = audioRecord.read(buffer, 0, buffer.size)
                    if (readCount > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until readCount) {
                            val sample = buffer[i].toDouble() / 32768.0
                            sumSquares += sample * sample
                        }
                        val rms = sqrt(sumSquares / readCount).toFloat()
                        val normalized = (rms * 4.2f).coerceIn(0.05f, 1.0f)
                        _amplitude.value = normalized

                        // Shift waveform history for crisp animated bars
                        val current = _waveformBars.value.toMutableList()
                        if (current.isNotEmpty()) {
                            current.removeAt(0)
                            current.add(normalized)
                            _waveformBars.value = current
                        }

                        val isSpeaking = normalized >= bargeInThreshold
                        _voiceActive.value = isSpeaking
                        if (isSpeaking) {
                            consecutiveLoudFrames++
                            if (consecutiveLoudFrames >= 3) {
                                consecutiveLoudFrames = 0
                                onBargeInDetected()
                            }
                        } else {
                            consecutiveLoudFrames = 0
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing RECORD_AUDIO permission for VAD", e)
            } catch (e: Exception) {
                Log.w(TAG, "AudioVadEngine stopped: ${e.message}")
            } finally {
                runCatching { audioRecord?.stop() }
                runCatching { audioRecord?.release() }
                _amplitude.value = 0f
                _voiceActive.value = false
                _waveformBars.value = List(24) { 0.08f }
            }
        }
    }

    fun stopMonitoring() {
        captureJob?.cancel()
        captureJob = null
        _amplitude.value = 0f
        _voiceActive.value = false
        _waveformBars.value = List(24) { 0.08f }
    }

    companion object {
        private const val TAG = "AudioVadEngine"
    }
}
