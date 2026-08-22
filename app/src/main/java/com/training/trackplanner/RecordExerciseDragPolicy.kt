package com.training.trackplanner

import kotlin.math.abs

internal data class VisibleRecordEntry(
    val entryId: Long,
    val top: Int,
    val bottom: Int
)

internal object RecordExerciseDragPolicy {
    fun targetEntryId(visibleEntries: List<VisibleRecordEntry>, draggedCenterY: Float): Long? =
        visibleEntries.minByOrNull { entry ->
            abs(((entry.top + entry.bottom) / 2f) - draggedCenterY)
        }?.entryId

    fun reorderedEntryIds(
        orderedEntryIds: List<Long>,
        movingEntryId: Long,
        targetEntryId: Long?
    ): List<Long> {
        if (movingEntryId !in orderedEntryIds || targetEntryId !in orderedEntryIds) return orderedEntryIds
        val targetIndex = orderedEntryIds.indexOf(targetEntryId)
        return orderedEntryIds.toMutableList().apply {
            remove(movingEntryId)
            add(targetIndex.coerceIn(0, size), movingEntryId)
        }
    }
}
