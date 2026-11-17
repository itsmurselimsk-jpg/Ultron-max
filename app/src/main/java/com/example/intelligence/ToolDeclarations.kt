package com.example.intelligence

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool

object ToolDeclarations {

    val allAndroidAndMemoryTools: List<Tool> by lazy {
        listOf(
            Tool.functionDeclarations(
                listOf(
                    FunctionDeclaration(
                        name = "toggleFlashlight",
                        description = "Turn the device camera flashlight torch on or off.",
                        parameters = mapOf(
                            "enabled" to Schema.boolean("True to turn flashlight on, false to turn off")
                        )
                    ),
                    FunctionDeclaration(
                        name = "setVolume",
                        description = "Set Android device audio volume percentage for media, ring, or alarm stream.",
                        parameters = mapOf(
                            "stream" to Schema.enumeration(
                                values = listOf("media", "ring", "alarm", "notification"),
                                description = "Audio stream name"
                            ),
                            "percent" to Schema.integer("Volume percentage from 0 to 100")
                        )
                    ),
                    FunctionDeclaration(
                        name = "setBrightness",
                        description = "Set screen brightness percentage from 5 to 100.",
                        parameters = mapOf(
                            "percent" to Schema.integer("Brightness percentage from 5 to 100")
                        )
                    ),
                    FunctionDeclaration(
                        name = "getBatteryStatus",
                        description = "Read real-time battery level, charging state, temperature, and health.",
                        parameters = emptyMap()
                    ),
                    FunctionDeclaration(
                        name = "getNetworkStatus",
                        description = "Read real-time network transport (Wi-Fi/Cellular/VPN), link speeds, and connectivity state.",
                        parameters = emptyMap()
                    ),
                    FunctionDeclaration(
                        name = "launchApp",
                        description = "Launch an installed Android application by app name or package ID.",
                        parameters = mapOf(
                            "appQuery" to Schema.string("Application display name or package name")
                        )
                    ),
                    FunctionDeclaration(
                        name = "controlMedia",
                        description = "Send a system media playback command.",
                        parameters = mapOf(
                            "command" to Schema.enumeration(
                                values = listOf("play_pause", "next", "previous", "stop"),
                                description = "Media transport command"
                            )
                        )
                    ),
                    FunctionDeclaration(
                        name = "openSystemSettings",
                        description = "Open an Android Settings screen.",
                        parameters = mapOf(
                            "section" to Schema.enumeration(
                                values = listOf("wifi", "bluetooth", "notifications", "accessibility", "security", "display", "battery"),
                                description = "Target Android Settings page"
                            )
                        )
                    ),
                    FunctionDeclaration(
                        name = "saveMemory",
                        description = "Encrypt and persist a fact, user preference, or instruction into Ultron Omega long-term memory.",
                        parameters = mapOf(
                            "category" to Schema.enumeration(
                                values = listOf("FACT", "PREFERENCE", "EPISODIC", "INSTRUCTION"),
                                description = "Memory category"
                            ),
                            "title" to Schema.string("Concise memory title"),
                            "content" to Schema.string("Detailed memory text to encrypt and store")
                        )
                    ),
                    FunctionDeclaration(
                        name = "searchMemories",
                        description = "Semantically search Ultron Omega encrypted long-term memories.",
                        parameters = mapOf(
                            "query" to Schema.string("Search query")
                        )
                    ),
                    FunctionDeclaration(
                        name = "readNotifications",
                        description = "Read recent notifications captured by Ultron Notification Assistant if enabled.",
                        parameters = emptyMap()
                    ),
                    FunctionDeclaration(
                        name = "performAccessibilityAction",
                        description = "Perform an approved accessibility global action or read screen summary when Accessibility Service is enabled.",
                        parameters = mapOf(
                            "action" to Schema.enumeration(
                                values = listOf("BACK", "HOME", "RECENTS", "NOTIFICATIONS", "SCREEN_SUMMARY"),
                                description = "Approved accessibility action"
                            )
                        )
                    )
                )
            )
        )
    }
}
