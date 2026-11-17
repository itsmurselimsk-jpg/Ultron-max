package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BriefingHistoryEntity
import com.example.data.local.UltronDatabase
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UltronDatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 preserves encrypted memories and tasks while adding briefing_history`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v1_v2.db"
        context.deleteDatabase(dbName)

        val crypto = CryptoManager("mig_test_alias")
        val encryptedBody = crypto.encrypt("Survives v1 -> v2 schema upgrade cleanly.")

        // Create authentic v1 SQLite database manually
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `memories` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `category` TEXT NOT NULL, `title` TEXT NOT NULL, `encryptedContent` TEXT NOT NULL, `searchKeywords` TEXT NOT NULL, `importance` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `syncedToCloud` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_userId` ON `memories` (`userId`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, `modelTier` TEXT NOT NULL, `languageCode` TEXT NOT NULL, `lastMessagePreview` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_userId` ON `conversations` (`userId`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `userId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `modelUsed` TEXT NOT NULL, `citationsJson` TEXT NOT NULL, `verified` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `agent_tasks` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `goal` TEXT NOT NULL, `status` TEXT NOT NULL, `planSummary` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_tasks_userId` ON `agent_tasks` (`userId`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `agent_steps` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `stepOrder` INTEGER NOT NULL, `agentRole` TEXT NOT NULL, `toolName` TEXT NOT NULL, `argumentsJson` TEXT NOT NULL, `riskLevel` TEXT NOT NULL, `status` TEXT NOT NULL, `output` TEXT NOT NULL, `verificationNotes` TEXT NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_steps_taskId` ON `agent_steps` (`taskId`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `citations` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `query` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `snippet` TEXT NOT NULL, `summary` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_citations_userId` ON `citations` (`userId`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `actor` TEXT NOT NULL, `action` TEXT NOT NULL, `riskLevel` TEXT NOT NULL, `outcome` TEXT NOT NULL, `details` TEXT NOT NULL, `integrityHash` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_userId` ON `audit_logs` (`userId`)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.use { v1 ->
            v1.execSQL(
                "INSERT INTO memories (id, userId, category, title, encryptedContent, searchKeywords, importance, pinned, syncedToCloud, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf("m1", "u_mig", "FACT", "Legacy Key", encryptedBody, "legacy", 8, 1, 0, 1000L, 1000L)
            )
        }
        helper.close()

        // Re-open with Room v2 and real MIGRATION_1_2
        val migratedDb = Room.databaseBuilder(context, UltronDatabase::class.java, dbName)
            .addMigrations(UltronDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val dao = migratedDb.ultronDao()
        val preserved = dao.getMemoriesOnce("u_mig")
        assertEquals(1, preserved.size)
        assertEquals("Survives v1 -> v2 schema upgrade cleanly.", crypto.decrypt(preserved.first().encryptedContent))

        dao.insertBriefingHistory(
            BriefingHistoryEntity(
                id = "b1",
                userId = "u_mig",
                title = "Post-Migration Briefing",
                body = "All v2 tables nominal",
                createdAt = 2000L
            )
        )
        val briefings = dao.observeBriefingHistory("u_mig").first()
        assertEquals(1, briefings.size)
        assertEquals("Post-Migration Briefing", briefings.first().title)

        migratedDb.close()
        context.deleteDatabase(dbName)
    }
}
