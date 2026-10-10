package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.analysis.core.CoreDirectTarget
import com.training.trackplanner.data.Exercise
import java.util.Locale

/** Shape evidence for an exact, canonical direct-Core exercise. It grants no dose authority. */
internal sealed interface CoreDirectPrescriptionShape {
    data class Repetitions(val reps: Int, val fromPersonalHistory: Boolean) : CoreDirectPrescriptionShape
    data class Duration(val seconds: Int) : CoreDirectPrescriptionShape
}

/**
 * Resolves only the execution metric already declared by the exercise's recording mode or an
 * exact confirmed set. Ambiguous time-or-repetition exercises need exact personal history;
 * static duration is never synthesized.
 */
internal object CoreDirectPrescriptionShapeResolver {
    private const val PERSONAL_REP_MIN = 7
    private const val PERSONAL_REP_MAX = 15
    private const val COLD_START_REPS = RegionalColdStartDosePolicy.HYPERTROPHY_COLD_START_REPS

    fun resolve(stableKey: String, snapshot: PlanningHistorySnapshot, coreCatalog: CanonicalCoreCatalog): CoreDirectPrescriptionShape? {
        val profile = coreCatalog.resolve(stableKey)?.takeIf {
            it.coreClass == CoreClass.DIRECT && it.directTarget != null
        } ?: return null
        val history = snapshot.allConfirmedSets.asSequence()
            .filter { it.stableKey == stableKey }
            .maxWithOrNull(compareBy<PlanningSetRecord> { it.date }.thenBy { it.setIndex })
        if (history != null) {
            if (history.seconds > 0 && history.reps <= 0) return CoreDirectPrescriptionShape.Duration(history.seconds)
            if (history.reps in PERSONAL_REP_MIN..PERSONAL_REP_MAX && history.seconds <= 0 &&
                (!effortRequired(profile.directTarget) || history.rpe == null || history.rpe >= RegionalColdStartDosePolicy.HYPERTROPHY_MINIMUM_TARGET_RPE)
            ) return CoreDirectPrescriptionShape.Repetitions(history.reps, fromPersonalHistory = true)
        }
        val exercise = snapshot.exercises[stableKey] ?: return null
        return when (recordingMetric(exercise)) {
            RecordingMetric.REPETITIONS -> CoreDirectPrescriptionShape.Repetitions(COLD_START_REPS, fromPersonalHistory = false)
            RecordingMetric.DURATION, RecordingMetric.AMBIGUOUS, RecordingMetric.UNKNOWN -> null
        }
    }

    fun supportsExecutableShape(stableKey: String, snapshot: PlanningHistorySnapshot, coreCatalog: CanonicalCoreCatalog): Boolean =
        resolve(stableKey, snapshot, coreCatalog) != null

    fun requiresHypertrophyEffort(target: CoreDirectTarget?): Boolean = effortRequired(target)

    private fun effortRequired(target: CoreDirectTarget?): Boolean = target in setOf(
        CoreDirectTarget.TRUNK_FLEXION,
        CoreDirectTarget.TRUNK_EXTENSION
    )

    private enum class RecordingMetric { REPETITIONS, DURATION, AMBIGUOUS, UNKNOWN }

    private fun recordingMetric(exercise: Exercise): RecordingMetric {
        val mode = exercise.mode.trim().lowercase(Locale.ROOT)
        val hasRepetitions = mode.contains("횟수") || mode.contains("반복") || mode.contains("repetition") || mode.contains("rep")
        val hasDuration = mode.contains("시간") || mode.contains("초") || mode.contains("duration") || mode.contains("time")
        return when {
            hasRepetitions && !hasDuration -> RecordingMetric.REPETITIONS
            hasDuration && !hasRepetitions -> RecordingMetric.DURATION
            hasDuration && hasRepetitions -> RecordingMetric.AMBIGUOUS
            else -> RecordingMetric.UNKNOWN
        }
    }
}
