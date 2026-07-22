package com.training.trackplanner.analysis.proxyperformance

import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import java.util.TreeMap

internal data class ProxyObservationBuildResult(
    val observations: List<ProxyPerformanceObservation>,
    val diagnostics: List<ProxyPerformanceDiagnostic>
)

internal object ProxyPerformanceObservationBuilder {
    fun build(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exercises: List<Exercise>,
        runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog,
        dailyMetrics: List<DailyMetric>,
        initialProfile: InitialUserProfile? = null
    ): ProxyObservationBuildResult {
        val exercisesById = exercises.associateBy(Exercise::id)
        val bodyWeights = TreeMap<String, Double>().apply {
            dailyMetrics.forEach { metric ->
                metric.bodyWeightKg?.takeIf { value -> value.isFinite() && value > 0.0 }
                    ?.let { value -> put(metric.date, value) }
            }
        }
        val diagnostics = mutableListOf<ProxyPerformanceDiagnostic>()
        val observations = entriesWithSets.mapNotNull { record ->
            val entry = record.entry
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
            if (date == null) {
                diagnostics += ProxyPerformanceDiagnostic(
                    ProxyPerformanceDiagnosticCode.OBSERVATION_SKIPPED,
                    "Workout entry has an invalid date.",
                    workoutEntryId = entry.id
                )
                return@mapNotNull null
            }
            val exercise = exercisesById[entry.exerciseId] ?: return@mapNotNull null
            val runtimeMetadata = runtimeMetadataCatalog.resolve(exercise)
            val activityKind = runtimeMetadata?.activityKind?.ifBlank { exercise.activityKind }
                ?: exercise.activityKind
            if (activityKind.uppercase().contains("SPORT_SESSION")) return@mapNotNull null
            val loading = ProxyPerformanceLoadingBuilder.build(exercise, entry, runtimeMetadata)
            if (!loading.isUsable) return@mapNotNull null
            val bodyWeightKg = bodyWeights.floorEntry(entry.date)?.value ?: initialProfile?.bodyWeightKg
            val usableSets = record.sets
                .asSequence()
                .filter(WorkoutSet::confirmed)
                .filter { set -> set.reps in SUPPORTED_REPETITIONS }
                .sortedWith(compareBy(WorkoutSet::setIndex, WorkoutSet::id))
                .mapNotNull { set ->
                    val effectiveLoad = BodyweightEffectiveLoadCalculator.volumeLoad(exercise, set, bodyWeightKg) / set.reps
                    if (!effectiveLoad.isFinite() || effectiveLoad <= 0.0) return@mapNotNull null
                    val rpe = set.rpe.validRpe() ?: entry.rpe.validRpe()
                    val rir = rpe?.let { value -> (10.0 - value).coerceIn(0.0, MAX_RIR) }
                    val canonical = effectiveLoad * (1.0 + set.reps / EPLEY_DENOMINATOR)
                    val effortAdjusted = effectiveLoad * (
                        1.0 + (set.reps + (rir ?: 0.0)) / EPLEY_DENOMINATOR
                        )
                    val quality = repetitionQuality(set.reps) *
                        (if (rpe != null) 1.0 else 0.72) *
                        setOrderQuality(set.setIndex) *
                        (if (set.weightKg > 0.0) 1.0 else 0.85) *
                        loading.metadataConfidence.coerceAtLeast(0.25)
                    SetSignal(set, canonical, effortAdjusted, quality, rpe != null)
                }
                .toList()
            if (usableSets.isEmpty()) return@mapNotNull null
            val rpeAvailable = usableSets.any(SetSignal::rpeAvailable)
            val quality = usableSets.map(SetSignal::quality).average().coerceIn(0.10, 1.0)
            val directTarget = ProxyPerformanceLoadingBuilder.directTarget(exercise, entry)
            val actualCanonical = if (directTarget != null) {
                record.sets.asSequence()
                    .filter(WorkoutSet::confirmed)
                    .filter { set -> set.weightKg > 0.0 && set.reps in SUPPORTED_REPETITIONS }
                    .maxOfOrNull { set -> set.weightKg * (1.0 + set.reps / EPLEY_DENOMINATOR) }
                    ?: return@mapNotNull null
            } else {
                weightedMedian(usableSets) { signal -> signal.canonicalE1rmKg }
            }
            ProxyPerformanceObservation(
                workoutEntryId = entry.id,
                date = date,
                performedAt = entry.performedAt,
                displayOrder = entry.displayOrder,
                exerciseId = exercise.id,
                exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name,
                directTarget = directTarget,
                canonicalE1rmKg = actualCanonical,
                effortAdjustedPerformanceKg = weightedMedian(usableSets) { signal -> signal.effortAdjustedE1rmKg },
                observationVariance = ((if (rpeAvailable) 0.10 else 0.28) / quality).coerceIn(0.08, 1.20),
                quality = quality,
                rpeAvailable = rpeAvailable,
                sourceSetIds = usableSets.map { signal -> signal.set.id },
                loading = loading
            )
        }.sortedWith(
            compareBy<ProxyPerformanceObservation>(ProxyPerformanceObservation::date)
                .thenBy { observation -> observation.performedAt ?: Long.MIN_VALUE }
                .thenBy(ProxyPerformanceObservation::displayOrder)
                .thenBy(ProxyPerformanceObservation::workoutEntryId)
                .thenBy(ProxyPerformanceObservation::exerciseStableKey)
        )
        return ProxyObservationBuildResult(observations, diagnostics)
    }

    private fun weightedMedian(
        signals: List<SetSignal>,
        value: (SetSignal) -> Double
    ): Double {
        val ordered = signals.sortedWith(
            compareBy<SetSignal> { signal -> value(signal) }
                .thenBy { signal -> signal.set.setIndex }
                .thenBy { signal -> signal.set.id }
        )
        val threshold = ordered.sumOf(SetSignal::quality) / 2.0
        var accumulated = 0.0
        return ordered.firstOrNull { signal ->
            accumulated += signal.quality
            accumulated >= threshold
        }?.let(value) ?: value(ordered.last())
    }

    private fun repetitionQuality(reps: Int): Double = when (reps) {
        in 1..5 -> 1.0
        in 6..8 -> 0.88
        else -> 0.72
    }

    private fun setOrderQuality(index: Int): Double = 1.0 / (1.0 + index.coerceAtLeast(0) * 0.04)

    private fun Double?.validRpe(): Double? =
        this?.takeIf { value -> value.isFinite() && value in 1.0..10.0 }

    private data class SetSignal(
        val set: WorkoutSet,
        val canonicalE1rmKg: Double,
        val effortAdjustedE1rmKg: Double,
        val quality: Double,
        val rpeAvailable: Boolean
    )

    private val SUPPORTED_REPETITIONS = 1..12
    private const val MAX_RIR = 4.0
    private const val EPLEY_DENOMINATOR = 30.0
}
