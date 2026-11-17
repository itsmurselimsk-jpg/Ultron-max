package com.example.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.androidtools.AndroidToolExecutor
import com.example.security.CryptoManager
import com.example.security.SecureCredentialStore
import com.example.system.UltronIntentActions
import kotlinx.coroutines.flow.first

private val WidgetBgObsidian = Color(0xFF050507)
private val WidgetPanelCharcoal = Color(0xFF161922)
private val WidgetCrimson = Color(0xFFE11D48)
private val WidgetCrimsonDark = Color(0xFF3F0B19)
private val WidgetAmber = Color(0xFFF59E0B)
private val WidgetEmerald = Color(0xFF10B981)
private val WidgetTextPrimary = Color(0xFFF4F5F7)
private val WidgetTextSecondary = Color(0xFFA0A8B8)

class UltronGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = SecureCredentialStore(context.applicationContext, CryptoManager())
        val prefs = runCatching { store.preferencesFlow.first() }.getOrNull()
        val telemetry = runCatching { AndroidToolExecutor(context.applicationContext).refreshTelemetry() }.getOrNull()

        val battery = telemetry?.batteryPercent ?: 0
        val network = telemetry?.networkTransport ?: "Offline"
        val torchOn = prefs?.widgetTorchActive == true
        val emergencyStop = prefs?.widgetEmergencyStop == true
        val briefing = prefs?.latestBriefingBody?.takeIf { it.isNotBlank() }
            ?: "BAT $battery% • $network • Policy: ${prefs?.riskPolicy?.name ?: "BALANCED"}"

        provideContent {
            UltronWidgetContent(
                context = context,
                batteryPercent = battery,
                networkTransport = network,
                torchActive = torchOn,
                emergencyStop = emergencyStop,
                summaryLine = briefing
            )
        }
    }

    @Composable
    private fun UltronWidgetContent(
        context: Context,
        batteryPercent: Int,
        networkTransport: String,
        torchActive: Boolean,
        emergencyStop: Boolean,
        summaryLine: String
    ) {
        val baseModifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBgObsidian))
            .padding(12.dp)
        val rootModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            baseModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            baseModifier.cornerRadius(16.dp)
        }

        Column(
            modifier = rootModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ULTRON OMEGA",
                    style = TextStyle(
                        color = ColorProvider(WidgetTextPrimary),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = if (emergencyStop) "E-STOP" else "BAT $batteryPercent% • $networkTransport",
                    style = TextStyle(
                        color = ColorProvider(if (emergencyStop) WidgetAmber else WidgetEmerald),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Text(
                text = summaryLine,
                maxLines = 2,
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = 11.sp
                )
            )

            Spacer(modifier = GlanceModifier.height(10.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    text = "Voice",
                    onClick = actionStartActivity(
                        UltronIntentActions.createLaunchIntent(
                            context = context,
                            action = UltronIntentActions.ACTION_LAUNCH_VOICE_CORE
                        )
                    ),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(WidgetCrimson),
                        contentColor = ColorProvider(WidgetTextPrimary)
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Button(
                    text = if (torchActive) "Torch ON" else "Torch",
                    onClick = actionRunCallback<ToggleTorchWidgetAction>(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(if (torchActive) WidgetCrimsonDark else WidgetPanelCharcoal),
                        contentColor = ColorProvider(WidgetTextPrimary)
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Button(
                    text = if (emergencyStop) "Reset" else "E-STOP",
                    onClick = actionRunCallback<ToggleEmergencyStopWidgetAction>(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(if (emergencyStop) WidgetAmber else WidgetCrimsonDark),
                        contentColor = ColorProvider(if (emergencyStop) WidgetBgObsidian else WidgetTextPrimary)
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }
}

class UltronGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UltronGlanceWidget()
}

class ToggleTorchWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appContext = context.applicationContext
        val store = SecureCredentialStore(appContext, CryptoManager())
        val prefs = store.preferencesFlow.first()
        if (prefs.widgetEmergencyStop) {
            UltronGlanceWidget().updateAll(appContext)
            return
        }
        val next = !prefs.widgetTorchActive
        val msg = AndroidToolExecutor(appContext).toggleFlashlight(next)
        val actualState = if (msg.contains("ON", ignoreCase = true)) true else if (msg.contains("OFF", ignoreCase = true)) false else false
        store.updateWidgetStatus(torchActive = actualState)
        UltronGlanceWidget().updateAll(appContext)
    }
}

class ToggleEmergencyStopWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appContext = context.applicationContext
        val store = SecureCredentialStore(appContext, CryptoManager())
        val prefs = store.preferencesFlow.first()
        val nextStop = !prefs.widgetEmergencyStop
        if (nextStop && prefs.widgetTorchActive) {
            AndroidToolExecutor(appContext).toggleFlashlight(false)
            store.updateWidgetStatus(torchActive = false, emergencyStop = true)
        } else {
            store.updateWidgetStatus(emergencyStop = nextStop)
        }
        UltronGlanceWidget().updateAll(appContext)
    }
}
