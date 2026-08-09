package com.training.trackplanner.data

import androidx.room.withTransaction
import java.util.UUID

internal class WorkoutSourceIdentityProvider(
    private val db: TrainingDatabase,
    private val appMetaDao: AppMetaDao,
    private val workoutDao: WorkoutDao
) {
    suspend fun newWorkoutSourceId(): String =
        "${sourceDatabaseLineageId()}:workout_entry:${UUID.randomUUID()}"

    suspend fun sourceIdForImport(sourceId: String?): String =
        sourceId?.trim()?.takeIf(String::isNotEmpty) ?: newWorkoutSourceId()

    suspend fun backfillMissingWorkoutSourceIds(): Int = db.withTransaction {
        val lineageId = sourceDatabaseLineageId()
        val missing = workoutDao.entriesMissingBackupSourceId()
        missing.forEach { entry ->
            workoutDao.updateEntry(
                entry.copy(backupSourceId = "$lineageId:workout_entry:${entry.id}")
            )
        }
        missing.size
    }

    suspend fun sourceDatabaseLineageId(): String {
        appMetaDao.value(SOURCE_DATABASE_LINEAGE_ID)?.takeIf(String::isNotBlank)?.let { return it }
        val generated = UUID.randomUUID().toString()
        appMetaDao.upsert(AppMeta(SOURCE_DATABASE_LINEAGE_ID, generated))
        return appMetaDao.value(SOURCE_DATABASE_LINEAGE_ID).orEmpty().ifBlank { generated }
    }

    companion object {
        const val SOURCE_DATABASE_LINEAGE_ID = "source_database_lineage_id"
    }
}
