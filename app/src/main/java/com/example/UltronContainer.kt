package com.example

import android.content.Context
import com.example.agent.AgentEngine
import com.example.androidtools.AndroidToolExecutor
import com.example.data.local.UltronDatabase
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.remote.GitHubApiService
import com.example.data.remote.UrlContentFetcher
import com.example.developer.DeveloperEngine
import com.example.intelligence.IntelligenceEngine
import com.example.memory.MemoryRepository
import com.example.memory.SemanticSearchEngine
import com.example.research.ResearchEngine
import com.example.security.BiometricAuthManager
import com.example.security.CryptoManager
import com.example.security.PermissionManager
import com.example.security.RiskEngine
import com.example.security.SecureCredentialStore
import com.example.vision.VisionEngine
import com.example.voice.AudioVadEngine
import com.example.voice.DeepMaleTtsEngine
import com.example.voice.VoiceEngine
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Clean constructor-injection dependency container for all Ultron Omega engines.
 */
class UltronContainer(context: Context) {
    private val appContext = context.applicationContext

    val cryptoManager = CryptoManager()
    val credentialStore = SecureCredentialStore(appContext, cryptoManager)
    val riskEngine = RiskEngine()
    val permissionManager = PermissionManager(appContext)
    val biometricAuthManager = BiometricAuthManager()

    val database = UltronDatabase.getInstance(appContext)
    val dao = database.ultronDao()

    val firestoreDb: FirebaseFirestore = FirebaseFirestore.getInstance(
        appContext.getString(R.string.firestore_database_id)
    )
    val firestoreSync = FirestoreSyncRepository(firestoreDb)

    val semanticSearchEngine = SemanticSearchEngine()
    val memoryRepository = MemoryRepository(
        dao = dao,
        cryptoManager = cryptoManager,
        semanticSearchEngine = semanticSearchEngine,
        firestoreSync = firestoreSync
    )

    val intelligenceEngine = IntelligenceEngine()
    val androidToolExecutor = AndroidToolExecutor(appContext)
    val urlContentFetcher = UrlContentFetcher()
    val researchEngine = ResearchEngine(
        context = appContext,
        urlFetcher = urlContentFetcher,
        intelligenceEngine = intelligenceEngine,
        memoryRepository = memoryRepository
    )

    val gitHubApi = GitHubApiService.create()
    val developerEngine = DeveloperEngine(
        gitHubApi = gitHubApi,
        credentialStore = credentialStore,
        intelligenceEngine = intelligenceEngine
    )

    val visionEngine = VisionEngine(
        context = appContext,
        intelligenceEngine = intelligenceEngine
    )

    val vadEngine = AudioVadEngine(appContext)
    val ttsEngine = DeepMaleTtsEngine(appContext)
    val voiceEngine = VoiceEngine(appContext, vadEngine, ttsEngine)

    val agentEngine = AgentEngine(
        dao = dao,
        intelligenceEngine = intelligenceEngine,
        androidTools = androidToolExecutor,
        memoryRepository = memoryRepository,
        researchEngine = researchEngine,
        riskEngine = riskEngine
    )
}
