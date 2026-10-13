package com.training.trackplanner.analysis.v2profile

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.isAnalysisEligibleCompletedSet
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object V2Stage1TrainingProfileAnalyzer {
    private val windowDays = listOf(7, 28, 56)

    /** Adapter over existing records. It is read-only and does not change Room or backup data. */
    fun observationsFrom(
        records: List<WorkoutEntryWithSets>,
        exercisesByStableKey: Map<String, Exercise>
    ): List<V2Stage1SetObservation> = records.flatMap { record ->
        val entry = record.entry
        val metadata = exercisesByStableKey[entry.exerciseStableKey]
        val key = entry.exerciseStableKey.ifBlank { entry.exerciseName }
        val name = entry.exerciseName.ifBlank { metadata?.name.orEmpty() }.ifBlank { key }
        val category = entry.category.ifBlank { metadata?.category.orEmpty() }
        record.sets.map { set ->
            V2Stage1SetObservation(
                entryId = entry.id,
                date = entry.date,
                sessionStableKey = entry.sessionStableKey,
                backupSourceId = entry.backupSourceId,
                exerciseStableKey = key,
                exerciseName = name,
                category = category,
                setIndex = set.setIndex,
                confirmed = set.confirmed,
                analysisEligibleCompletedSet = set.isAnalysisEligibleCompletedSet(),
                loadState = set.loadState.name,
                weightKg = set.weightKg,
                repetitions = set.reps,
                // Current backups and app analysis use set RPE first, then the entry-level RPE.
                // The fallback also preserves the source analyzer's per-set RPE interpretation.
                rpe = set.rpe ?: entry.rpe
            )
        }
    }

    fun analyzeRecords(
        records: List<WorkoutEntryWithSets>,
        exercisesByStableKey: Map<String, Exercise>,
        asOf: LocalDate
    ): V2Stage1TrainingProfile = analyze(
        observations = observationsFrom(records, exercisesByStableKey),
        exercisesByStableKey = exercisesByStableKey,
        asOf = asOf
    )

    /** Pure calculation entry point for deterministic tests and non-Room callers. */
    fun analyze(
        observations: List<V2Stage1SetObservation>,
        exercisesByStableKey: Map<String, Exercise> = emptyMap(),
        asOf: LocalDate
    ): V2Stage1TrainingProfile {
        var unconfirmed = 0
        var calibrationPending = 0
        var invalidDate = 0
        var future = 0
        var olderThan56 = 0
        val completedThroughCutoff = mutableListOf<IncludedSet>()

        observations.forEach { observation ->
            if (!observation.confirmed) {
                unconfirmed++
                return@forEach
            }
            if (!observation.analysisEligibleCompletedSet) {
                calibrationPending++
                return@forEach
            }
            val date = runCatching { LocalDate.parse(observation.date) }.getOrNull()
            if (date == null) {
                invalidDate++
                return@forEach
            }
            if (date.isAfter(asOf)) {
                future++
                return@forEach
            }
            if (date.isBefore(asOf.minusDays(55))) olderThan56++
            completedThroughCutoff += IncludedSet(observation, date)
        }

        val windows = windowDays.map { days ->
            val start = asOf.minusDays((days - 1).toLong())
            val rows = completedThroughCutoff.filter { !it.date.isBefore(start) }
            summarizeWindow(days, start, asOf, rows)
        }
        val exercises = summarizeExercises(
            rows = completedThroughCutoff.filter { !it.date.isBefore(asOf.minusDays(55)) && it.isResistance },
            exercisesByStableKey = exercisesByStableKey,
            asOf = asOf
        )

        return V2Stage1TrainingProfile(
            asOf = asOf,
            completedSetCount = completedThroughCutoff.size,
            exclusions = V2Stage1Exclusions(
                unconfirmedSets = unconfirmed,
                loadCalibrationPendingSets = calibrationPending,
                invalidDateCompletedSets = invalidDate,
                futureCompletedSets = future,
                olderThan56DaysCompletedSets = olderThan56
            ),
            windows = windows,
            exercises = exercises
        )
    }

    private fun summarizeWindow(
        days: Int,
        start: LocalDate,
        end: LocalDate,
        rows: List<IncludedSet>
    ): V2Stage1WindowSummary {
        val resistanceRows = rows.filter(IncludedSet::isResistance)
        val allDates = rows.map(IncludedSet::date).distinct().sorted()
        val resistanceDates = resistanceRows.map(IncludedSet::date).distinct().sorted()
        val sessionEvidence = rows
            .groupBy { it.date to it.sessionKey }
            .map { (dateAndKey, sessionRows) ->
                V2Stage1SessionEvidence(
                    date = dateAndKey.first,
                    sessionKey = dateAndKey.second,
                    sourceEntryIds = sessionRows.map { it.observation.entryId }.distinct().sorted()
                )
            }
            .sortedWith(compareBy<V2Stage1SessionEvidence>({ it.date }, { it.sessionKey }))
        val dailyEvidence = allDates.map { date ->
            val dayRows = rows.filter { it.date == date }
            V2Stage1DailyEvidence(
                date = date,
                completedSetCount = dayRows.size,
                resistanceSetCount = dayRows.count(IncludedSet::isResistance),
                sessionKeys = sessionEvidence.filter { it.date == date }.map(V2Stage1SessionEvidence::sessionKey)
            )
        }
        val exerciseFrequencies = resistanceRows
            .groupBy(IncludedSet::exerciseKey)
            .map { (key, exerciseRows) ->
                V2Stage1ExerciseFrequency(
                    stableKey = key,
                    name = exerciseRows.maxWithOrNull(compareBy<IncludedSet>({ it.date }, { it.observation.setIndex }, { it.observation.entryId }))
                        ?.observation?.exerciseName.orEmpty(),
                    completedSetCount = exerciseRows.size
                )
            }
            .sortedWith(compareByDescending<V2Stage1ExerciseFrequency> { it.completedSetCount }.thenBy { it.name })

        return V2Stage1WindowSummary(
            days = days,
            start = start,
            end = end,
            completedSetCount = rows.size,
            resistanceSetCount = resistanceRows.size,
            otherSetCount = rows.size - resistanceRows.size,
            resistanceWeeklySetEquivalent = round2(resistanceRows.size * 7.0 / days),
            allWeeklySetEquivalent = round2(rows.size * 7.0 / days),
            activeDays = allDates.size,
            resistanceDays = resistanceDates.size,
            sessionCount = sessionEvidence.size,
            resistanceExerciseCount = exerciseFrequencies.size,
            contributingDates = allDates,
            contributingResistanceDates = resistanceDates,
            dailyEvidence = dailyEvidence,
            sessionEvidence = sessionEvidence,
            resistanceExerciseFrequencies = exerciseFrequencies
        )
    }

    private fun summarizeExercises(
        rows: List<IncludedSet>,
        exercisesByStableKey: Map<String, Exercise>,
        asOf: LocalDate
    ): List<V2Stage1ExerciseSummary> = rows
        .groupBy(IncludedSet::exerciseKey)
        .map { (stableKey, exerciseRows) ->
            val ordered = exerciseRows.sortedWith(
                compareBy<IncludedSet>({ it.date }, { it.observation.setIndex }, { it.observation.entryId })
            )
            val latest = ordered.last()
            val metadata = exercisesByStableKey[stableKey]
            val evidence = ordered.map(IncludedSet::performanceEvidence)
            val loadEvidence = evidence.filter { it.explicitPositiveWeightKg != null }
            val rpes = evidence.mapNotNull { it.rpe?.takeIf { value -> value.isFinite() && value > 0.0 } }
            val exposureByWindow = windowDays.associateWith { days ->
                val start = asOf.minusDays((days - 1).toLong())
                exposure(ordered.filter { !it.date.isBefore(start) }, asOf)
            }
            val performance = V2Stage1ExercisePerformance(
                latestExplicitLoad = loadEvidence.lastOrNull(),
                highestLoggedLoad = loadEvidence.maxByOrNull { it.explicitPositiveWeightKg!! },
                recordedRpeSetCount = rpes.size,
                meanRecordedRpe = if (rpes.isEmpty()) null else round2(rpes.average()),
                evidence = evidence
            )
            V2Stage1ExerciseSummary(
                stableKey = stableKey,
                name = latest.observation.exerciseName,
                category = latest.observation.category,
                metadata = metadata?.toV2Stage1Metadata(),
                exposureByWindow = exposureByWindow,
                performance = performance,
                maintenancePriorityEvidence = V2Stage1MaintenancePriorityEvidence(
                    exposureByWindow = exposureByWindow
                )
            )
        }
        .sortedWith(
            compareByDescending<V2Stage1ExerciseSummary> { it.exposureByWindow.getValue(56).completedSetCount }
                .thenByDescending { it.exposureByWindow.getValue(28).completedSetCount }
                .thenBy { it.name }
        )

    private fun exposure(rows: List<IncludedSet>, asOf: LocalDate): V2Stage1ExerciseExposure {
        val dates = rows.map(IncludedSet::date).distinct().sorted()
        val last = dates.lastOrNull()
        return V2Stage1ExerciseExposure(
            completedSetCount = rows.size,
            sessionCount = rows.map { it.date to it.sessionKey }.distinct().size,
            trainingDays = dates.size,
            lastCompletedDate = last,
            daysSinceLastCompleted = last?.let { ChronoUnit.DAYS.between(it, asOf).toInt() }
        )
    }

    private fun Exercise.toV2Stage1Metadata() = V2Stage1ExerciseMetadata(
        activityKind = activityKind,
        planningEligibility = planningEligibility,
        movementPattern = movementPattern,
        movementCategory = movementCategory,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        equipment = equipment,
        equipmentTags = equipmentTags,
        metadataConfidence = metadataConfidence,
        needsReview = needsReview
    )

    private fun round2(value: Double): Double = BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_EVEN)
        .toDouble()

    private data class IncludedSet(
        val observation: V2Stage1SetObservation,
        val date: LocalDate
    ) {
        val isResistance: Boolean
            get() = observation.category == RESISTANCE_CATEGORY
        val exerciseKey: String
            get() = observation.exerciseStableKey.ifBlank { observation.exerciseName }
        val sessionKey: String
            get() = observation.sessionStableKey.orEmpty().ifBlank {
                observation.backupSourceId.orEmpty().ifBlank { "entry:${observation.entryId}" }
            }

        val performanceEvidence: V2Stage1RecordedSetEvidence
            get() {
                val rawWeight = observation.weightKg.takeIf { it.isFinite() }
                val explicitWeight = rawWeight
                    ?.takeIf { observation.loadState == ProgramLoadState.EXPLICIT_LOAD.name && it > 0.0 }
                val repetitions = observation.repetitions.takeIf { it > 0 }
                val rpe = observation.rpe?.takeIf { it.isFinite() && it > 0.0 }
                return V2Stage1RecordedSetEvidence(
                    entryId = observation.entryId,
                    date = date,
                    sessionKey = sessionKey,
                    setIndex = observation.setIndex,
                    loadState = observation.loadState,
                    recordedWeightKg = rawWeight,
                    explicitPositiveWeightKg = explicitWeight,
                    repetitions = repetitions,
                    rpe = rpe
                )
            }
    }

    private const val RESISTANCE_CATEGORY = "근력운동"
}
