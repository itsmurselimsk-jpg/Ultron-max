package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed AES-256-GCM encryption engine using AndroidKeyStore with
 * versioned key aliases supporting safe zero-loss key rotation (ENC_V1 & ENC_V2).
 * In local JVM/Robolectric tests where AndroidKeyStore is absent, derives an
 * isolated deterministic key per version alias so rotation can be unit-tested cleanly.
 */
class CryptoManager(
    private val keyAliasPrefix: String = DEFAULT_KEY_ALIAS_PREFIX,
    initialKeyVersion: Int = 1
) {
    @Volatile
    private var activeKeyVersion: Int = initialKeyVersion.coerceAtLeast(1)

    val isHardwareKeystoreActive: Boolean
        get() = runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            true
        }.getOrDefault(false)

    fun getActiveKeyVersion(): Int = activeKeyVersion

    fun setActiveKeyVersion(version: Int) {
        activeKeyVersion = version.coerceAtLeast(1)
    }

    @Synchronized
    fun rotateKey(): Int {
        val nextVersion = activeKeyVersion + 1
        getOrCreateKeyForVersion(nextVersion)
        activeKeyVersion = nextVersion
        return nextVersion
    }

    private fun aliasForVersion(version: Int): String =
        if (version <= 1) "${keyAliasPrefix}_v1" else "${keyAliasPrefix}_v$version"

    private fun fallbackKeyForVersion(version: Int): SecretKey {
        val alias = aliasForVersion(version)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("ultron_omega_jvm_test_key_$alias".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun getOrCreateKeyForVersion(version: Int): SecretKey {
        val alias = aliasForVersion(version)
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingKey = keyStore.getKey(alias, null) as? SecretKey
            if (existingKey != null) return existingKey

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore unavailable for $alias, using fallback AES key: ${e.message}")
            fallbackKeyForVersion(version)
        }
    }

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val ver = activeKeyVersion
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeyForVersion(ver))
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
        val b64 = Base64.encodeToString(combined, Base64.NO_WRAP)
        return if (ver <= 1) {
            "$PREFIX_V1$b64"
        } else {
            "$PREFIX_V2$ver:$b64"
        }
    }

    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return when {
            encoded.startsWith(PREFIX_V2) -> {
                val remainder = encoded.removePrefix(PREFIX_V2)
                val sepIdx = remainder.indexOf(':')
                require(sepIdx > 0) { "Corrupt ENC_V2 header" }
                val version = remainder.substring(0, sepIdx).toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid ENC_V2 key version")
                val b64 = remainder.substring(sepIdx + 1)
                decryptWithVersion(version, b64)
            }
            encoded.startsWith(PREFIX_V1) -> {
                val b64 = encoded.removePrefix(PREFIX_V1)
                decryptWithVersion(1, b64)
            }
            else -> encoded
        }
    }

    fun needsReencryption(encoded: String): Boolean {
        if (encoded.isBlank()) return false
        val payloadVersion = extractKeyVersion(encoded) ?: return true
        return payloadVersion != activeKeyVersion
    }

    fun extractKeyVersion(encoded: String): Int? = when {
        encoded.startsWith(PREFIX_V2) -> {
            val remainder = encoded.removePrefix(PREFIX_V2)
            val sep = remainder.indexOf(':')
            if (sep > 0) remainder.substring(0, sep).toIntOrNull() else null
        }
        encoded.startsWith(PREFIX_V1) -> 1
        else -> null
    }

    private fun decryptWithVersion(version: Int, b64: String): String {
        val payload = Base64.decode(b64, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Empty encrypted payload" }
        val ivLen = payload[0].toInt() and 0xFF
        require(ivLen in 12..16 && payload.size > 1 + ivLen) { "Corrupt encrypted payload" }
        val iv = payload.copyOfRange(1, 1 + ivLen)
        val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeyForVersion(version), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "CryptoManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS_PREFIX = "ultron_omega_master_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFIX_V1 = "ENC_V1:"
        private const val PREFIX_V2 = "ENC_V2:"
    }
}
