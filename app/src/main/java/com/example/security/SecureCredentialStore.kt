package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ultronPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ultron_omega_secure_prefs"
)

enum class VoiceLanguage(val bcp47Tag: String, val displayName: String, val shortLabel: String) {
    ENGLISH("en-US", "English", "EN"),
    HINDI("hi-IN", "Hindi", "HI"),
    HINGLISH("hi-IN", "Hinglish", "HI-EN"),
    BANGLA("bn-IN", "Bangla", "BN"),
    BANGLISH("bn-IN", "Banglish", "BN-EN");

    companion object {
        fun fromName(name: String?): VoiceLanguage =
            entries.firstOrNull { it.name == name } ?: ENGLISH
    }
}

enum class RiskPolicyLevel(val label: String, val description: String) {
    STRICT("Strict Zero-Trust", "Confirm all MEDIUM, HIGH, and CRITICAL actions"),
    BALANCED("Balanced Tactical", "Confirm HIGH and CRITICAL actions; auto-run LOW/MEDIUM"),
    AUTONOMOUS("Supervised Autonomous", "Confirm CRITICAL actions only");

    companion object {
        fun fromName(name: String?): RiskPolicyLevel =
            entries.firstOrNull { it.name == name } ?: BALANCED
    }
}

data class UserPreferencesSnapshot(
    val wakeWord: String = "ultron",
    val wakeWordEnabled: Boolean = false,
    val continuousVoiceEnabled: Boolean = false,
    val bargeInEnabled: Boolean = true,
    val ttsEnabled: Boolean = true,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.ENGLISH,
    val biometricLockEnabled: Boolean = false,
    val confirmWithBiometrics: Boolean = false,
    val riskPolicy: RiskPolicyLevel = RiskPolicyLevel.BALANCED,
    val cloudSyncEnabled: Boolean = true,
    val hasGitHubToken: Boolean = false,
    val preferredModelTier: String = "AUTO",
    val accessibilityConsentAccepted: Boolean = false,
    val proactiveBriefingsEnabled: Boolean = false,
    val briefingIntervalHours: Int = 12,
    val latestBriefingTitle: String = "",
    val latestBriefingBody: String = "",
    val latestBriefingTimestamp: Long = 0L,
    val briefingDismissed: Boolean = false,
    val widgetTorchActive: Boolean = false,
    val widgetEmergencyStop: Boolean = false,
    val activeKeyVersion: Int = 1,
    val lastKeyRotationAt: Long = 0L
)

