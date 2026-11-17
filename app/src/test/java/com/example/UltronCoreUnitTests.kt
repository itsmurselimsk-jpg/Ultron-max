package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.UltronDatabase
import com.example.intelligence.ModelRouter
import com.example.intelligence.ModelTier
import com.example.memory.DecryptedMemory
import com.example.memory.MemoryRepository
import com.example.memory.SemanticSearchEngine
import com.example.security.CryptoManager
import com.example.security.RiskEngine
import com.example.security.RiskLevel
import com.example.security.RiskPolicyLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UltronCoreUnitTests {

    private lateinit var db: UltronDatabase
    private lateinit var cryptoManager: CryptoManager
    private lateinit var semanticEngine: SemanticSearchEngine
    private lateinit var memoryRepo: MemoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UltronDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cryptoManager = CryptoManager("test_alias_unit")
        semanticEngine = SemanticSearchEngine()
        memoryRepo = MemoryRepository(
            dao = db.ultronDao(),
            cryptoManager = cryptoManager,
            semanticSearchEngine = semanticEngine,
            firestoreSync = null
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `crypto manager encrypts and decrypts with unique IVs`() {
        val secret = "Ultron Omega tactical key material 12345"
        val enc1 = cryptoManager.encrypt(secret)
        val enc2 = cryptoManager.encrypt(secret)

        assertTrue(enc1.startsWith("ENC_V1:"))
        assertNotEquals(secret, enc1)
        assertNotEquals(enc1, enc2)
        assertEquals(secret, cryptoManager.decrypt(enc1))
        assertEquals(secret, cryptoManager.decrypt(enc2))
    }

    @Test
    fun `model router routes by complexity and explicit tier`() {
        assertEquals(ModelTier.LITE, ModelRouter.selectModel("battery status", "AUTO", false))
        assertEquals(ModelTier.ARGON, ModelRouter.selectModel("Please debug this Kotlin Gradle stacktrace", "AUTO", false))
        assertEquals(ModelTier.FLASH, ModelRouter.selectModel("Summarize quarterly notes", "AUTO", false))
        assertEquals(ModelTier.FLASH, ModelRouter.selectModel("Analyze image", "LITE", true))
        assertEquals(ModelTier.ARGON, ModelRouter.selectModel("hi", "ARGON", false))
    }

    @Test
    fun `risk engine enforces policy tiers and emergency stop`() {
        val riskEngine = RiskEngine()

        val lowEval = riskEngine.evaluateTool("getBatteryStatus", "", RiskPolicyLevel.STRICT, true)
        assertEquals(RiskLevel.LOW, lowEval.riskLevel)
        assertFalse(lowEval.requiresUserConfirmation)

        val medBalanced = riskEngine.evaluateTool("toggleFlashlight", "true", RiskPolicyLevel.BALANCED, true)
        assertEquals(RiskLevel.MEDIUM, medBalanced.riskLevel)
        assertFalse(medBalanced.requiresUserConfirmation)

        val medStrict = riskEngine.evaluateTool("toggleFlashlight", "true", RiskPolicyLevel.STRICT, true)
        assertTrue(medStrict.requiresUserConfirmation)

        val highEval = riskEngine.evaluateTool("launchApp", "Settings", RiskPolicyLevel.BALANCED, true)
        assertEquals(RiskLevel.HIGH, highEval.riskLevel)
        assertTrue(highEval.requiresUserConfirmation)
        assertTrue(highEval.requiresBiometric)

        riskEngine.engageEmergencyStop()
        val stoppedEval = riskEngine.evaluateTool("getBatteryStatus", "", RiskPolicyLevel.AUTONOMOUS, false)
        assertEquals(RiskLevel.CRITICAL, stoppedEval.riskLevel)
        assertTrue(stoppedEval.requiresUserConfirmation)
    }

    @Test
    fun `semantic search ranks matching memories above unrelated entries`() {
        val now = System.currentTimeMillis()
        val memories = listOf(
            DecryptedMemory("1", "u1", "FACT", "Favorite Coffee", "Drinks black espresso every morning", "coffee espresso", 5, false, false, now, now),
            DecryptedMemory("2", "u1", "INSTRUCTION", "Kotlin Architecture", "Prefer Jetpack Compose MVVM with Room and Coroutines", "kotlin compose mvvm room", 9, true, false, now, now),
            DecryptedMemory("3", "u1", "PREFERENCE", "Travel Note", "Booked train tickets for Kolkata", "travel kolkata train", 6, false, false, now, now)
        )

        val results = semanticEngine.search("Kotlin Compose MVVM architecture", memories, topK = 2)
        assertEquals(2, results.size)
        assertEquals("2", results.first().id)
    }

    @Test
    fun `memory repository saves encrypted records supports backup restore and audit chain`() = runTest {
        val userId = "operator_test"
        val saved = memoryRepo.saveMemory(
            userId = userId,
            category = "FACT",
            title = "Core Directive",
            content = "Maintain strict zero-trust encryption across all local memory.",
            importance = 10,
            pinned = true,
            syncToCloud = false
        ).getOrThrow()

        val rawEntities = db.ultronDao().getMemoriesOnce(userId)
        assertEquals(1, rawEntities.size)
        assertTrue(rawEntities.first().encryptedContent.startsWith("ENC_V1:"))
        assertFalse(rawEntities.first().encryptedContent.contains("zero-trust"))

        val observed = memoryRepo.observeMemories(userId).first()
        assertEquals(1, observed.size)
        assertEquals("Maintain strict zero-trust encryption across all local memory.", observed.first().content)

        val backupJson = memoryRepo.exportEncryptedBackupJson(userId)
        assertTrue(backupJson.contains("ULTRON_OMEGA_ENCRYPTED_BACKUP_V1"))
        assertFalse(backupJson.contains("zero-trust"))

        memoryRepo.deleteMemory(userId, saved.id, syncToCloud = false).getOrThrow()
        assertTrue(db.ultronDao().getMemoriesOnce(userId).isEmpty())

        val restoredCount = memoryRepo.importEncryptedBackupJson(userId, backupJson, syncToCloud = false).getOrThrow()
        assertEquals(1, restoredCount)
        assertEquals(1, db.ultronDao().getMemoriesOnce(userId).size)

        memoryRepo.recordAudit(userId, "USER", "VERIFY_TEST", RiskLevel.LOW, "EXECUTED", "First entry", false)
        memoryRepo.recordAudit(userId, "USER", "VERIFY_TEST_2", RiskLevel.MEDIUM, "EXECUTED", "Second entry", false)
        val logs = memoryRepo.observeAuditLogs(userId).first()
        assertEquals(2, logs.size)
        assertNotEquals(logs[0].integrityHash, logs[1].integrityHash)
        assertEquals(64, logs[0].integrityHash.length)
    }
}
