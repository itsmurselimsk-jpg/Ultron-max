package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import com.example.ui.theme.EmberAmber
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RiskConfirmationDialog(
    request: PendingConfirmationRequest,
    biometricAuthManager: BiometricAuthManager,
    onResolve: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val riskColor = when (request.evaluation.riskLevel) {
        RiskLevel.CRITICAL, RiskLevel.HIGH -> CrimsonCore
        RiskLevel.MEDIUM -> EmberAmber
        RiskLevel.LOW -> TextSecondary
    }

    AlertDialog(
        onDismissRequest = { onResolve(false) },
        containerColor = CharcoalElevated,
        title = {
            Text(
                text = "SECURITY GATE: ${request.evaluation.riskLevel.name} RISK",
                style = MaterialTheme.typography.titleLarge,
                color = riskColor
            )
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
                    Text(
                        text = "Biometric verification required before execution.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmberAmber
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val activity = context as? FragmentActivity
                    if (request.evaluation.requiresBiometric && activity != null && biometricAuthManager.canAuthenticate(activity)) {
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
                        onResolve(true)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                modifier = Modifier.testTag("confirm_risk_action_button")
            ) {
                Text("Authorize")
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
