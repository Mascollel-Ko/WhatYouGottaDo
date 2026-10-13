package com.training.trackplanner.analysis.v2profile

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Adds RPE-aware descriptive evidence to the completed V2-1A profile without filtering its roster. */
object V2Stage1BAnalyzer {
    private const val RPE_WARNING = "RPE_MISSING_OR_OUT_OF_RANGE_NO_RIR_ADJUSTMENT"
    private const val HIGH_REP_WARNING = "HIGH_REP_LOAD_PROXY_UNCERTAIN"

    fun analyze(profile: V2Stage1TrainingProfile): V2Stage1BAnalysis {
        val exercises = profile.exercises.map { exercise -> analyzeExercise(exercise) }
            .sortedWith(
                compareByDescending<V2Stage1BExerciseAnalysis> { it.completedSets56d }
                    .thenBy { it.name }
            )
        return V2Stage1BAnalysis(asOf = profile.asOf, exercises = exercises)
    }

    private fun analyzeExercise(exercise: V2Stage1ExerciseSummary): V2Stage1BExerciseAnalysis {
        val isPullUp = exercise.stableKey.contains("pull_up", ignoreCase = true) ||
            exercise.stableKey.contains("pullup", ignoreCase = true) ||
            exercise.name.contains("풀업") ||
            exercise.name.contains("pull-up", ignoreCase = true) ||
            exercise.name.contains("pull up", ignoreCase = true)
        val isDumbbell = exercise.stableKey.contains("dumbbell", ignoreCase = true) ||
            exercise.name.contains("덤벨") ||
            exercise.name.contains("dumbbell", ignoreCase = true)
        val warnings = linkedSetOf<String>()
        if (isDumbbell) warnings += "DUMBBELL_UNIT_CONVENTION_UNVERIFIED"
        if (isPullUp) warnings += "BODYWEIGHT_NOT_INCLUDED_IN_LOAD_INDEX"

        val sessions = exercise.performance.evidence
            .groupBy { it.date to it.sessionKey }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (dateAndSession, sourceSets) ->
                val setEvidence = sourceSets
                    .sortedWith(compareBy<V2Stage1RecordedSetEvidence>({ it.setIndex }, { it.entryId }))
                    .map { source -> scoreEvidence(source, warnings) }
                val scoredSets = setEvidence.mapNotNull(V2Stage1BAnalyzer::scoredSet)
                V2Stage1BSessionEvidence(
                    date = dateAndSession.first,
                    sessionKey = dateAndSession.second,
                    best = bestSetWithinSession(scoredSets),
                    lastRecordedSet = setEvidence.lastOrNull(),
                    completedSetCount = setEvidence.size,
                    scoredSetCount = scoredSets.size,
                    sets = setEvidence
                )
            }

        val usableSessions = sessions.filter { it.best != null }
        val bestLoadIndex = usableSessions
            .mapNotNull(V2Stage1BSessionEvidence::best)
            .takeIf { it.isNotEmpty() }
            ?.let(::bestAcrossSessions)
        val dailyEvidence = usableSessions
            .groupBy(V2Stage1BSessionEvidence::date)
            .toSortedMap()
            .map { (date, sameDaySessions) ->
                // Session inputs are already sorted by key. A tied load index keeps the first key,
                // matching Python's max(..., key=loadIndex) stability.
                val representative = highestLoadIndexOnly(sameDaySessions.mapNotNull { it.best })
                V2Stage1BDayEvidence(
                    date = date,
                    representativeSessionKey = representative.sessionKey,
                    representativeLoadIndex = representative.loadIndex,
                    sessionKeys = sameDaySessions.map(V2Stage1BSessionEvidence::sessionKey)
                )
            }

        val trend = trend(dailyEvidence, isPullUp, isDumbbell, warnings)
        if (usableSessions.isEmpty()) warnings += "NO_EXPLICIT_POSITIVE_LOAD_FOR_PROXY"
        if (sessions.size == 1) warnings += "ONLY_ONE_SESSION"
        if (dailyEvidence.isEmpty()) warnings += "NO_TREND_DATA"

