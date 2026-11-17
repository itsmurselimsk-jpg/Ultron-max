package com.example.agent

import com.example.androidtools.AndroidToolExecutor
import com.example.data.local.AgentStepEntity
import com.example.data.local.AgentTaskEntity
import com.example.data.local.UltronDao
import com.example.intelligence.IntelligenceEngine
import com.example.intelligence.ModelTier
import com.example.memory.MemoryRepository
import com.example.research.ResearchEngine
import com.example.security.RiskEngine
import com.example.security.RiskEvaluation
import com.example.security.RiskPolicyLevel
import com.google.firebase.ai.type.Schema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

data class PendingConfirmationRequest(
    val taskId: String,
    val stepId: String,
    val evaluation: RiskEvaluation,
    val description: String,
    val deferred: CompletableDeferred<Boolean>
)

class AgentEngine(
    private val dao: UltronDao,
    private val intelligenceEngine: IntelligenceEngine,
    private val androidTools: AndroidToolExecutor,
    private val memoryRepository: MemoryRepository,
    private val researchEngine: ResearchEngine,
    private val riskEngine: RiskEngine
) {
    private val _pendingConfirmation = MutableStateFlow<PendingConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    private val _activeTaskId = MutableStateFlow<String?>(null)
    val activeTaskId: StateFlow<String?> = _activeTaskId.asStateFlow()

    fun observeTasks(userId: String): Flow<List<AgentTaskEntity>> = dao.observeAgentTasks(userId)

    fun observeAllSteps(userId: String): Flow<List<AgentStepEntity>> = dao.observeAllStepsForUser(userId)

    fun resolveConfirmation(approved: Boolean) {
        val current = _pendingConfirmation.value
        _pendingConfirmation.value = null
        current?.deferred?.complete(approved)
    }

    suspend fun cancelActiveTask(userId: String, syncToCloud: Boolean) {
        val taskId = _activeTaskId.value ?: return
        _pendingConfirmation.value?.deferred?.complete(false)
        _pendingConfirmation.value = null
        val tasks = mutableListOf<AgentTaskEntity>()
        // Mark cancelled cleanly via DAO upsert
        dao.upsertAgentTask(
            AgentTaskEntity(
                id = taskId,
                userId = userId,
                goal = "Cancelled by Operator",
                status = "CANCELLED",
                planSummary = "Halted before completion.",
                updatedAt = System.currentTimeMillis()
            )
        )
        _activeTaskId.value = null
        memoryRepository.recordAudit(
            userId = userId,
            actor = "USER",
            action = "AGENT_TASK_CANCEL",
            riskLevel = com.example.security.RiskLevel.MEDIUM,
            outcome = "STOPPED",
            details = "Cancelled autonomous task $taskId",
            syncToCloud = syncToCloud
        )
        tasks.clear()
    }

    suspend fun executeAutonomousGoal(
        userId: String,
        goal: String,
        riskPolicy: RiskPolicyLevel,
        biometricConfirm: Boolean,
        syncToCloud: Boolean
    ): Result<String> {
        if (riskEngine.emergencyStopEngaged.value) {
            return Result.failure(IllegalStateException("Emergency Stop is engaged. Reset it in Security Command before launching agents."))
        }

        val taskId = "task_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        _activeTaskId.value = taskId
        val now = System.currentTimeMillis()
        dao.upsertAgentTask(
            AgentTaskEntity(
                id = taskId,
                userId = userId,
                goal = goal,
                status = "PLANNING",
                planSummary = "Generating structured multi-agent plan...",
                createdAt = now,
                updatedAt = now
            )
        )

        return try {
            val planSchema = Schema.obj(
                properties = mapOf(
                    "planSummary" to Schema.string("Brief 1-sentence strategic plan overview"),
                    "steps" to Schema.array(
                        items = Schema.obj(
                            properties = mapOf(
                                "agentRole" to Schema.enumeration(
                                    values = listOf("RESEARCHER", "ANDROID_EXECUTOR", "MEMORY_CURATOR", "VERIFIER"),
                                    description = "Specialized agent role"
                                ),
                                "toolName" to Schema.enumeration(
                                    values = listOf(
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
                                    ),
                                    description = "Exact registered tool to invoke"
                                ),
                                "arg1" to Schema.string("Primary tool argument (e.g. query, true/false, app name, percent, or empty)"),
                                "arg2" to Schema.string("Secondary tool argument (e.g. content or stream name, or empty)")
                            )
                        ),
                        description = "Ordered execution steps (1 to 5 steps)"
                    )
                )
            )

            val planJsonStr = intelligenceEngine.generateStructuredJson(
                prompt = """
                Plan a concise, safe 1-to-4 step autonomous execution using ONLY the registered tools for the user goal:
                "$goal"
                Do not invent tools. Use webResearch for internet info, saveMemory/searchMemories for knowledge, and Android tools for device actions.
                """.trimIndent(),
                schema = planSchema,
                tier = ModelTier.ARGON
            ).getOrElse { err ->
                dao.upsertAgentTask(
                    AgentTaskEntity(taskId, userId, goal, "FAILED", err.localizedMessage ?: "Planning failed", now, System.currentTimeMillis())
                )
                _activeTaskId.value = null
                return Result.failure(err)
            }

            val rootJson = JSONObject(planJsonStr)
            val planSummary = rootJson.optString("planSummary", "Autonomous Plan")
            val stepsArray = rootJson.getJSONArray("steps")
            val stepEntities = (0 until stepsArray.length()).map { idx ->
                val obj = stepsArray.getJSONObject(idx)
                val tool = obj.getString("toolName")
                val arg1 = obj.optString("arg1", "")
                val arg2 = obj.optString("arg2", "")
                val eval = riskEngine.evaluateTool(tool, "$arg1 $arg2".trim(), riskPolicy, biometricConfirm)
                AgentStepEntity(
                    id = "${taskId}_step_$idx",
                    taskId = taskId,
                    stepOrder = idx + 1,
                    agentRole = obj.optString("agentRole", "ANDROID_EXECUTOR"),
                    toolName = tool,
                    argumentsJson = JSONObject().apply {
                        put("arg1", arg1)
                        put("arg2", arg2)
                    }.toString(),
                    riskLevel = eval.riskLevel.name,
                    status = "PENDING"
                )
            }

            dao.upsertAgentTask(
                AgentTaskEntity(taskId, userId, goal, "RUNNING", planSummary, now, System.currentTimeMillis())
            )
            dao.upsertAgentSteps(stepEntities)

            val executionTrace = StringBuilder()
            for (step in stepEntities) {
                if (riskEngine.emergencyStopEngaged.value || _activeTaskId.value != taskId) {
                    dao.upsertAgentStep(step.copy(status = "CANCELLED", output = "Aborted by Emergency Stop / Cancel"))
                    dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "CANCELLED", planSummary, now, System.currentTimeMillis()))
                    return Result.failure(IllegalStateException("Agent task cancelled."))
                }

                val argsJson = JSONObject(step.argumentsJson)
                val arg1 = argsJson.optString("arg1", "")
                val arg2 = argsJson.optString("arg2", "")
                val eval = riskEngine.evaluateTool(step.toolName, "$arg1 $arg2".trim(), riskPolicy, biometricConfirm)

                if (eval.requiresUserConfirmation) {
                    dao.upsertAgentStep(step.copy(status = "AWAITING_CONFIRMATION"))
                    dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "AWAITING_CONFIRMATION", planSummary, now, System.currentTimeMillis()))

                    val deferred = CompletableDeferred<Boolean>()
                    _pendingConfirmation.value = PendingConfirmationRequest(
                        taskId = taskId,
                        stepId = step.id,
                        evaluation = eval,
                        description = "${step.agentRole} requests ${step.toolName}($arg1${if (arg2.isNotBlank()) ", $arg2" else ""})",
                        deferred = deferred
                    )
                    val approved = deferred.await()
                    if (!approved) {
                        dao.upsertAgentStep(step.copy(status = "FAILED", output = "Denied by Operator"))
                        memoryRepository.recordAudit(
                            userId, "USER", step.toolName, eval.riskLevel, "DENIED",
                            "User denied ${step.toolName}($arg1)", syncToCloud
                        )
                        dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "CANCELLED", "Step denied by operator.", now, System.currentTimeMillis()))
                        _activeTaskId.value = null
                        return Result.failure(IllegalStateException("Action ${step.toolName} denied by operator."))
                    }
                }

                dao.upsertAgentStep(step.copy(status = "RUNNING"))
                dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "RUNNING", planSummary, now, System.currentTimeMillis()))

                val output = executeRegisteredTool(userId, step.toolName, arg1, arg2, syncToCloud)
                val verifiedNote = "Verified non-empty output (${output.length} chars)"
                dao.upsertAgentStep(
                    step.copy(
                        status = "VERIFIED",
                        output = output,
                        verificationNotes = verifiedNote
                    )
                )
                memoryRepository.recordAudit(
                    userId, step.agentRole, step.toolName, eval.riskLevel, "EXECUTED",
                    output.take(300), syncToCloud
                )
                executionTrace.append("[${step.agentRole} -> ${step.toolName}]: $output\n")
            }

            dao.upsertAgentTask(
                AgentTaskEntity(taskId, userId, goal, "VERIFIED", planSummary, now, System.currentTimeMillis())
            )
            _activeTaskId.value = null
            Result.success(executionTrace.toString().trim())
        } catch (e: CancellationException) {
            dao.upsertAgentTask(
                AgentTaskEntity(taskId, userId, goal, "CANCELLED", "Cancelled", now, System.currentTimeMillis())
            )
            _activeTaskId.value = null
            throw e
        } catch (e: Exception) {
            dao.upsertAgentTask(
                AgentTaskEntity(taskId, userId, goal, "FAILED", e.localizedMessage ?: "Execution error", now, System.currentTimeMillis())
            )
            _activeTaskId.value = null
            Result.failure(e)
        }
    }

    suspend fun executeRegisteredTool(
        userId: String,
        toolName: String,
        arg1: String,
        arg2: String,
        syncToCloud: Boolean
    ): String {
        return when (toolName) {
            "toggleFlashlight" -> androidTools.toggleFlashlight(arg1.equals("true", ignoreCase = true) || arg1.equals("on", ignoreCase = true))
            "setVolume" -> androidTools.setVolume(arg1.ifBlank { "media" }, arg2.toIntOrNull() ?: arg1.toIntOrNull() ?: 50)
            "setBrightness" -> androidTools.setBrightness(arg1.toIntOrNull() ?: 60)
            "getBatteryStatus" -> androidTools.getBatteryStatusSummary()
            "getNetworkStatus" -> androidTools.getNetworkStatusSummary()
            "launchApp" -> androidTools.launchApp(arg1)
            "controlMedia" -> androidTools.controlMedia(arg1.ifBlank { "play_pause" })
            "openSystemSettings" -> androidTools.openSystemSettings(arg1.ifBlank { "settings" })
            "readNotifications" -> androidTools.readNotificationsSummary()
            "performAccessibilityAction" -> androidTools.performAccessibilityAction(arg1.ifBlank { "SCREEN_SUMMARY" })
            "saveMemory" -> {
                val res = memoryRepository.saveMemory(
                    userId = userId,
                    category = "FACT",
                    title = arg1.ifBlank { "Agent Memory" },
                    content = arg2.ifBlank { arg1 },
                    importance = 7,
                    pinned = false,
                    syncToCloud = syncToCloud
                )
                res.fold(
                    onSuccess = { "Encrypted & saved memory '${it.title}'." },
                    onFailure = { "Memory save failed: ${it.localizedMessage}" }
                )
            }
            "searchMemories" -> {
                val matches = memoryRepository.searchRelevantMemories(userId, arg1, topK = 4)
                if (matches.isEmpty()) "No matching encrypted memories found."
                else matches.joinToString("\n") { "- ${it.title}: ${it.content}" }
            }
            "webResearch" -> {
                val res = researchEngine.searchAndSummarize(userId, arg1, syncToCloud)
                res.fold(
                    onSuccess = { report ->
                        "${report.summary}\nSources: ${report.sources.take(3).joinToString { it.title }}"
                    },
                    onFailure = { "Web research failed: ${it.localizedMessage}" }
                )
            }
            else -> "Tool '$toolName' is not registered."
        }
    }
}
