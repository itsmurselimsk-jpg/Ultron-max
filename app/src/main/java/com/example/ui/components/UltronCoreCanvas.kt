package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.CoreVisualMode
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonDarkContainer
import com.example.ui.theme.CrimsonLaser
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmberAmber
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UltronNeuralCore(
    mode: CoreVisualMode,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "ultron_core")
    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    CoreVisualMode.THINKING -> 2600
                    CoreVisualMode.SPEAKING -> 4000
                    CoreVisualMode.LISTENING -> 5200
                    CoreVisualMode.ALERT -> 1800
                    CoreVisualMode.IDLE -> 11000
                },
                easing = LinearEasing
            )
        ),
        label = "outer_angle"
    )
    val innerAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing)
        ),
        label = "inner_angle"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val coreColor = when (mode) {
        CoreVisualMode.IDLE -> CrimsonCore
        CoreVisualMode.LISTENING -> CyberCyan
        CoreVisualMode.THINKING -> EmberAmber
        CoreVisualMode.SPEAKING -> CrimsonLaser
        CoreVisualMode.ALERT -> CrimsonLaser
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(164.dp)
            .clip(CircleShape)
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("ultron_neural_core")
            .semantics { contentDescription = "Ultron Omega AI Core, state ${mode.name}" }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f

            // Ambient radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = (0.34f + amplitude * 0.25f).coerceAtMost(0.6f)),
                        ObsidianVoid.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )

            // Outer segmented tactical ring
            val outerR = baseRadius * 0.86f
            for (i in 0 until 3) {
                drawArc(
                    color = coreColor.copy(alpha = 0.82f),
                    startAngle = outerAngle + i * 120f,
                    sweepAngle = 76f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerR, center.y - outerR),
                    size = Size(outerR * 2f, outerR * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Middle counter-rotating ring
            val midR = baseRadius * 0.68f
            for (i in 0 until 4) {
                drawArc(
                    color = TextPrimary.copy(alpha = 0.45f),
                    startAngle = innerAngle + i * 90f,
                    sweepAngle = 48f,
                    useCenter = false,
                    topLeft = Offset(center.x - midR, center.y - midR),
                    size = Size(midR * 2f, midR * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Inner neural nodes
            val nodeR = baseRadius * 0.52f
            for (i in 0 until 6) {
                val rad = Math.toRadians((outerAngle * 0.5 + i * 60).toDouble())
                val nx = center.x + (nodeR * cos(rad)).toFloat()
                val ny = center.y + (nodeR * sin(rad)).toFloat()
                drawCircle(
                    color = coreColor,
                    radius = 3.dp.toPx(),
                    center = Offset(nx, ny)
                )
            }

            // Pulsing central plasma orb
            val orbRadius = baseRadius * (0.32f + amplitude * 0.08f) * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, coreColor, CrimsonDarkContainer),
                    center = center,
                    radius = orbRadius
                ),
                radius = orbRadius,
                center = center
            )
        }
    }
}

@Composable
fun LiveAudioWaveform(
    bars: List<Float>,
    isActive: Boolean,
    accentColor: Color = CrimsonLaser,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .testTag("live_audio_waveform")
    ) {
        val count = bars.size.coerceAtLeast(1)
        val totalWidth = size.width
        val slotWidth = totalWidth / count
        val barWidth = (slotWidth * 0.55f).coerceAtLeast(3.dp.toPx())
        val centerY = size.height / 2f

        bars.forEachIndexed { index, level ->
            val effective = if (isActive) level.coerceIn(0.10f, 1.0f) else 0.09f
            val barHeight = (size.height * effective).coerceAtLeast(4.dp.toPx())
            val x = index * slotWidth + (slotWidth - barWidth) / 2f
            val y = centerY - barHeight / 2f
            drawRoundRect(
                color = if (isActive) accentColor.copy(alpha = 0.55f + effective * 0.45f) else HairlineBorder,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun TacticalStatusChip(
    label: String,
    color: Color = EmeraldSignal,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = CharcoalElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmergencyStopButton(
    engaged: Boolean,
    onEngage: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { if (engaged) onReset() else onEngage() },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (engaged) EmberAmber else CrimsonCore,
            contentColor = if (engaged) ObsidianVoid else TextPrimary
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("emergency_stop_button")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Emergency Stop",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (engaged) "RESET E-STOP" else "E-STOP",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
