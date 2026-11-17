package com.example.security

import java.net.InetAddress
import java.net.URI

sealed interface ToolValidationResult {
    data class Valid(val arg1: String, val arg2: String) : ToolValidationResult
    data class Invalid(val reason: String) : ToolValidationResult
}

/**
 * Centralized zero-trust validator for registered AI tools, user/LLM parameters,
 * URLs (anti-SSRF), and prompt-injection isolation boundaries.
 */
object ToolInputValidator {

    val REGISTERED_TOOLS: Set<String> = setOf(
        "getBatteryStatus",
        "getNetworkStatus",
        "toggleFlashlight",
        "setVolume",
        "setBrightness",
        "launchApp",
        "controlMedia",
        "openSystemSettings",
        "readNotifications",
        "performAccessibilityAction",
        "saveMemory",
        "searchMemories",
        "webResearch"
    )

    private val VALID_STREAMS = setOf("media", "ring", "alarm", "notification")
    private val VALID_MEDIA_COMMANDS = setOf("play_pause", "next", "previous", "stop")
    private val VALID_SETTINGS_SECTIONS = setOf(
        "wifi", "bluetooth", "notifications", "accessibility", "security", "display", "battery", "settings"
    )
    private val VALID_ACCESSIBILITY_ACTIONS = setOf(
        "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "SCREEN_SUMMARY"
    )
    private val CONTROL_CHAR_REGEX = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    fun sanitizePlainText(input: String, maxLength: Int = 500): String =
        input.replace(CONTROL_CHAR_REGEX, "").trim().take(maxLength)

    fun wrapUntrustedContext(label: String, rawText: String): String {
        val clean = sanitizePlainText(rawText, 4000)
            .replace("</untrusted_", "<\\/untrusted_")
        return "<untrusted_$label>\n$clean\n</untrusted_$label>"
    }

    fun validateToolArguments(toolName: String, rawArg1: String, rawArg2: String): ToolValidationResult {
        if (toolName !in REGISTERED_TOOLS) {
            return ToolValidationResult.Invalid("Unregistered tool '$toolName' rejected by ToolInputValidator.")
        }
        val a1 = sanitizePlainText(rawArg1, 600)
        val a2 = sanitizePlainText(rawArg2, 3000)

        return when (toolName) {
            "getBatteryStatus", "getNetworkStatus", "readNotifications" ->
                ToolValidationResult.Valid("", "")

            "toggleFlashlight" -> {
                val normalized = a1.lowercase()
                if (normalized !in setOf("true", "false", "on", "off")) {
                    ToolValidationResult.Invalid("Invalid flashlight state '$a1'. Expected true/false/on/off.")
                } else {
                    ToolValidationResult.Valid(normalized, "")
                }
            }

            "setVolume" -> {
                val stream = a1.lowercase().ifBlank { "media" }
                val numericCandidate = a2.toIntOrNull() ?: a1.toIntOrNull()
                    ?: return ToolValidationResult.Invalid("Volume percentage must be an integer 0..100.")
                val finalStream = if (a1.toIntOrNull() != null) "media" else stream
                if (finalStream !in VALID_STREAMS) {
                    return ToolValidationResult.Invalid("Unsupported audio stream '$finalStream'.")
                }
                if (numericCandidate !in 0..100) {
                    return ToolValidationResult.Invalid("Volume percentage $numericCandidate is outside allowed range 0..100.")
                }
                ToolValidationResult.Valid(finalStream, numericCandidate.toString())
            }

            "setBrightness" -> {
                val percent = a1.toIntOrNull()
                    ?: return ToolValidationResult.Invalid("Brightness percentage must be an integer 5..100.")
                if (percent !in 5..100) {
                    return ToolValidationResult.Invalid("Brightness $percent is outside allowed range 5..100.")
                }
                ToolValidationResult.Valid(percent.toString(), "")
            }

            "launchApp" -> {
                if (a1.isBlank() || a1.length > 100) {
                    return ToolValidationResult.Invalid("Application query must be 1..100 characters.")
                }
                if (a1.any { it in setOf(';', '&', '|', '`', '$', '<', '>') }) {
                    return ToolValidationResult.Invalid("Application query contains disallowed characters.")
                }
                ToolValidationResult.Valid(a1, "")
            }

            "controlMedia" -> {
                val cmd = a1.lowercase().ifBlank { "play_pause" }
                if (cmd !in VALID_MEDIA_COMMANDS) {
                    ToolValidationResult.Invalid("Unsupported media command '$cmd'.")
                } else {
                    ToolValidationResult.Valid(cmd, "")
                }
            }

            "openSystemSettings" -> {
                val sec = a1.lowercase().ifBlank { "settings" }
                if (sec !in VALID_SETTINGS_SECTIONS) {
                    ToolValidationResult.Invalid("Unsupported system settings section '$sec'.")
                } else {
                    ToolValidationResult.Valid(sec, "")
                }
            }

            "performAccessibilityAction" -> {
                val action = a1.uppercase().ifBlank { "SCREEN_SUMMARY" }
                if (action !in VALID_ACCESSIBILITY_ACTIONS) {
                    ToolValidationResult.Invalid("Unsupported accessibility action '$action'.")
                } else {
                    ToolValidationResult.Valid(action, "")
                }
            }

            "saveMemory" -> {
                val title = a1.take(160)
                val content = a2.ifBlank { a1 }.take(3000)
                if (content.isBlank()) {
                    ToolValidationResult.Invalid("Cannot persist empty memory content.")
                } else {
                    ToolValidationResult.Valid(title.ifBlank { "Agent Memory" }, content)
                }
            }

            "searchMemories" -> {
                if (a1.isBlank()) {
                    ToolValidationResult.Invalid("Memory search query cannot be blank.")
                } else {
                    ToolValidationResult.Valid(a1.take(240), "")
                }
            }

            "webResearch" -> {
                if (a1.isBlank()) {
                    ToolValidationResult.Invalid("Web research query cannot be blank.")
                } else {
                    ToolValidationResult.Valid(a1.take(360), "")
                }
            }

            else -> ToolValidationResult.Invalid("Tool '$toolName' validation rule missing.")
        }
    }

    fun validatePublicHttpUrl(rawUrl: String): Result<String> {
        val trimmed = sanitizePlainText(rawUrl, 1000)
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("URL cannot be blank."))
        val normalized = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else if (trimmed.contains("://")) {
            return Result.failure(IllegalArgumentException("Unsupported URL scheme. Only http and https are allowed."))
        } else {
            "https://$trimmed"
        }

        val uri = runCatching { URI(normalized) }.getOrElse {
            return Result.failure(IllegalArgumentException("Malformed URL."))
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result.failure(IllegalArgumentException("Disallowed URL scheme '$scheme'."))
        }
        val host = uri.host?.lowercase()?.trim()
            ?: return Result.failure(IllegalArgumentException("URL host is missing."))

        if (isPrivateOrLocalHost(host)) {
            return Result.failure(IllegalArgumentException("Blocked access to private or loopback host '$host'."))
        }
        return Result.success(normalized)
    }

    fun isPrivateOrLocalHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "0.0.0.0" || h.endsWith(".local") || h.endsWith(".internal")) {
            return true
        }
        if (h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("169.254.")) {
            return true
        }
        val parts = h.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
            val p0 = parts[0].toInt()
            val p1 = parts[1].toInt()
            if (p0 == 172 && p1 in 16..31) return true
            if (p0 == 127 || p0 == 0) return true
        }
        return runCatching {
            val addr = InetAddress.getByName(h)
            addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress || addr.isAnyLocalAddress
        }.getOrDefault(false)
    }
}
