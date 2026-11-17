package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.agent.AgentEngine
import com.example.androidtools.AndroidToolExecutor
import com.example.data.local.UltronDatabase
import com.example.intelligence.IntelligenceEngine
import com.example.memory.MemoryRepository
import com.example.memory.SemanticSearchEngine
import com.example.research.ResearchEngine
import com.example.security.CryptoManager
import com.example.security.RiskEngine
import com.example.security.RiskPolicyLevel
import com.example.data.remote.UrlContentFetcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UltronAgentEngineTest {

    private lateinit var context: Context
    private lateinit var db: UltronDatabase
    private lateinit var cryptoManager: CryptoManager
    private lateinit var memoryRepo: MemoryRepository
    private lateinit var riskEngine: RiskEngine
    private lateinit var androidTools: AndroidToolExecutor
    private lateinit var intelligenceEngine: IntelligenceEngine
    private lateinit var researchEngine: ResearchEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, UltronDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cryptoManager = CryptoManager("agent_engine_test_alias")
        memoryRepo = MemoryRepository(
            dao = db.ultronDao(),
            cryptoManager = cryptoManager,
            semanticSearchEngine = SemanticSearchEngine(),
            firestoreSync = null
        )
        riskEngine = RiskEngine()
        androidTools = AndroidToolExecutor(context)
        intelligenceEngine = IntelligenceEngine()
        researchEngine = ResearchEngine(
            context = context,
            urlFetcher = UrlContentFetcher(),
            intelligenceEngine = intelligenceEngine,
            memoryRepository = memoryRepo
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun buildAgent(
        confirmationTimeoutMs: Long = 5_000L,
        planJson: String
    ): AgentEngine {
        return AgentEngine(
            dao = db.ultronDao(),
            intelligenceEngine = intelligenceEngine,
            androidTools = androidTools,
            memoryRepository = memoryRepo,
            researchEngine = researchEngine,
            riskEngine = riskEngine,
            confirmationTimeoutMs = confirmationTimeoutMs,
            planProvider = { Result.success(planJson) }
        )
    }

    @Test
    fun `concurrent autonomous goals are rejected by single active task mutex`() = runTest {
        val plan = """
            {"planSummary":"Confirm test","steps":[{"agentRole":"ANDROID_EXECUTOR","toolName":"toggleFlashlight","arg1":"false","arg2":""}]}
        """.trimIndent()
        val engine = buildAgent(confirmationTimeoutMs = 5_000L, planJson = plan)

        val firstJob = async {
            engine.executeAutonomousGoal("user1", "First mission", RiskPolicyLevel.STRICT, false, false)
        }
        // Wait until first mission pauses on confirmation
        var waited = 0
        while (engine.pendingConfirmation.value == null && waited < 40) {
            delay(25L)
            waited++
        }
        assertNotNull(engine.pendingConfirmation.value)

        val secondResult = engine.executeAutonomousGoal("user1", "Second mission", RiskPolicyLevel.BALANCED, false, false)
        assertTrue(secondResult.isFailure)
        assertTrue(secondResult.exceptionOrNull()?.message?.contains("already running") == true)

        // Deny first mission cleanly so coroutine completes
        engine.resolveConfirmation(false)
        firstJob.await()
        assertNull(engine.activeTaskId.value)
    }

    @Test
    fun `cancelActiveTask preserves original goal and cancels pending steps`() = runTest {
        val plan = """
            {"planSummary":"Two step confirm","steps":[{"agentRole":"ANDROID_EXECUTOR","toolName":"toggleFlashlight","arg1":"true","arg2":""}]}
        """.trimIndent()
        val engine = buildAgent(confirmationTimeoutMs = 5_000L, planJson = plan)

        val job = async {
            engine.executeAutonomousGoal("user1", "Original Mission Title", RiskPolicyLevel.STRICT, false, false)
        }
        var waited = 0
        while (engine.pendingConfirmation.value == null && waited < 40) {
            delay(25L)
            waited++
        }
        val activeId = engine.activeTaskId.value
        assertNotNull(activeId)

        engine.cancelActiveTask("user1", syncToCloud = false)
        job.await()

        val task = db.ultronDao().getAgentTaskById(activeId!!)
        assertNotNull(task)
        assertEquals("Original Mission Title", task?.goal)
        assertEquals("CANCELLED", task?.status)
        assertNull(engine.activeTaskId.value)
        assertNull(engine.pendingConfirmation.value)

        val audits = memoryRepo.observeAuditLogs("user1").first()
        assertTrue(audits.any { it.action == "AGENT_TASK_CANCEL" && it.outcome == "STOPPED" })
    }

    @Test
    fun `confirmation timeout auto cancels mission and logs DENIED audit`() = runTest {
        val plan = """
            {"planSummary":"Timeout confirm","steps":[{"agentRole":"ANDROID_EXECUTOR","toolName":"toggleFlashlight","arg1":"true","arg2":""}]}
        """.trimIndent()
        val engine = buildAgent(confirmationTimeoutMs = 150L, planJson = plan)

        val res = engine.executeAutonomousGoal("user1", "Timeout Mission", RiskPolicyLevel.STRICT, false, false)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("timed out") == true)
        assertNull(engine.pendingConfirmation.value)
        assertNull(engine.activeTaskId.value)

        val tasks = engine.observeTasks("user1").first()
        assertEquals("CANCELLED", tasks.first().status)

        val audits = memoryRepo.observeAuditLogs("user1").first()
        assertTrue(audits.any { it.action == "toggleFlashlight" && it.outcome == "DENIED" })
    }

    @Test
    fun `stale taskId or stepId cannot resolve active confirmation`() = runTest {
        val plan = """
            {"planSummary":"Stale guard","steps":[{"agentRole":"MEMORY_CURATOR","toolName":"saveMemory","arg1":"Title","arg2":"Body"}]}
        """.trimIndent()
        val engine = buildAgent(confirmationTimeoutMs = 5_000L, planJson = plan)

        val job = async {
            engine.executeAutonomousGoal("user1", "Stale test", RiskPolicyLevel.STRICT, false, false)
        }
        var waited = 0
        while (engine.pendingConfirmation.value == null && waited < 40) {
            delay(25L)
            waited++
        }
        val req = engine.pendingConfirmation.value!!
        assertFalse(engine.resolveConfirmation(true, expectedTaskId = "wrong_task_id"))
        assertFalse(engine.resolveConfirmation(true, expectedStepId = "wrong_step_id"))
        assertNotNull(engine.pendingConfirmation.value)

        assertTrue(engine.resolveConfirmation(true, expectedTaskId = req.taskId, expectedStepId = req.stepId))
        val res = job.await()
        assertTrue(res.isSuccess)
    }

    @Test
    fun `denying step blocks subsequent steps and keeps memory clean`() = runTest {
        val plan = """
            {"planSummary":"Deny test","steps":[
              {"agentRole":"ANDROID_EXECUTOR","toolName":"toggleFlashlight","arg1":"true","arg2":""},
              {"agentRole":"MEMORY_CURATOR","toolName":"saveMemory","arg1":"ShouldNotSave","arg2":"Blocked body"}
            ]}
        """.trimIndent()
        val engine = buildAgent(confirmationTimeoutMs = 5_000L, planJson = plan)

        val job = async {
            engine.executeAutonomousGoal("user1", "Deny Mission", RiskPolicyLevel.STRICT, false, false)
        }
        var waited = 0
        while (engine.pendingConfirmation.value == null && waited < 40) {
            delay(25L)
            waited++
        }
        engine.resolveConfirmation(false)
        val res = job.await()

        assertTrue(res.isFailure)
        assertTrue(db.ultronDao().getMemoriesOnce("user1").isEmpty())

        val steps = engine.observeAllSteps("user1").first()
        assertEquals(2, steps.size)
        assertTrue(steps.any { it.toolName == "toggleFlashlight" && it.status == "FAILED" })
        assertTrue(steps.any { it.toolName == "saveMemory" && it.status == "CANCELLED" })
    }

    @Test
    fun `emergency stop blocks launch cleanly`() = runTest {
        val plan = """
            {"planSummary":"Noop","steps":[{"agentRole":"VERIFIER","toolName":"getBatteryStatus","arg1":"","arg2":""}]}
        """.trimIndent()
        val engine = buildAgent(planJson = plan)
        riskEngine.engageEmergencyStop()

        val res = engine.executeAutonomousGoal("user1", "Battery Check", RiskPolicyLevel.AUTONOMOUS, false, false)
        assertTrue(res.isFailure)
        val audits = memoryRepo.observeAuditLogs("user1").first()
        assertTrue(audits.any { it.action == "AGENT_LAUNCH_BLOCKED" && it.outcome == "STOPPED" })
    }

    @Test
    fun `successful tool persists VERIFIED while failing tool marks FAILED`() = runTest {
        val okPlan = """
            {"planSummary":"Save encrypted fact","steps":[{"agentRole":"MEMORY_CURATOR","toolName":"saveMemory","arg1":"Mission Fact","arg2":"Verified AES payload"}]}
        """.trimIndent()
        val okEngine = buildAgent(planJson = okPlan)
        val okResult = okEngine.executeAutonomousGoal("user1", "Remember mission fact", RiskPolicyLevel.AUTONOMOUS, false, false)
        assertTrue(okResult.isSuccess)
        assertEquals(1, db.ultronDao().getMemoriesOnce("user1").size)
        val okTasks = okEngine.observeTasks("user1").first()
        assertEquals("VERIFIED", okTasks.first().status)

        val failPlan = """
            {"planSummary":"Launch non-existent app","steps":[{"agentRole":"ANDROID_EXECUTOR","toolName":"launchApp","arg1":"com.nonexistent.ultron.fake.package","arg2":""}]}
        """.trimIndent()
        val failEngine = buildAgent(planJson = failPlan)
        val failJob = async {
            failEngine.executeAutonomousGoal("user2", "Launch missing app", RiskPolicyLevel.BALANCED, false, false)
        }
        var waited = 0
        while (failEngine.pendingConfirmation.value == null && waited < 40) {
            delay(25L)
            waited++
        }
        failEngine.resolveConfirmation(true)
        val failResult = failJob.await()
        assertTrue(failResult.isFailure)

        val failTasks = failEngine.observeTasks("user2").first()
        assertEquals("FAILED", failTasks.first().status)
        val failSteps = failEngine.observeAllSteps("user2").first()
        assertEquals("FAILED", failSteps.first().status)
        val audits = memoryRepo.observeAuditLogs("user2").first()
        assertTrue(audits.any { it.action == "launchApp" && it.outcome == "BLOCKED" })
    }
}
