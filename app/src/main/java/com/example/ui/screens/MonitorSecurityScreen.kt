package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.UltronViewModel
import com.example.auth.AuthManager
import com.example.security.RiskPolicyLevel
import com.example.ui.components.AccessibilityDisclosureDialog
import com.example.ui.components.EmergencyStopButton
import com.example.ui.components.TacticalStatusChip
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonDarkContainer
import com.example.ui.theme.CrimsonLaser
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmberAmber
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MonitorSecurityScreen(
    viewModel: UltronViewModel,
    isKeystoreActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val perms by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val emergencyStop by viewModel.emergencyStopEngaged.collectAsStateWithLifecycle()

    var volumeSlider by remember(telemetry.mediaVolumePercent) { mutableFloatStateOf(telemetry.mediaVolumePercent.toFloat()) }
    var brightnessSlider by remember(telemetry.brightnessPercent) { mutableFloatStateOf(telemetry.brightnessPercent.toFloat()) }
    var appLaunchInput by remember { mutableStateOf("") }
    var patInput by remember { mutableStateOf("") }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSystemState()
        viewModel.setProactiveBriefings(enabled = true)
        if (!granted) {
            viewModel.refreshBriefingNow(forceNotify = false)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .padding(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SYSTEM & SECURITY COMMAND", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Live Telemetry • Device Tools • Keystore & Audit Trail", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                IconButton(onClick = { viewModel.refreshSystemState() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Telemetry", tint = CyberCyan)
                }
            }
        }

        // Hardware Telemetry Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryTile(
                    title = "BATTERY",
                    value = "${telemetry.batteryPercent}%",
                    subtitle = "${if (telemetry.isCharging) "Charging" else "Drain"} • ${telemetry.batteryTempC}°C",
                    icon = Icons.Default.BatteryChargingFull,
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    title = "MEMORY",
                    value = "${telemetry.ramUsedMb} MB",
                    subtitle = "of ${telemetry.ramTotalMb} MB",
                    icon = Icons.Default.Memory,
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    title = "NETWORK",
                    value = telemetry.networkTransport,
                    subtitle = if (telemetry.networkValidated) "Validated" else "Limited",
                    icon = Icons.Default.Wifi,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Android Hardware & Media Controls
        item {
            Surface(
                color = CharcoalDeep,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ANDROID HARDWARE & SYSTEM TOOLS", style = MaterialTheme.typography.labelLarge, color = CyberCyan)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.toggleTorch(!telemetry.torchEnabled) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (telemetry.torchEnabled) EmberAmber else CrimsonCore
                            ),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("torch_toggle_button")
                        ) {
                            Icon(Icons.Default.FlashlightOn, contentDescription = "Toggle Flashlight")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (telemetry.torchEnabled) "Torch ON" else "Torch OFF")
                        }
                        OutlinedButton(
                            onClick = { viewModel.controlMedia("play_pause") },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                            Text("Play/Pause", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { viewModel.controlMedia("next") },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Next", color = TextPrimary)
                        }
                    }

                    Text("Media Volume: ${volumeSlider.toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Slider(
                        value = volumeSlider,
                        onValueChange = { volumeSlider = it },
                        onValueChangeFinished = { viewModel.setMediaVolume(volumeSlider.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = CrimsonCore, activeTrackColor = CrimsonCore)
                    )

                    Text("Brightness: ${brightnessSlider.toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Slider(
                        value = brightnessSlider,
                        onValueChange = { brightnessSlider = it },
                        onValueChangeFinished = { viewModel.setBrightness(brightnessSlider.toInt()) },
                        valueRange = 5f..100f,
                        colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = appLaunchInput,
                            onValueChange = { appLaunchInput = it },
                            placeholder = { Text("App name (e.g., Settings, Chrome)", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonCore,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (appLaunchInput.isNotBlank()) {
                                    viewModel.launchApp(appLaunchInput)
                                    appLaunchInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Launch")
                        }
                    }
                }
            }
        }

        // Security Command & Keystore Vault
        item {
            Surface(
                color = CharcoalDeep,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = CrimsonLaser)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ZERO-TRUST SECURITY", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                        TacticalStatusChip(
                            label = if (isKeystoreActive) "KEYSTORE ACTIVE" else "SOFTWARE KEY",
                            color = if (isKeystoreActive) EmeraldSignal else EmberAmber
                        )
                    }

                    EmergencyStopButton(
                        engaged = emergencyStop,
                        onEngage = { viewModel.engageEmergencyStop() },
                        onReset = { viewModel.resetEmergencyStop() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Risk Policy
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RiskPolicyLevel.entries.forEach { level ->
                            FilterChip(
                                selected = prefs.riskPolicy == level,
                                onClick = {
                                    viewModel.updateSecurityPolicy(
                                        prefs.biometricLockEnabled,
                                        prefs.confirmWithBiometrics,
                                        level,
                                        prefs.cloudSyncEnabled
                                    )
                                },
                                label = { Text(level.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonDarkContainer,
                                    selectedLabelColor = TextPrimary
                                )
                            )
                        }
                    }

                    SecurityToggleRow("App Launch Biometric Lock", prefs.biometricLockEnabled) {
                        viewModel.updateSecurityPolicy(it, prefs.confirmWithBiometrics, prefs.riskPolicy, prefs.cloudSyncEnabled)
                    }
                    SecurityToggleRow("Biometric Gate on High-Risk Tools", prefs.confirmWithBiometrics) {
                        viewModel.updateSecurityPolicy(prefs.biometricLockEnabled, it, prefs.riskPolicy, prefs.cloudSyncEnabled)
                    }
                    SecurityToggleRow("Firebase Encrypted Cloud Sync", prefs.cloudSyncEnabled) {
                        viewModel.updateSecurityPolicy(prefs.biometricLockEnabled, prefs.confirmWithBiometrics, prefs.riskPolicy, it)
                    }

                    // Encrypted GitHub PAT Input
                    OutlinedTextField(
                        value = patInput,
                        onValueChange = { patInput = it },
                        label = {
                            Text(
                                if (prefs.hasGitHubToken) "GitHub PAT (Encrypted in Keystore)" else "Set GitHub Personal Access Token",
                                color = TextSecondary
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonCore,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("github_pat_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveGitHubPat(patInput)
                                patInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_pat_button")
                        ) {
                            Text("Encrypt PAT")
                        }
                        OutlinedButton(
                            onClick = { viewModel.saveGitHubPat("") },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Clear", color = CrimsonLaser)
                        }
                    }

                    // System Surfaces & Proactive Briefings
                    Surface(
                        color = CharcoalElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Widgets, contentDescription = null, tint = CyberCyan)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "SYSTEM SURFACES & BRIEFINGS",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.requestQuickSettingsTile() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDarkContainer),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("add_qs_tile_button")
                                ) {
                                    Text("Add QS Tile", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = { viewModel.requestPinHomeWidget() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDarkContainer),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("pin_home_widget_button")
                                ) {
                                    Text("Pin Widget", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            SecurityToggleRow("Opt-in Proactive Briefings", prefs.proactiveBriefingsEnabled) { enable ->
                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setProactiveBriefings(enable)
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.refreshBriefingNow(forceNotify = true) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("generate_briefing_button")
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = CyberCyan)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate Briefing Now", color = CyberCyan)
                            }
                        }
                    }

                    // Permission & Special Access Manager
                    Text("PERMISSION & SPECIAL ACCESS MANAGER", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    PermissionRow("Microphone", perms.microphoneGranted) { viewModel.openAppDetailsSettings() }
                    PermissionRow("Camera", perms.cameraGranted) { viewModel.openAppDetailsSettings() }
                    PermissionRow("Notifications", perms.notificationsGranted) { viewModel.openAppDetailsSettings() }
                    PermissionRow("Write System Brightness", perms.writeSettingsGranted) { viewModel.openWriteSettings() }
                    PermissionRow("Notification Listener", perms.notificationListenerEnabled) { viewModel.openNotificationListenerSettings() }
                    PermissionRow("Accessibility Assist", perms.accessibilityServiceEnabled) {
                        showAccessibilityDisclosure = true
                    }

                    OutlinedButton(
                        onClick = { AuthManager.signOut(credentialManager, scope) },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = CrimsonLaser)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out of Ultron Omega", color = CrimsonLaser)
                    }
                }
            }
        }

        // Audit Trail
        item {
            Text("SECURITY AUDIT HISTORY (${auditLogs.size})", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        }
        items(auditLogs.take(40), key = { it.id }) { log ->
            Surface(
                color = CharcoalElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${log.actor} • ${log.action}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        Text(
                            text = "${log.riskLevel} • ${log.outcome}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (log.outcome == "DENIED" || log.outcome == "STOPPED") CrimsonLaser else EmeraldSignal
                        )
                    }
                    Text(log.details, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("SHA256: ${log.integrityHash.take(18)}...", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showAccessibilityDisclosure = false
                viewModel.acceptAccessibilityDisclosure()
            },
            onDismiss = { showAccessibilityDisclosure = false }
        )
    }
}

@Composable
private fun TelemetryTile(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CharcoalElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(icon, contentDescription = title, tint = CrimsonLaser)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = CyberCyan, maxLines = 1)
        }
    }
}

@Composable
private fun SecurityToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CrimsonCore)
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onConfigure: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text(
                text = if (granted) "GRANTED / ACTIVE" else "NOT GRANTED",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) EmeraldSignal else EmberAmber
            )
        }
        OutlinedButton(
            onClick = onConfigure,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Text("Configure", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
        }
    }
}
