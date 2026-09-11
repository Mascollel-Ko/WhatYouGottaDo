package com.training.trackplanner.data

internal object RecordEntryOrdering {
    fun firstConfirmationOrder(records: List<WorkoutEntryWithSets>): List<WorkoutEntryWithSets> {
        val previous = ordered(records)
        return previous.filter { it.entry.firstConfirmedAt != null }.sortedBy { it.entry.firstConfirmedAt } +
            previous.filter { it.entry.firstConfirmedAt == null }
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
