package com.example.agent

import com.example.androidtools.AndroidToolExecutor
import com.example.androidtools.ToolExecutionResult
import com.example.data.local.AgentStepEntity
import com.example.data.local.AgentTaskEntity
import com.example.data.local.UltronDao
import com.example.intelligence.IntelligenceEngine
import com.example.intelligence.ModelTier
import com.example.memory.MemoryRepository
import com.example.research.ResearchEngine
import com.example.security.RiskEngine
import com.example.security.RiskEvaluation
import com.example.security.RiskLevel
import com.example.security.RiskPolicyLevel
import com.example.security.ToolInputValidator
import com.example.security.ToolValidationResult
import com.google.firebase.ai.type.Schema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID

data class PendingConfirmationRequest(
    val taskId: String,
    val stepId: String,
    val evaluation: RiskEvaluation,
    val description: String,
    val deferred: CompletableDeferred<Boolean>,
    val expiresAtEpochMs: Long = System.currentTimeMillis() + AgentEngine.DEFAULT_CONFIRMATION_TIMEOUT_MS
)

class AgentEngine(
    private val dao: UltronDao,
    private val intelligenceEngine: IntelligenceEngine,
    private val androidTools: AndroidToolExecutor,
    private val memoryRepository: MemoryRepository,
    private val researchEngine: ResearchEngine,
    private val riskEngine: RiskEngine,
    private val confirmationTimeoutMs: Long = DEFAULT_CONFIRMATION_TIMEOUT_MS,
    private val planProvider: (suspend (String) -> Result<String>)? = null
) {
    private val executionMutex = Mutex()

    @Volatile
    private var activeExecutionJob: Job? = null

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    private val _activeTaskId = MutableStateFlow<String?>(null)
    val activeTaskId: StateFlow<String?> = _activeTaskId.asStateFlow()

    fun observeTasks(userId: String): Flow<List<AgentTaskEntity>> = dao.observeAgentTasks(userId)

    fun observeAllSteps(userId: String): Flow<List<AgentStepEntity>> = dao.observeAllStepsForUser(userId)

    /**
     * Resolves an active confirmation request only if taskId/stepId (when provided) match
     * the currently active confirmation, preventing stale approvals from crossing tasks.
     */
    fun resolveConfirmation(
        approved: Boolean,
        expectedTaskId: String? = null,
        expectedStepId: String? = null
    ): Boolean {
        val current = _pendingConfirmation.value ?: return false
        if (expectedTaskId != null && current.taskId != expectedTaskId) return false
        if (expectedStepId != null && current.stepId != expectedStepId) return false
        _pendingConfirmation.value = null
        current.deferred.complete(approved)
        return true
    }

    suspend fun cancelActiveTask(userId: String, syncToCloud: Boolean) {
        val taskId = _activeTaskId.value ?: return
        _pendingConfirmation.value?.let { req ->
            if (req.taskId == taskId) {
                _pendingConfirmation.value = null
                req.deferred.complete(false)
            }
        }
        val existingTask = dao.getAgentTaskById(taskId)
        val now = System.currentTimeMillis()
        dao.cancelIncompleteSteps(taskId, "Cancelled by Operator")
        dao.upsertAgentTask(
            AgentTaskEntity(
                id = taskId,
                userId = userId,
                goal = existingTask?.goal ?: "Cancelled Mission",
                status = "CANCELLED",
                planSummary = "Halted by operator before completion.",
                createdAt = existingTask?.createdAt ?: now,
                updatedAt = now
            )
        )
        _activeTaskId.value = null
        activeExecutionJob?.cancel()
        activeExecutionJob = null
        memoryRepository.recordAudit(
            userId = userId,
            actor = "USER",
            action = "AGENT_TASK_CANCEL",
            riskLevel = RiskLevel.MEDIUM,
            outcome = "STOPPED",
            details = "Cancelled autonomous task $taskId (${existingTask?.goal?.take(80).orEmpty()})",
            syncToCloud = syncToCloud
        )
    }

    suspend fun executeAutonomousGoal(
        userId: String,
        goal: String,
        riskPolicy: RiskPolicyLevel,
        biometricConfirm: Boolean,
        syncToCloud: Boolean
    ): Result<String> {
        if (riskEngine.emergencyStopEngaged.value) {
            memoryRepository.recordAudit(
                userId, "AGENT", "AGENT_LAUNCH_BLOCKED", RiskLevel.CRITICAL, "STOPPED",
                "Blocked autonomous goal while Emergency Stop is engaged", syncToCloud
            )
            return Result.failure(IllegalStateException("Emergency Stop is engaged. Reset it in Security Command before launching agents."))
        }

        if (!executionMutex.tryLock()) {
            val runningId = _activeTaskId.value ?: "active"
            return Result.failure(IllegalStateException("Autonomous mission '$runningId' is already running. Cancel or await completion before starting another."))
        }

        val taskId = "task_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val now = System.currentTimeMillis()
        activeExecutionJob = currentCoroutineContext()[Job]
        _activeTaskId.value = taskId

        return try {
            currentCoroutineContext().ensureActive()
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
                                    values = ToolInputValidator.REGISTERED_TOOLS.toList(),
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

            val sanitizedGoal = ToolInputValidator.sanitizePlainText(goal, 500)
            val planResult = if (planProvider != null) {
                planProvider.invoke(sanitizedGoal)
            } else {
                intelligenceEngine.generateStructuredJson(
                    prompt = """
                    Plan a concise, safe 1-to-4 step autonomous execution using ONLY the registered tools for the user goal below.
                    ${ToolInputValidator.wrapUntrustedContext("operator_goal", sanitizedGoal)}
                    Do not invent tools. Use webResearch for internet info, saveMemory/searchMemories for knowledge, and Android tools for device actions.
                    """.trimIndent(),
                    schema = planSchema,
                    tier = ModelTier.ARGON
                )
            }
            val planJsonStr = planResult.getOrElse { err ->
                dao.upsertAgentTask(
                    AgentTaskEntity(taskId, userId, goal, "FAILED", err.localizedMessage ?: "Planning failed", now, System.currentTimeMillis())
                )
                return Result.failure(err)
            }

            currentCoroutineContext().ensureActive()
            if (isHalted(taskId)) {
                dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "CANCELLED", "Halted after planning.", now, System.currentTimeMillis()))
                return Result.failure(IllegalStateException("Agent task cancelled."))
            }

            val rootJson = JSONObject(planJsonStr)
            val planSummary = rootJson.optString("planSummary", "Autonomous Plan")
            val stepsArray = rootJson.getJSONArray("steps")
            val stepEntities = (0 until stepsArray.length().coerceAtMost(6)).map { idx ->
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
                // 1. Pre-step cancellation & E-STOP enforcement
                currentCoroutineContext().ensureActive()
                if (isHalted(taskId)) {
                    dao.upsertAgentStep(step.copy(status = "CANCELLED", output = "Aborted before execution", verificationNotes = "Cancelled / Emergency Stop"))
                    dao.cancelIncompleteSteps(taskId, "Aborted by Emergency Stop / Cancel")
                    dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "CANCELLED", planSummary, now, System.currentTimeMillis()))
                    return Result.failure(IllegalStateException("Agent task cancelled."))
                }

                val argsJson = JSONObject(step.argumentsJson)
                val rawArg1 = argsJson.optString("arg1", "")
                val rawArg2 = argsJson.optString("arg2", "")

                // 2. Centralized input validation
                val validArgs = when (val validation = ToolInputValidator.validateToolArguments(step.toolName, rawArg1, rawArg2)) {
                    is ToolValidationResult.Invalid -> {
                        dao.upsertAgentStep(
                            step.copy(
                                status = "FAILED",
                                output = validation.reason,
                                verificationNotes = "Blocked by ToolInputValidator"
                            )
                        )
                        dao.cancelIncompleteSteps(taskId, "Halted due to invalid step arguments")
                        memoryRepository.recordAudit(
                            userId, "AGENT", step.toolName, RiskLevel.HIGH, "BLOCKED",
                            validation.reason, syncToCloud
                        )
                        dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "FAILED", validation.reason, now, System.currentTimeMillis()))
                        return Result.failure(IllegalArgumentException(validation.reason))
                    }
                    is ToolValidationResult.Valid -> validation
                }

                val eval = riskEngine.evaluateTool(step.toolName, "${validArgs.arg1} ${validArgs.arg2}".trim(), riskPolicy, biometricConfirm)

                // 3. Per-step confirmation with 45s timeout and stale-ID safety
                if (eval.requiresUserConfirmation) {
                    dao.upsertAgentStep(step.copy(status = "AWAITING_CONFIRMATION"))
                    dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "AWAITING_CONFIRMATION", planSummary, now, System.currentTimeMillis()))

                    val deferred = CompletableDeferred<Boolean>()
                    val request = PendingConfirmationRequest(
                        taskId = taskId,
                        stepId = step.id,
                        evaluation = eval,
                        description = "${step.agentRole} requests ${step.toolName}(${validArgs.arg1}${if (validArgs.arg2.isNotBlank()) ", ${validArgs.arg2}" else ""})",
                        deferred = deferred,
                        expiresAtEpochMs = System.currentTimeMillis() + confirmationTimeoutMs
                    )
                    _pendingConfirmation.value = request

                    val decision: Boolean? = withTimeoutOrNull(confirmationTimeoutMs) {
                        deferred.await()
                    }
                    if (_pendingConfirmation.value?.stepId == step.id) {
                        _pendingConfirmation.value = null
                    }

                    // Re-check E-STOP & cancellation immediately after awaiting confirmation
                    currentCoroutineContext().ensureActive()
                    if (isHalted(taskId)) {
                        deferred.cancel()
                        dao.upsertAgentStep(step.copy(status = "CANCELLED", output = "Aborted during confirmation", verificationNotes = "Emergency Stop / Cancel"))
                        dao.cancelIncompleteSteps(taskId, "Emergency Stop / Cancel")
                        dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "CANCELLED", "Aborted during confirmation.", now, System.currentTimeMillis()))
                        return Result.failure(IllegalStateException("Agent task cancelled."))
                    }

                    if (decision == null) {
                        deferred.cancel()
                        val timeoutMsg = "Confirmation timed out after ${confirmationTimeoutMs / 1000L}s; aborted for safety."
                        dao.upsertAgentStep(step.copy(status = "CANCELLED", output = timeoutMsg, verificationNotes = "Timed out"))
                        dao.cancelIncompleteSteps(taskId, "Confirmation timeout")
                        memoryRepository.recordAudit(
                            userId, "USER", step.toolName, eval.riskLevel, "DENIED",
                            "Confirmation timeout for ${step.toolName}", syncToCloud
                        )
                        dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "CANCELLED", timeoutMsg, now, System.currentTimeMillis()))
                        return Result.failure(IllegalStateException(timeoutMsg))
                    }

                    if (!decision) {
                        dao.upsertAgentStep(step.copy(status = "FAILED", output = "Denied by Operator", verificationNotes = "Operator denied action"))
                        dao.cancelIncompleteSteps(taskId, "Prior step denied by operator")
                        memoryRepository.recordAudit(
                            userId, "USER", step.toolName, eval.riskLevel, "DENIED",
                            "User denied ${step.toolName}(${validArgs.arg1})", syncToCloud
                        )
                        dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "CANCELLED", "Step denied by operator.", now, System.currentTimeMillis()))
                        return Result.failure(IllegalStateException("Action ${step.toolName} denied by operator."))
                    }

                    memoryRepository.recordAudit(
                        userId, "USER", step.toolName, eval.riskLevel, "APPROVED",
                        "Authorized ${step.toolName}(${validArgs.arg1})", syncToCloud
                    )
                }

                // Final E-STOP & cancellation check immediately before hardware/tool call
                currentCoroutineContext().ensureActive()
                if (isHalted(taskId)) {
                    dao.upsertAgentStep(step.copy(status = "CANCELLED", output = "Aborted before tool dispatch", verificationNotes = "Emergency Stop / Cancel"))
                    dao.cancelIncompleteSteps(taskId, "Emergency Stop / Cancel")
                    dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "CANCELLED", planSummary, now, System.currentTimeMillis()))
                    return Result.failure(IllegalStateException("Agent task cancelled."))
                }

                dao.upsertAgentStep(step.copy(status = "RUNNING"))
                dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "RUNNING", planSummary, now, System.currentTimeMillis()))

                val toolRes = executeRegisteredToolVerified(
                    userId = userId,
                    toolName = step.toolName,
                    arg1 = validArgs.arg1,
                    arg2 = validArgs.arg2,
                    syncToCloud = syncToCloud
                )

                // Post-tool cancellation & E-STOP check
                if (isHalted(taskId)) {
                    dao.upsertAgentStep(
                        step.copy(
                            status = "CANCELLED",
                            output = toolRes.output,
                            verificationNotes = "Task cancelled/E-STOP immediately after tool execution"
                        )
                    )
                    dao.cancelIncompleteSteps(taskId, "Aborted post-tool due to Cancel/E-STOP")
                    dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "CANCELLED", planSummary, now, System.currentTimeMillis()))
                    return Result.failure(IllegalStateException("Agent task halted after step."))
                }

                if (!toolRes.success) {
                    dao.upsertAgentStep(
                        step.copy(
                            status = "FAILED",
                            output = toolRes.output,
                            verificationNotes = toolRes.verificationNote
                        )
                    )
                    dao.cancelIncompleteSteps(taskId, "Halted because previous step failed")
                    memoryRepository.recordAudit(
                        userId, step.agentRole, step.toolName, eval.riskLevel, "BLOCKED",
                        "Tool failed: ${toolRes.output.take(260)}", syncToCloud
                    )
                    dao.upsertAgentTask(AgentTaskEntity(taskId, userId, goal, "FAILED", toolRes.output.take(200), now, System.currentTimeMillis()))
                    return Result.failure(IllegalStateException(toolRes.output))
                }

                dao.upsertAgentStep(
                    step.copy(
                        status = "VERIFIED",
                        output = toolRes.output,
                        verificationNotes = toolRes.verificationNote
                    )
                )
                memoryRepository.recordAudit(
                    userId, step.agentRole, step.toolName, eval.riskLevel, "EXECUTED",
                    toolRes.output.take(300), syncToCloud
                )
                executionTrace.append("[${step.agentRole} -> ${step.toolName}]: ${toolRes.output}\n")
            }

            currentCoroutineContext().ensureActive()
            dao.upsertAgentTask(
                AgentTaskEntity(taskId, userId, goal, "VERIFIED", planSummary, now, System.currentTimeMillis())
            )
            Result.success(executionTrace.toString().trim())
        } catch (e: CancellationException) {
            dao.cancelIncompleteSteps(taskId, "Coroutine cancelled")
            dao.upsertAgentTask(
                AgentTaskEntity(taskId, userId, goal, "CANCELLED", "Cancelled", now, System.currentTimeMillis())
            )
            throw e
        } catch (e: Exception) {
            dao.upsertAgentTask(
                AgentTaskEntity(taskId, userId, goal, "FAILED", e.localizedMessage ?: "Execution error", now, System.currentTimeMillis())
            )
            Result.failure(e)
        } finally {
            if (_pendingConfirmation.value?.taskId == taskId) {
                _pendingConfirmation.value?.deferred?.complete(false)
                _pendingConfirmation.value = null
            }
            if (_activeTaskId.value == taskId) {
                _activeTaskId.value = null
            }
            activeExecutionJob = null
            if (executionMutex.isLocked) {
                executionMutex.unlock()
            }
        }
    }

    private fun isHalted(taskId: String): Boolean =
        riskEngine.emergencyStopEngaged.value || _activeTaskId.value != taskId

    suspend fun executeRegisteredTool(
        userId: String,
        toolName: String,
        arg1: String,
        arg2: String,
        syncToCloud: Boolean
    ): String = executeRegisteredToolVerified(userId, toolName, arg1, arg2, syncToCloud).output

    suspend fun executeRegisteredToolVerified(
        userId: String,
        toolName: String,
        arg1: String,
        arg2: String,
        syncToCloud: Boolean
    ): ToolExecutionResult {
        if (riskEngine.emergencyStopEngaged.value) {
            return ToolExecutionResult(
                success = false,
                output = "Blocked by Emergency Stop.",
                verificationNote = "Emergency Stop engaged"
            )
        }
        val validated = when (val v = ToolInputValidator.validateToolArguments(toolName, arg1, arg2)) {
            is ToolValidationResult.Invalid -> {
                return ToolExecutionResult(false, v.reason, "Failed ToolInputValidator")
            }
            is ToolValidationResult.Valid -> v
        }

        return when (toolName) {
            "toggleFlashlight" -> androidTools.toggleFlashlightVerified(
                validated.arg1.equals("true", ignoreCase = true) || validated.arg1.equals("on", ignoreCase = true)
            )
            "setVolume" -> androidTools.setVolumeVerified(validated.arg1, validated.arg2.toInt())
            "setBrightness" -> androidTools.setBrightnessVerified(validated.arg1.toInt())
            "getBatteryStatus" -> androidTools.getBatteryStatusVerified()
            "getNetworkStatus" -> androidTools.getNetworkStatusVerified()
            "launchApp" -> androidTools.launchAppVerified(validated.arg1)
            "controlMedia" -> androidTools.controlMediaVerified(validated.arg1)
            "openSystemSettings" -> androidTools.openSystemSettingsVerified(validated.arg1)
            "readNotifications" -> androidTools.readNotificationsVerified()
            "performAccessibilityAction" -> androidTools.performAccessibilityActionVerified(validated.arg1)
            "saveMemory" -> {
                val res = memoryRepository.saveMemory(
                    userId = userId,
                    category = "FACT",
                    title = validated.arg1,
                    content = validated.arg2,
                    importance = 7,
                    pinned = false,
                    syncToCloud = syncToCloud
                )
                res.fold(
                    onSuccess = {
                        ToolExecutionResult(
                            success = true,
                            output = "Encrypted & saved memory '${it.title}'.",
                            verificationNote = "Persisted encrypted memory id=${it.id}"
                        )
                    },
                    onFailure = {
                        ToolExecutionResult(
                            success = false,
                            output = "Memory save failed: ${it.localizedMessage}",
                            verificationNote = "MemoryRepository failure"
                        )
                    }
                )
            }
            "searchMemories" -> {
                val matches = memoryRepository.searchRelevantMemories(userId, validated.arg1, topK = 4)
                if (matches.isEmpty()) {
                    ToolExecutionResult(
                        success = true,
                        output = "No matching encrypted memories found.",
                        verificationNote = "SemanticSearch returned 0 matches"
                    )
                } else {
                    ToolExecutionResult(
                        success = true,
                        output = matches.joinToString("\n") { "- ${it.title}: ${it.content}" },
                        verificationNote = "SemanticSearch returned ${matches.size} memories"
                    )
                }
            }
            "webResearch" -> {
                val res = researchEngine.searchAndSummarize(userId, validated.arg1, syncToCloud)
                res.fold(
                    onSuccess = { report ->
                        ToolExecutionResult(
                            success = true,
                            output = "${report.summary}\nSources: ${report.sources.take(3).joinToString { it.title }}",
                            verificationNote = "Grounded with ${report.sources.size} web sources"
                        )
                    },
                    onFailure = {
                        ToolExecutionResult(
                            success = false,
                            output = "Web research failed: ${it.localizedMessage}",
                            verificationNote = "ResearchEngine failure"
                        )
                    }
                )
            }
            else -> ToolExecutionResult(false, "Tool '$toolName' is not registered.", "Unregistered tool")
        }
    }

    companion object {
        const val DEFAULT_CONFIRMATION_TIMEOUT_MS = 45_000L
    }
}
