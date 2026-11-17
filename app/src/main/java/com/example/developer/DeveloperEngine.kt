package com.example.developer

import android.util.Base64
import com.example.data.remote.GitHubApiService
import com.example.data.remote.GitHubGistFile
import com.example.data.remote.GitHubGistRequest
import com.example.data.remote.GitHubIssueDto
import com.example.data.remote.GitHubRepoDto
import com.example.intelligence.IntelligenceEngine
import com.example.intelligence.ModelTier
import com.example.security.SecureCredentialStore
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

enum class DevTaskMode(val label: String) {
    CODE_GEN("Code Generation"),
    BUILD_DIAGNOSIS("Build Error Diagnosis"),
    CODE_ANALYSIS("Project / Code Analysis"),
    TEST_GEN("Robolectric & Unit Tests")
}

class DeveloperEngine(
    private val gitHubApi: GitHubApiService,
    private val credentialStore: SecureCredentialStore,
    private val intelligenceEngine: IntelligenceEngine
) {
    private val argonModel by lazy {
        Firebase.ai.generativeModel(modelName = ModelTier.ARGON.modelId)
    }

    suspend fun runDevTask(mode: DevTaskMode, input: String): Result<String> {
        val systemPrefix = when (mode) {
            DevTaskMode.CODE_GEN -> "Act as a Principal Android Kotlin & Jetpack Compose engineer. Generate clean, production-grade code with no placeholders:"
            DevTaskMode.BUILD_DIAGNOSIS -> "Diagnose this Gradle/Kotlin/Android build error or stack trace. Identify exact root cause, affected dependency/file, and provide the precise fix:"
            DevTaskMode.CODE_ANALYSIS -> "Analyze this code or architecture snippet for concurrency bugs, security flaws, memory leaks, and performance optimizations:"
            DevTaskMode.TEST_GEN -> "Generate deterministic JUnit4 + Robolectric unit tests covering edge cases and critical user journeys for:"
        }
        return try {
            val response = withTimeout(60_000L) {
                argonModel.generateContent("$systemPrefix\n\n$input")
            }
            val text = response.text?.takeIf { it.isNotBlank() }
                ?: error("Developer model returned empty output.")
            Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMyRepos(): Result<List<GitHubRepoDto>> = runCatching {
        val pat = credentialStore.getGitHubPat()
            ?: error("GitHub Personal Access Token not configured. Add it in Security Command.")
        gitHubApi.getUserRepos("Bearer $pat")
    }

    suspend fun fetchRepoIssues(owner: String, repo: String): Result<List<GitHubIssueDto>> = runCatching {
        val pat = credentialStore.getGitHubPat()
            ?: error("GitHub Personal Access Token not configured.")
        gitHubApi.getRepoIssues("Bearer $pat", owner, repo)
    }

    suspend fun fetchRepoFileText(owner: String, repo: String, path: String): Result<String> = runCatching {
        val pat = credentialStore.getGitHubPat()
            ?: error("GitHub Personal Access Token not configured.")
        val dto = gitHubApi.getRepoFile("Bearer $pat", owner, repo, path)
        val raw = dto.content ?: error("No file content returned for $path")
        if (dto.encoding == "base64") {
            val clean = raw.replace("\n", "").replace("\r", "")
            String(Base64.decode(clean, Base64.DEFAULT), Charsets.UTF_8)
        } else {
            raw
        }
    }

    suspend fun createPrivateGist(fileName: String, content: String, description: String): Result<String> = runCatching {
        val pat = credentialStore.getGitHubPat()
            ?: error("GitHub Personal Access Token not configured.")
        val resp = gitHubApi.createGist(
            authHeader = "Bearer $pat",
            body = GitHubGistRequest(
                description = description,
                isPublicGist = false,
                files = mapOf(fileName to GitHubGistFile(content))
            )
        )
        resp.htmlUrl
    }
}
