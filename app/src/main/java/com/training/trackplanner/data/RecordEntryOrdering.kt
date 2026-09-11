package com.training.trackplanner.data

internal object RecordEntryOrdering {
    /** Current display order owns presentation; only the newly started entry may move. */
    fun insertNewlyPerformed(records: List<WorkoutEntryWithSets>, newlyPerformedEntryId: Long): List<WorkoutEntryWithSets> {
        val current = ordered(records).toMutableList()
        val targetIndex = current.indexOfFirst { it.entry.id == newlyPerformedEntryId }
        if (targetIndex < 0) return current
        val target = current.removeAt(targetIndex)
        val insertionIndex = current.indexOfLast { it.entry.firstConfirmedAt != null } + 1
        current.add(insertionIndex, target)
        return current
    }

    fun ordered(records: List<WorkoutEntryWithSets>): List<WorkoutEntryWithSets> =
        if (records.all { it.entry.displayOrder > 0 }) {
            records.sortedWith(compareBy<WorkoutEntryWithSets> { it.entry.displayOrder }.thenBy { it.entry.id })
        } else {
            records.sortedWith(compareBy<WorkoutEntryWithSets> { it.entry.createdAt }.thenBy { it.entry.id })
        }

    fun moveAfter(
        orderedEntryIds: List<Long>,
        movingEntryId: Long,
        anchorEntryId: Long?
    ): List<Long> {
        if (movingEntryId !in orderedEntryIds) return orderedEntryIds
        val result = orderedEntryIds.toMutableList().apply { remove(movingEntryId) }
        val insertionIndex = anchorEntryId
            ?.let(result::indexOf)
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        result.add(insertionIndex.coerceIn(0, result.size), movingEntryId)
        return result
    }
}
