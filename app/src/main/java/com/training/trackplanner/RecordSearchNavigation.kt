package com.training.trackplanner

import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import com.training.trackplanner.data.isAnalysisEligibleCompletedSet

internal data class RecordSearchJumpRequest(
    val date: String,
    val query: String
)

internal object RecordSearchNavigation {
    fun request(date: String, query: String, tappedMatchingResult: Boolean): RecordSearchJumpRequest? {
        val normalized = query.trim()
        return if (tappedMatchingResult && normalized.isNotEmpty()) {
            RecordSearchJumpRequest(date, normalized)
        } else {
            null
        }
    }

    fun firstConfirmedMatch(
        request: RecordSearchJumpRequest,
        selectedDate: String,
        records: List<WorkoutEntryWithSets>,
        currentExerciseNames: Map<String, String>
    ): Long? {
        if (request.date != selectedDate) return null
        return records.firstOrNull { record ->
            record.sets.any(WorkoutSet::isAnalysisEligibleCompletedSet) &&
                sequenceOf(
                    record.entry.exerciseName,
                    currentExerciseNames[record.entry.exerciseStableKey].orEmpty()
                ).any { name -> name.contains(request.query, ignoreCase = true) }
        }?.entry?.id
    }
}
