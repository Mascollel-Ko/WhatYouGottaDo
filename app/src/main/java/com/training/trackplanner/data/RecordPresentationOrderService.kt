package com.training.trackplanner.data

import androidx.room.withTransaction

internal class RecordPresentationOrderService(
    private val db: TrainingDatabase,
    private val workoutDao: WorkoutDao,
    private val appMetaDao: AppMetaDao
) {
    suspend fun reorder(date: String, orderedEntryIds: List<Long>): Boolean = db.withTransaction {
        val existing = RecordEntryOrdering.ordered(workoutDao.entriesWithSets(date))
        val existingIds = existing.map { record -> record.entry.id }
        if (orderedEntryIds.size != existingIds.size || orderedEntryIds.toSet() != existingIds.toSet()) {
            return@withTransaction false
        }

        orderedEntryIds.forEachIndexed { index, entryId ->
            val displayOrder = index + 1
            if (existing.first { record -> record.entry.id == entryId }.entry.displayOrder != displayOrder) {
                workoutDao.updateEntryDisplayOrder(entryId, displayOrder)
            }
        }
        appMetaDao.upsert(
            AppMeta(
                key = RecordManualOrderPolicy.key(date),
                value = RecordManualOrderPolicy.markerValue(existingIds)
            )
        )
        true
    }
}

internal object RecordManualOrderPolicy {
    private const val KEY_PREFIX = "record_manual_display_order:"

    fun key(date: String): String = "$KEY_PREFIX$date"

    fun markerValue(entryIds: Collection<Long>): String = entryIds.sorted().joinToString(",")

    fun applies(marker: String?, currentEntryIds: Collection<Long>): Boolean {
        if (marker.isNullOrBlank() || currentEntryIds.isEmpty()) return false
        val current = currentEntryIds.toSet()
        return marker.split(',').any { token -> token.toLongOrNull() in current }
    }
}