        val exposure = exercise.exposureByWindow.getValue(56)
        return V2Stage1BExerciseAnalysis(
            stableKey = exercise.stableKey,
            name = exercise.name,
            completedSets56d = exposure.completedSetCount,
            sessionCount56d = exposure.sessionCount,
            trainingDays56d = exposure.trainingDays,
            lastPerformedDate = exposure.lastCompletedDate,
            performance = V2Stage1BPerformance(
                status = if (usableSessions.isEmpty()) {
                    V2Stage1BPerformanceStatus.RAW_RECORD_ONLY
                } else {
                    V2Stage1BPerformanceStatus.ESTIMATED_WITH_WARNINGS
                },
                latestScoredSet = usableSessions.lastOrNull()?.best,
                latestRawSet = sessions.lastOrNull()?.lastRecordedSet,
                bestLoadIndex56d = bestLoadIndex
            ),
            trend = trend,
            warnings = warnings.sorted(),
            rosterEligibilityFromThisStage = V2Stage1BRosterEligibility.NOT_RESTRICTED_BY_ANALYSIS_CONFIDENCE,
            sessionEvidence = sessions,
            dayEvidence = dailyEvidence
        )
    }

    private fun scoreEvidence(
        source: V2Stage1RecordedSetEvidence,
        warnings: MutableSet<String>
    ): V2Stage1BSetEvidence {
        val normalizedRpe = source.rpe?.takeIf { it.isFinite() && it > 0.0 && it <= 10.0 }
        val reps = source.repetitions
        val weight = source.explicitPositiveWeightKg
            ?.takeIf { source.loadState == "EXPLICIT_LOAD" && it.isFinite() && it > 0.0 }
        val scoreable = weight != null && reps != null && reps in 1..15
        val exclusionReason = when {
            weight == null -> "NO_EXPLICIT_POSITIVE_LOAD"
            reps == null || reps !in 1..15 -> "REPETITIONS_OUTSIDE_1_TO_15"
            else -> null
        }
        val rir = if (scoreable && normalizedRpe != null && normalizedRpe in 6.0..10.0) {
            minOf(4.0, maxOf(0.0, 10.0 - normalizedRpe))
        } else {
            0.0
        }
        val loadIndex = if (scoreable) {
            round2(weight!! * (1.0 + (reps!! + rir) / 30.0))
        } else {
            null
        }
        if (scoreable && (normalizedRpe == null || normalizedRpe !in 6.0..10.0)) warnings += RPE_WARNING
        if (scoreable && reps!! > 10) warnings += HIGH_REP_WARNING

        return V2Stage1BSetEvidence(
            entryId = source.entryId,
            date = source.date,
            sessionKey = source.sessionKey,
            setIndex = source.setIndex,
            loadState = source.loadState,
            recordedWeightKg = source.recordedWeightKg,
            explicitPositiveWeightKg = weight,
            repetitions = reps,
            recordedRpe = source.rpe,
            normalizedRpe = normalizedRpe,
            rirAssumed = loadIndex?.let { round2(rir) },
            loadIndex = loadIndex,
            scoreExclusionReason = exclusionReason
        )
    }

    private fun scoredSet(evidence: V2Stage1BSetEvidence): V2Stage1BScoredSetEvidence? {
        val weight = evidence.explicitPositiveWeightKg ?: return null
        val reps = evidence.repetitions?.takeIf { it in 1..15 } ?: return null
        val rir = evidence.rirAssumed ?: return null
        val loadIndex = evidence.loadIndex ?: return null
        return V2Stage1BScoredSetEvidence(
            weightKg = weight,
            reps = reps,
            rpe = evidence.normalizedRpe,
            setIndex = evidence.setIndex,
            rirAssumed = rir,
            loadIndex = loadIndex,
            entryId = evidence.entryId,
            date = evidence.date,
            sessionKey = evidence.sessionKey
        )
    }

    /** Python uses (rounded index, weight, repetitions) and keeps the first full tie. */
    private fun bestSetWithinSession(sets: List<V2Stage1BScoredSetEvidence>): V2Stage1BScoredSetEvidence? {
        var best: V2Stage1BScoredSetEvidence? = null
        for (candidate in sets) {
            val current = best
            if (current == null || compareWithinSession(candidate, current) > 0) best = candidate
        }
        return best
    }

    private fun compareWithinSession(
        first: V2Stage1BScoredSetEvidence,
        second: V2Stage1BScoredSetEvidence
    ): Int = compareValuesBy(first, second,
        V2Stage1BScoredSetEvidence::loadIndex,
        V2Stage1BScoredSetEvidence::weightKg,
        V2Stage1BScoredSetEvidence::reps
    )

    /** Across sessions Python compares the index only and preserves the first tie. */
    private fun bestAcrossSessions(sets: List<V2Stage1BScoredSetEvidence>): V2Stage1BScoredSetEvidence? =
        highestLoadIndexOnly(sets)

    private fun highestLoadIndexOnly(sets: List<V2Stage1BScoredSetEvidence>): V2Stage1BScoredSetEvidence {
        require(sets.isNotEmpty())
        var best = sets.first()
        for (candidate in sets.drop(1)) if (candidate.loadIndex > best.loadIndex) best = candidate
        return best
    }

    private fun trend(
        days: List<V2Stage1BDayEvidence>,
        isPullUp: Boolean,
        isDumbbell: Boolean,
        exerciseWarnings: Set<String>
    ): V2Stage1BTrend {
        if (days.size < 2) {
            return V2Stage1BTrend(
                direction = V2Stage1BTrendDirection.NO_TREND_EVIDENCE,
                confidence = V2Stage1BTrendConfidence.INSUFFICIENT,
                changePercent = null,
                baselineIndex = null,
                recentIndex = null,
                baselineDates = emptyList(),
                recentDates = emptyList(),
                warnings = listOf(if (days.isEmpty()) "NO_SCORABLE_LOAD" else "ONLY_ONE_COMPARABLE_DATE")
            )
        }

        val sampleSizePerSide = minOf(2, days.size / 2)
        val early = days.take(sampleSizePerSide)
        val late = days.takeLast(sampleSizePerSide)
        val baseline = median(early.map(V2Stage1BDayEvidence::representativeLoadIndex))
        val recent = median(late.map(V2Stage1BDayEvidence::representativeLoadIndex))
        val delta = if (baseline > 0.0) 100.0 * (recent / baseline - 1.0) else null
        val direction = when {
            delta != null && delta >= 3.0 -> V2Stage1BTrendDirection.IMPROVING
            delta != null && delta <= -3.0 -> V2Stage1BTrendDirection.DECLINING
            else -> V2Stage1BTrendDirection.STABLE
        }
        val reasons = mutableListOf<String>()
        if (days.size < 4) reasons += "SPARSE_TREND_EVIDENCE"
        if (ChronoUnit.DAYS.between(early.first().date, late.last().date) < 14) reasons += "SHORT_TREND_SPAN"
        if (isPullUp) reasons += "EXTERNAL_LOAD_ONLY_TREND"
        if (delta != null && kotlin.math.abs(delta) >= 20.0) reasons += "LARGE_CHANGE_CHECK_RECORDING_CONSISTENCY"
        if (isDumbbell) reasons += "UNVERIFIED_DB_LOAD_CONVENTION"
        if (RPE_WARNING in exerciseWarnings) reasons += "PARTIAL_RPE_ADJUSTMENT"
        if (HIGH_REP_WARNING in exerciseWarnings) reasons += "HIGH_REP_PROXY"
        val confidence = if (reasons.isNotEmpty() || days.size < 6) {
            V2Stage1BTrendConfidence.LOW
        } else {
            V2Stage1BTrendConfidence.MODERATE
        }
        return V2Stage1BTrend(
            direction = direction,
            confidence = confidence,
            changePercent = delta?.let(::round2),
            baselineIndex = round2(baseline),
            recentIndex = round2(recent),
            baselineDates = early.map(V2Stage1BDayEvidence::date),
            recentDates = late.map(V2Stage1BDayEvidence::date),
            warnings = reasons
        )
    }

    private fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val ordered = values.sorted()
        val middle = ordered.size / 2
        return if (ordered.size % 2 == 1) ordered[middle] else (ordered[middle - 1] + ordered[middle]) / 2.0
    }

    // Python round() acts on the binary floating-point value. The Double constructor retains
    // that exact value; valueOf() would round its shortest decimal representation instead.
    private fun round2(value: Double): Double = BigDecimal(value)
        .setScale(2, RoundingMode.HALF_EVEN)
        .toDouble()
}
