package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntry
import java.util.Locale

object AnalysisExerciseDisplayNameResolver {
    fun resolve(
        entry: WorkoutEntry?,
        exercise: Exercise,
        catalog: RuntimeExerciseMetadataCatalog
    ): String =
        firstUsable(
            entry?.exerciseName,
            exercise.name,
            catalog.resolveByStableKey(exercise.stableKey)?.exerciseName,
            catalog.resolveLegacyName(entry?.exerciseName.orEmpty())?.exerciseName
        ) ?: "운동 ${exercise.id}"

    private fun firstUsable(vararg candidates: String?): String? =
        candidates.firstOrNull { candidate -> candidate != null && !candidate.isFallbackName() }?.trim()

    private fun String.isFallbackName(): Boolean {
        val value = trim()
        if (value.isBlank()) return true
        val normalized = value.lowercase(Locale.ROOT)
        return normalized == "unknown" ||
            normalized == "unknown exercise" ||
            normalized == "알 수 없는 운동" ||
            normalized == "csv 복원 운동" ||
            normalized.startsWith("imported_") ||
            normalized.startsWith("custom_") ||
            Regex("""^운동\s*\d+$""").matches(value) ||
            Regex("""^exercise\s*\d+$""", RegexOption.IGNORE_CASE).matches(value)
    }
}
