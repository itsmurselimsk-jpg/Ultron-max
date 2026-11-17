package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.CoreVisualMode
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonDarkContainer
import com.example.ui.theme.CrimsonLaser
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmberAmber
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UltravioletPurple
import com.example.voice.VoiceState
import java.text.DateFormat
import java.util.Date

@Composable
fun PersistentMiniCoreBar(
    mode: CoreVisualMode,
    voiceState: VoiceState,
    isTtsSpeaking: Boolean,
    emergencyStop: Boolean,
    partialTranscript: String,
    onOpenCore: () -> Unit,
    onToggleVoice: () -> Unit,
    onBargeIn: () -> Unit,
    onEngageEmergencyStop: () -> Unit,
    onResetEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "mini_core_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val beaconColor = when (mode) {
        CoreVisualMode.IDLE -> EmeraldSignal
        CoreVisualMode.LISTENING -> NeonCyan
        CoreVisualMode.THINKING -> ElectricBlue
        CoreVisualMode.SPEAKING -> UltravioletPurple
        CoreVisualMode.ALERT -> CrimsonLaser
    }

    val isListening = voiceState == VoiceState.LISTENING || voiceState == VoiceState.WAKE_LISTENING
    val statusText = when {
        emergencyStop -> "E-STOP ENGAGED • Tap Reset"
        partialTranscript.isNotBlank() -> "\"$partialTranscript\""
        isTtsSpeaking -> "Ultron speaking • Tap Mic to interrupt"
        voiceState == VoiceState.WAKE_LISTENING -> "Wake word armed..."
        voiceState == VoiceState.LISTENING -> "Listening..."
        mode == CoreVisualMode.THINKING -> "Reasoning active..."
        else -> "Ultron Core ready • Tap to return"
    }

    Surface(
        color = CharcoalElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            when {
                emergencyStop -> CrimsonLaser
                mode == CoreVisualMode.THINKING -> ElectricBlue.copy(alpha = 0.65f)
                isListening -> NeonCyan.copy(alpha = 0.65f)
                else -> HairlineBorder
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("persistent_mini_core_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onOpenCore)
                    .testTag("mini_core_jump_button")
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    drawCircle(
                        color = beaconColor.copy(alpha = if (mode == CoreVisualMode.IDLE) 0.8f else pulseAlpha),
                        radius = size.minDimension / 2f
                    )
                    drawCircle(
                        color = TextPrimary,
                        radius = size.minDimension / 5f
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CORE • ${mode.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = beaconColor,
                        maxLines = 1
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    when {
                        isTtsSpeaking -> onBargeIn()
                        else -> onToggleVoice()
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isListening || isTtsSpeaking) CrimsonCore else CrimsonDarkContainer,
                    contentColor = TextPrimary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mini_core_voice_button")
            ) {
                Icon(
                    imageVector = when {
                        isListening -> Icons.Default.Stop
                        isTtsSpeaking -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = if (isListening) "Stop voice" else "Toggle voice"
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = { if (emergencyStop) onResetEmergencyStop() else onEngageEmergencyStop() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (emergencyStop) EmberAmber else CrimsonCore,
                    contentColor = if (emergencyStop) ObsidianVoid else TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mini_core_estop_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Emergency Stop",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (emergencyStop) "RESET" else "STOP",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun ProactiveBriefingCard(
    title: String,
    body: String,
    timestamp: Long,
    onSpeak: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeLabel = if (timestamp > 0L) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
    } else {
        "Live"
    }

    Surface(
        color = CharcoalElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CrimsonCore.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("proactive_briefing_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "ULTRON BRIEFING" },
                        style = MaterialTheme.typography.labelLarge,
                        color = CrimsonLaser
                    )
                    Text(
                        text = "Updated $timeLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("briefing_refresh_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Briefing", tint = CyberCyan)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("briefing_dismiss_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss Briefing", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSpeak,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("briefing_speak_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Speak Briefing",
                        tint = CrimsonLaser,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Speak Briefing", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
    }
}
