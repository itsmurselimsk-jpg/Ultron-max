package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AgentTaskEntity::class,
        AgentStepEntity::class,
        CitationEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class UltronDatabase : RoomDatabase() {
    abstract fun ultronDao(): UltronDao

    companion object {
        @Volatile
        private var INSTANCE: UltronDatabase? = null

        fun getInstance(context: Context): UltronDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UltronDatabase::class.java,
                    "ultron_omega.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
