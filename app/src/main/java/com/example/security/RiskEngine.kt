package com.example.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class RiskEvaluation(
    val toolName: String,
    val riskLevel: RiskLevel,
    val requiresUserConfirmation: Boolean,
    val requiresBiometric: Boolean,
    val reason: String
)

class RiskEngine {
    private val _emergencyStopEngaged = MutableStateFlow(false)
    val emergencyStopEngaged: StateFlow<Boolean> = _emergencyStopEngaged.asStateFlow()

    fun engageEmergencyStop() {
        _emergencyStopEngaged.value = true
    }

    fun resetEmergencyStop() {
        _emergencyStopEngaged.value = false
    }

    fun evaluateTool(
        toolName: String,
        argumentsSummary: String,
        policy: RiskPolicyLevel,
        biometricRequiredForHigh: Boolean
    ): RiskEvaluation {
        if (_emergencyStopEngaged.value) {
            return RiskEvaluation(
                toolName = toolName,
                riskLevel = RiskLevel.CRITICAL,
                requiresUserConfirmation = true,
                requiresBiometric = true,
                reason = "Emergency Stop is currently engaged. Reset Emergency Stop before executing tools."
            )
        }

        val (level, baseReason) = when (toolName) {
            "getBatteryStatus", "getNetworkStatus", "searchMemories", "listWorkspaceFiles", "readWorkspaceFile" ->
                RiskLevel.LOW to "Read-only local telemetry or workspace inspection"
            "webResearch", "analyzeUrl", "githubRead" ->
                RiskLevel.LOW to "Read-only web grounding or repository query"
            "toggleFlashlight", "setVolume", "setBrightness", "controlMedia", "saveMemory" ->
                RiskLevel.MEDIUM to "Modifies device state or persists encrypted memory ($argumentsSummary)"
            "launchApp", "openSystemSettings", "readNotifications", "writeWorkspaceFile" ->
                RiskLevel.HIGH to "External app launch, notification inspection, or file mutation ($argumentsSummary)"
            "performAccessibilityAction", "deleteMemory", "wipeAllData", "githubWrite" ->
                RiskLevel.CRITICAL to "Privileged UI automation, destructive deletion, or remote repository mutation ($argumentsSummary)"
            else ->
                RiskLevel.HIGH to "Unclassified tool execution ($toolName)"
        }

        val requiresConfirm = when (policy) {
            RiskPolicyLevel.STRICT -> level != RiskLevel.LOW
            RiskPolicyLevel.BALANCED -> level == RiskLevel.HIGH || level == RiskLevel.CRITICAL
            RiskPolicyLevel.AUTONOMOUS -> level == RiskLevel.CRITICAL
        }

        val requiresBiometric = biometricRequiredForHigh &&
            (level == RiskLevel.HIGH || level == RiskLevel.CRITICAL)

        return RiskEvaluation(
            toolName = toolName,
            riskLevel = level,
            requiresUserConfirmation = requiresConfirm,
            requiresBiometric = requiresBiometric,
            reason = baseReason
        )
    }
}
