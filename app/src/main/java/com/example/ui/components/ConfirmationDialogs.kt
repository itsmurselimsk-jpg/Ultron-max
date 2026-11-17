package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.agent.PendingConfirmationRequest
import com.example.security.BiometricAuthManager
import com.example.security.RiskLevel
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonLaser
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmberAmber
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun RiskConfirmationDialog(
    request: PendingConfirmationRequest,
    biometricAuthManager: BiometricAuthManager,
    onResolve: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricReady = !request.evaluation.requiresBiometric ||
        (activity != null && biometricAuthManager.canAuthenticate(activity))

    var secondsRemaining by remember(request.stepId) {
        mutableLongStateOf(((request.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L)
    }

    LaunchedEffect(request.stepId) {
        while (true) {
            val remaining = ((request.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L
            secondsRemaining = remaining
            if (remaining <= 0L) break
            delay(500L)
        }
    }

    val riskColor = when (request.evaluation.riskLevel) {
        RiskLevel.CRITICAL, RiskLevel.HIGH -> CrimsonCore
        RiskLevel.MEDIUM -> EmberAmber
        RiskLevel.LOW -> CyberCyan
    }

    AlertDialog(
        onDismissRequest = { onResolve(false) },
        containerColor = CharcoalElevated,
        title = {
            Column {
                Text(
                    text = "SECURITY GATE: ${request.evaluation.riskLevel.name} RISK",
                    style = MaterialTheme.typography.titleLarge,
                    color = riskColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Auto-cancel in ${secondsRemaining}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (secondsRemaining <= 10L) CrimsonLaser else CyberCyan,
                    modifier = Modifier.testTag("confirmation_countdown_text")
                )
            }
        },
        text = {
            Column {
                Text(
                    text = request.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = ObsidianVoid,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(
                        text = request.evaluation.reason,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                if (request.evaluation.requiresBiometric) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (biometricReady) {
                        Text(
                            text = "Biometric or device credential verification required before execution.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmberAmber
                        )
                    } else {
                        Text(
                            text = "BLOCKED (Fail-Closed): Device biometrics or screen lock is not enrolled. High/Critical actions cannot be authorized until a screen lock is set.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonLaser,
                            modifier = Modifier.testTag("biometric_blocked_notice")
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (biometricReady) {
                Button(
                    onClick = {
                        if (request.evaluation.requiresBiometric) {
                            if (activity != null && biometricAuthManager.canAuthenticate(activity)) {
                                biometricAuthManager.authenticate(
                                    activity = activity,
                                    title = "Ultron Omega Confirmation",
                                    subtitle = "Authorize ${request.evaluation.toolName}",
                                    onSuccess = { onResolve(true) },
                                    onError = { err ->
                                        onError(err)
                                        onResolve(false)
                                    }
                                )
                            } else {
                                onError("Biometric verification failed: device lock unavailable.")
                                onResolve(false)
                            }
                        } else {
                            onResolve(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                    modifier = Modifier.testTag("confirm_risk_action_button")
                ) {
                    Text("Authorize")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.testTag("open_security_settings_button")
                ) {
                    Text("Set Screen Lock", color = CyberCyan)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onResolve(false) },
                modifier = Modifier.testTag("deny_risk_action_button")
            ) {
                Text("Deny", color = TextSecondary)
            }
        }
    )
}

@Composable
fun AccessibilityDisclosureDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalElevated,
        title = {
            Text(
                text = "Accessibility Service Disclosure",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = "Ultron Omega uses the optional Accessibility Service solely when explicitly triggered by you to perform approved navigation actions (Back, Home, Recents, Notifications) or summarize visible on-screen text for your prompt. No keystrokes, passwords, or background screens are silently recorded or transmitted without your request. You can disable this anytime in Android Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                modifier = Modifier.testTag("accept_accessibility_disclosure")
            ) {
                Text("Continue to Settings")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_accessibility_disclosure")
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
