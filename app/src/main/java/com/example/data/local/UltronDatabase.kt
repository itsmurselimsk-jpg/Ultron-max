package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AgentTaskEntity::class,
        AgentStepEntity::class,
        CitationEntity::class,
        AuditLogEntity::class,
        BriefingHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class UltronDatabase : RoomDatabase() {
    abstract fun ultronDao(): UltronDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `briefing_history` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_briefing_history_userId` ON `briefing_history` (`userId`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: UltronDatabase? = null

        fun getInstance(context: Context): UltronDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UltronDatabase::class.java,
                    "ultron_omega.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
