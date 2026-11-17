package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.AiUiState
import com.example.CoreVisualMode
import com.example.UltronViewModel
import com.example.security.VoiceLanguage
import com.example.ui.components.EmergencyStopButton
import com.example.ui.components.LiveAudioWaveform
import com.example.ui.components.ProactiveBriefingCard
import com.example.ui.components.TacticalStatusChip
import com.example.ui.components.UltronNeuralCore
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalPanel
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

@Composable
fun CoreVoiceScreen(
    viewModel: UltronViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val waveformBars by viewModel.waveformBars.collectAsStateWithLifecycle()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val ttsVoiceName by viewModel.activeTtsVoiceName.collectAsStateWithLifecycle()
    val emergencyStop by viewModel.emergencyStopEngaged.collectAsStateWithLifecycle()

    var inputPrompt by remember { mutableStateOf("") }
    var wakeWordInput by remember(prefs.wakeWord) { mutableStateOf(prefs.wakeWord) }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        viewModel.refreshSystemState()
        if (micGranted) {
            viewModel.toggleVoiceListening()
        }
    }

    val coreMode = when {
        emergencyStop -> CoreVisualMode.ALERT
        isTtsSpeaking -> CoreVisualMode.SPEAKING
        voiceState == VoiceState.LISTENING || voiceState == VoiceState.WAKE_LISTENING -> CoreVisualMode.LISTENING
        aiState is AiUiState.Loading || aiState is AiUiState.Streaming -> CoreVisualMode.THINKING
        else -> CoreVisualMode.IDLE
    }

    LaunchedEffect(messages.size, aiState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Header HUD
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ULTRON OMEGA",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "VOICE: ${ttsVoiceName.take(28)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.startNewConversation() },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddComment,
                        contentDescription = "New Conversation",
                        tint = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                EmergencyStopButton(
                    engaged = emergencyStop,
                    onEngage = { viewModel.engageEmergencyStop() },
                    onReset = { viewModel.resetEmergencyStop() }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Core & Waveform Panel
        Surface(
            color = CharcoalDeep,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    UltronNeuralCore(
                        mode = coreMode,
                        amplitude = waveformBars.lastOrNull() ?: 0.1f,
                        onClick = {
                            if (isTtsSpeaking) {
                                viewModel.bargeInInterrupt()
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    viewModel.toggleVoiceListening()
                                } else {
                                    val perms = buildList {
                                        add(Manifest.permission.RECORD_AUDIO)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }.toTypedArray()
                                    micPermissionLauncher.launch(perms)
                                }
                            }
                        },
                        modifier = Modifier.size(124.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TacticalStatusChip(
                                label = coreMode.name,
                                color = when (coreMode) {
                                    CoreVisualMode.IDLE -> EmeraldSignal
                                    CoreVisualMode.LISTENING -> NeonCyan
                                    CoreVisualMode.THINKING -> ElectricBlue
                                    CoreVisualMode.SPEAKING -> UltravioletPurple
                                    CoreVisualMode.ALERT -> CrimsonLaser
                                }
                            )
                            TacticalStatusChip(label = prefs.voiceLanguage.shortLabel, color = CyberCyan)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LiveAudioWaveform(
                            bars = waveformBars,
                            isActive = voiceState == VoiceState.LISTENING || voiceState == VoiceState.WAKE_LISTENING || isTtsSpeaking,
                            accentColor = if (isTtsSpeaking) CrimsonLaser else NeonCyan
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        val statusLine = when {
                            partialTranscript.isNotBlank() -> "\"$partialTranscript\""
                            !voiceError.isNullOrBlank() -> voiceError!!
                            voiceState == VoiceState.WAKE_LISTENING -> "Listening for wake word '${prefs.wakeWord}'..."
                            voiceState == VoiceState.LISTENING -> "Listening in ${prefs.voiceLanguage.displayName}..."
                            isTtsSpeaking -> "Speaking (Tap Core to interrupt)"
                            else -> "Tap Core or Mic for continuous voice"
                        }
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!voiceError.isNullOrBlank()) CrimsonLaser else TextSecondary,
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Language & Model Tier Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VoiceLanguage.entries.forEach { lang ->
                        val selected = prefs.voiceLanguage == lang
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateVoiceLanguage(lang) },
                            label = { Text(lang.displayName, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonDarkContainer,
                                selectedLabelColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("lang_chip_${lang.name.lowercase()}")
                        )
                    }
                }

                // Voice Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Continuous", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = prefs.continuousVoiceEnabled,
                            onCheckedChange = {
                                viewModel.updateVoiceToggles(it, prefs.bargeInEnabled, prefs.ttsEnabled)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = CrimsonCore),
                            modifier = Modifier.testTag("continuous_voice_switch")
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wake '${prefs.wakeWord}'", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = prefs.wakeWordEnabled,
                            onCheckedChange = { viewModel.updateWakeWord(wakeWordInput, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = CrimsonCore),
                            modifier = Modifier.testTag("wake_word_switch")
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.updateVoiceToggles(
                                prefs.continuousVoiceEnabled,
                                prefs.bargeInEnabled,
                                !prefs.ttsEnabled
                            )
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("tts_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (prefs.ttsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Toggle Male Voice Output",
                            tint = if (prefs.ttsEnabled) CrimsonLaser else TextMuted
                        )
                    }
                }

                // Model Tier Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("AUTO", "LITE", "FLASH", "ARGON").forEach { tier ->
                        FilterChip(
                            selected = prefs.preferredModelTier == tier,
                            onClick = { viewModel.updateModelTier(tier) },
                            label = { Text("MODEL:$tier", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CharcoalPanel,
                                selectedLabelColor = CyberCyan
                            ),
                            modifier = Modifier.testTag("model_tier_${tier.lowercase()}")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedVisibility(
            visible = !prefs.briefingDismissed && prefs.latestBriefingBody.isNotBlank()
        ) {
            Column {
                ProactiveBriefingCard(
                    title = prefs.latestBriefingTitle,
                    body = prefs.latestBriefingBody,
                    timestamp = prefs.latestBriefingTimestamp,
                    onSpeak = { viewModel.speakLatestBriefing() },
                    onRefresh = { viewModel.refreshBriefingNow(forceNotify = false) },
                    onDismiss = { viewModel.dismissBriefingCard() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Conversation Stream
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (messages.isEmpty() && aiState is AiUiState.Idle) {
                item {
                    Surface(
                        color = CharcoalElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "NEURAL INTERFACE READY",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrimsonLaser
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ask in English, Hindi, Hinglish, Bangla, or Banglish. Ultron routes dynamically across Gemini 3.5 Lite, 3.8 Flash, and 4 Argon, injects encrypted semantic memories, and calls Android hardware tools safely.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                Surface(
                    color = if (isUser) CrimsonDarkContainer.copy(alpha = 0.65f) else CharcoalElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (isUser) CrimsonCore.copy(alpha = 0.5f) else HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isUser) "OPERATOR" else "ULTRON OMEGA",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) CrimsonLaser else CyberCyan
                            )
                            if (!isUser && msg.modelUsed.isNotBlank()) {
                                Text(
                                    text = "${msg.modelUsed} • ${if (msg.verified) "VERIFIED" else "RAW"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldSignal
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            when (val st = aiState) {
                is AiUiState.Loading -> {
                    item {
                        Surface(
                            color = CharcoalElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, UltravioletPurple.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Synthesizing intelligence & retrieving semantic memories...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricBlue,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                is AiUiState.Streaming -> {
                    item {
                        Surface(
                            color = CharcoalElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "STREAMING • ${st.tier.modelId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = st.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
                is AiUiState.Error -> {
                    item {
                        Surface(
                            color = CrimsonDarkContainer,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CrimsonLaser),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = st.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                else -> Unit
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input & Voice Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                placeholder = { Text("Command Ultron Omega...", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonCore,
                    unfocusedBorderColor = HairlineBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = CharcoalDeep,
                    unfocusedContainerColor = CharcoalDeep
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("core_prompt_input")
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.WAKE_LISTENING) {
                            viewModel.stopVoiceSession()
                        } else {
                            viewModel.toggleVoiceListening()
                        }
                    } else {
                        micPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (voiceState == VoiceState.LISTENING) CrimsonCore else CharcoalElevated,
                    contentColor = TextPrimary
                ),
                modifier = Modifier.size(48.dp).testTag("voice_mic_button")
            ) {
                Icon(
                    imageVector = when {
                        voiceState == VoiceState.LISTENING -> Icons.Default.Stop
                        isTtsSpeaking -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Voice Input"
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val text = inputPrompt.trim()
                    if (text.isNotBlank()) {
                        inputPrompt = ""
                        viewModel.sendPrompt(text)
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = CrimsonCore,
                    contentColor = TextPrimary
                ),
                modifier = Modifier.size(48.dp).testTag("send_prompt_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Prompt"
                )
            }
        }
    }
}
