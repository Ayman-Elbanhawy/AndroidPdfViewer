package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json

@Entity(tableName = "ai_provider_settings")
data class AiProviderSettingsEntity(
    @PrimaryKey val singletonId: String,
    val payloadJson: String,
    val updatedAtEpochMillis: Long,
)

@Dao
interface AiProviderSettingsDao {
    @Query("SELECT * FROM ai_provider_settings WHERE singletonId = :singletonId LIMIT 1")
    suspend fun get(singletonId: String = SINGLETON_ID): AiProviderSettingsEntity?

    @Upsert
    suspend fun upsert(entity: AiProviderSettingsEntity)

    companion object {
        const val SINGLETON_ID: String = "assistant-provider-settings"
    }
}

@Database(
    entities = [AiProviderSettingsEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AiAssistantDatabase : RoomDatabase() {
    abstract fun providerSettingsDao(): AiProviderSettingsDao

    companion object {
        const val DATABASE_NAME: String = "ai-assistant.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE ai_provider_settings ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}

interface AiProviderSettingsStore {
    suspend fun load(): AssistantPersistenceModel
    suspend fun save(model: AssistantPersistenceModel)
}

class RoomAiProviderSettingsStore(
    private val dao: AiProviderSettingsDao,
    private val json: Json,
) : AiProviderSettingsStore {
    override suspend fun load(): AssistantPersistenceModel {
        return dao.get()?.let { entity ->
            runCatching { json.decodeFromString(AssistantPersistenceModel.serializer(), entity.payloadJson) }
                .getOrDefault(AssistantPersistenceModel())
        } ?: AssistantPersistenceModel()
    }

    override suspend fun save(model: AssistantPersistenceModel) {
        dao.upsert(
            AiProviderSettingsEntity(
                singletonId = AiProviderSettingsDao.SINGLETON_ID,
                payloadJson = json.encodeToString(AssistantPersistenceModel.serializer(), model),
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }
}

internal fun newAiAssistantDatabase(context: Context): AiAssistantDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        AiAssistantDatabase::class.java,
        AiAssistantDatabase.DATABASE_NAME,
    ).addMigrations(AiAssistantDatabase.MIGRATION_1_2).build()
}
