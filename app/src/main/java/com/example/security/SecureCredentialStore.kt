package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val accessibilityConsentAccepted: Boolean = false
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
            accessibilityConsentAccepted = prefs[Keys.ACCESSIBILITY_CONSENT] ?: false
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
}