class SecureCredentialStore(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    private object Keys {
        val ENCRYPTED_GITHUB_PAT = stringPreferencesKey("encrypted_github_pat")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val CONTINUOUS_VOICE = booleanPreferencesKey("continuous_voice")
        val BARGE_IN = booleanPreferencesKey("barge_in")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val CONFIRM_WITH_BIOMETRICS = booleanPreferencesKey("confirm_with_biometrics")
        val RISK_POLICY = stringPreferencesKey("risk_policy")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val PREFERRED_MODEL_TIER = stringPreferencesKey("preferred_model_tier")
        val ACCESSIBILITY_CONSENT = booleanPreferencesKey("accessibility_consent")
        val PROACTIVE_BRIEFINGS = booleanPreferencesKey("proactive_briefings")
        val BRIEFING_INTERVAL_HOURS = intPreferencesKey("briefing_interval_hours")
        val LATEST_BRIEFING_TITLE = stringPreferencesKey("latest_briefing_title")
        val LATEST_BRIEFING_BODY = stringPreferencesKey("latest_briefing_body")
        val LATEST_BRIEFING_TS = longPreferencesKey("latest_briefing_ts")
        val BRIEFING_DISMISSED = booleanPreferencesKey("briefing_dismissed")
        val WIDGET_TORCH_ACTIVE = booleanPreferencesKey("widget_torch_active")
        val WIDGET_EMERGENCY_STOP = booleanPreferencesKey("widget_emergency_stop")
        val ACTIVE_KEY_VERSION = intPreferencesKey("active_key_version")
        val LAST_KEY_ROTATION_AT = longPreferencesKey("last_key_rotation_at")
    }

    val preferencesFlow: Flow<UserPreferencesSnapshot> = context.ultronPrefsDataStore.data.map { prefs ->
        UserPreferencesSnapshot(
            wakeWord = prefs[Keys.WAKE_WORD] ?: "ultron",
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            continuousVoiceEnabled = prefs[Keys.CONTINUOUS_VOICE] ?: false,
            bargeInEnabled = prefs[Keys.BARGE_IN] ?: true,
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            voiceLanguage = VoiceLanguage.fromName(prefs[Keys.VOICE_LANGUAGE]),
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            confirmWithBiometrics = prefs[Keys.CONFIRM_WITH_BIOMETRICS] ?: false,
            riskPolicy = RiskPolicyLevel.fromName(prefs[Keys.RISK_POLICY]),
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: true,
            hasGitHubToken = !prefs[Keys.ENCRYPTED_GITHUB_PAT].isNullOrBlank(),
            preferredModelTier = prefs[Keys.PREFERRED_MODEL_TIER] ?: "AUTO",
            accessibilityConsentAccepted = prefs[Keys.ACCESSIBILITY_CONSENT] ?: false,
            proactiveBriefingsEnabled = prefs[Keys.PROACTIVE_BRIEFINGS] ?: false,
            briefingIntervalHours = prefs[Keys.BRIEFING_INTERVAL_HOURS] ?: 12,
            latestBriefingTitle = prefs[Keys.LATEST_BRIEFING_TITLE] ?: "",
            latestBriefingBody = prefs[Keys.LATEST_BRIEFING_BODY] ?: "",
            latestBriefingTimestamp = prefs[Keys.LATEST_BRIEFING_TS] ?: 0L,
            briefingDismissed = prefs[Keys.BRIEFING_DISMISSED] ?: false,
            widgetTorchActive = prefs[Keys.WIDGET_TORCH_ACTIVE] ?: false,
            widgetEmergencyStop = prefs[Keys.WIDGET_EMERGENCY_STOP] ?: false,
            activeKeyVersion = (prefs[Keys.ACTIVE_KEY_VERSION] ?: 1).coerceAtLeast(1),
            lastKeyRotationAt = prefs[Keys.LAST_KEY_ROTATION_AT] ?: 0L
        )
    }

    suspend fun saveGitHubPat(token: String) {
        val cleaned = token.trim()
        context.ultronPrefsDataStore.edit { prefs ->
            if (cleaned.isEmpty()) {
                prefs.remove(Keys.ENCRYPTED_GITHUB_PAT)
            } else {
                prefs[Keys.ENCRYPTED_GITHUB_PAT] = cryptoManager.encrypt(cleaned)
            }
        }
    }

    suspend fun getGitHubPat(): String? {
        val encrypted = context.ultronPrefsDataStore.data.first()[Keys.ENCRYPTED_GITHUB_PAT]
        if (encrypted.isNullOrBlank()) return null
        return runCatching { cryptoManager.decrypt(encrypted) }.getOrNull()
    }

    suspend fun updateWakeWord(phrase: String, enabled: Boolean) {
        context.ultronPrefsDataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD] = phrase.trim().lowercase().ifBlank { "ultron" }
            prefs[Keys.WAKE_WORD_ENABLED] = enabled
        }
    }

    suspend fun updateVoiceSettings(
        continuous: Boolean,
        bargeIn: Boolean,
        ttsEnabled: Boolean,
        language: VoiceLanguage
    ) {
        context.ultronPrefsDataStore.edit { prefs ->
            prefs[Keys.CONTINUOUS_VOICE] = continuous
            prefs[Keys.BARGE_IN] = bargeIn
            prefs[Keys.TTS_ENABLED] = ttsEnabled
            prefs[Keys.VOICE_LANGUAGE] = language.name
        }
    }

    suspend fun updateSecuritySettings(
        biometricLock: Boolean,
        confirmWithBiometrics: Boolean,
        riskPolicy: RiskPolicyLevel,
        cloudSyncEnabled: Boolean
    ) {
        context.ultronPrefsDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_LOCK] = biometricLock
            prefs[Keys.CONFIRM_WITH_BIOMETRICS] = confirmWithBiometrics
            prefs[Keys.RISK_POLICY] = riskPolicy.name
            prefs[Keys.CLOUD_SYNC_ENABLED] = cloudSyncEnabled
        }
    }

    suspend fun updateModelTier(tier: String) {
        context.ultronPrefsDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_MODEL_TIER] = tier
        }
    }

    suspend fun setAccessibilityConsentAccepted(accepted: Boolean) {
        context.ultronPrefsDataStore.edit { prefs ->
            prefs[Keys.ACCESSIBILITY_CONSENT] = accepted
        }
    }

    suspend fun updateBriefingPreference(enabled: Boolean, intervalHours: Int = 12) {
        context.ultronPrefsDataStore.edit { prefs ->
            prefs[Keys.PROACTIVE_BRIEFINGS] = enabled
            prefs[Keys.BRIEFING_INTERVAL_HOURS] = intervalHours.coerceIn(1, 24)
        }
    }

    suspend fun saveBriefingSnapshot(title: String, body: String, timestamp: Long) {
        context.ultronPrefsDataStore.edit { prefs ->
            prefs[Keys.LATEST_BRIEFING_TITLE] = title.take(120)
            prefs[Keys.LATEST_BRIEFING_BODY] = body.take(800)
            prefs[Keys.LATEST_BRIEFING_TS] = timestamp
            prefs[Keys.BRIEFING_DISMISSED] = false
        }
    }

    suspend fun dismissBriefing(dismissed: Boolean) {
        context.ultronPrefsDataStore.edit { prefs ->
            prefs[Keys.BRIEFING_DISMISSED] = dismissed
        }
    }

    suspend fun updateWidgetStatus(
        torchActive: Boolean? = null,
        emergencyStop: Boolean? = null
    ) {
        context.ultronPrefsDataStore.edit { prefs ->
            if (torchActive != null) prefs[Keys.WIDGET_TORCH_ACTIVE] = torchActive
            if (emergencyStop != null) prefs[Keys.WIDGET_EMERGENCY_STOP] = emergencyStop
        }
    }

    suspend fun updateKeyVersion(version: Int, rotatedAt: Long) {
        context.ultronPrefsDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_KEY_VERSION] = version.coerceAtLeast(1)
            prefs[Keys.LAST_KEY_ROTATION_AT] = rotatedAt
        }
    }

    suspend fun reencryptPatIfNeeded() {
        context.ultronPrefsDataStore.edit { prefs ->
            val currentEnc = prefs[Keys.ENCRYPTED_GITHUB_PAT]
            if (!currentEnc.isNullOrBlank() && cryptoManager.needsReencryption(currentEnc)) {
                val plain = runCatching { cryptoManager.decrypt(currentEnc) }.getOrNull()
                if (!plain.isNullOrBlank()) {
                    prefs[Keys.ENCRYPTED_GITHUB_PAT] = cryptoManager.encrypt(plain)
                }
            }
        }
    }
}
