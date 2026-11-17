package com.example.intelligence

enum class ModelTier(
    val modelId: String,
    val label: String,
    val description: String
) {
    LITE(
        modelId = "gemini-3.5-flash-lite",
        label = "Omega Lite",
        description = "Ultra-fast triage, intent routing & low-latency classification"
    ),
    FLASH(
        modelId = "gemini-3.8-flash",
        label = "Omega Flash",
        description = "Default streaming intelligence, multimodal vision & Google Search grounding"
    ),
    ARGON(
        modelId = "gemini-4-argon",
        label = "Omega Argon",
        description = "Deep multi-step reasoning, autonomous planning, code & diagnostics"
    )
}

object ModelRouter {

    fun selectModel(prompt: String, preferredTier: String = "AUTO", hasMedia: Boolean = false): ModelTier {
        when (preferredTier.uppercase()) {
            "LITE" -> return if (hasMedia) ModelTier.FLASH else ModelTier.LITE
            "FLASH" -> return ModelTier.FLASH
            "ARGON" -> return ModelTier.ARGON
        }

        if (hasMedia) return ModelTier.FLASH

        val lower = prompt.lowercase()
        val deepSignals = listOf(
            "architect", "debug", "stacktrace", "exception", "compile", "gradle",
            "refactor", "algorithm", "proof", "multi-step", "agent", "plan",
            "generate code", "write test", "kotlin", "security audit", "analyze codebase"
        )
        if (deepSignals.any { lower.contains(it) } || prompt.length > 500) {
            return ModelTier.ARGON
        }

        val quickSignals = listOf("status", "battery", "volume", "flashlight", "torch", "time", "hello", "hi")
        if (prompt.length < 48 && quickSignals.any { lower.contains(it) }) {
            return ModelTier.LITE
        }

        return ModelTier.FLASH
    }
}
