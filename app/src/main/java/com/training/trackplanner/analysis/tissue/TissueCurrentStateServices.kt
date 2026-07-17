package com.training.trackplanner.analysis.tissue

object TissueCalibrationPolicy {
    const val ELIGIBILITY_DAYS = 56L

    fun classify(
        currentResidual: Double,
        comparableHistory: List<Double>,
        observationDays: Long,
        symptomOverride: TissueSymptomOverride
    ): TissueCalibrationResult {
        val modeled = when {
            observationDays < ELIGIBILITY_DAYS -> TissueCalibrationResult(
                TissueCanonicalStatus.CALIBRATING,
                null,
                observationDays,
                symptomOverride,
                listOf("Personal status requires 56 observation days.")
            )
            comparableHistory.isEmpty() -> TissueCalibrationResult(
                TissueCanonicalStatus.UNAVAILABLE,
                null,
                observationDays,
                symptomOverride,
                listOf("No comparable personal history is available.")
            )
            else -> {
                val percentile = comparableHistory.count { it <= currentResidual } * 100.0 / comparableHistory.size
                val status = when {
                    percentile > 90.0 -> TissueCanonicalStatus.VERY_HIGH
                    percentile > 75.0 -> TissueCanonicalStatus.HIGH
                    percentile > 50.0 -> TissueCanonicalStatus.MODERATE
                    else -> TissueCanonicalStatus.LOW
                }
                TissueCalibrationResult(
                    status,
                    percentile,
                    observationDays,
                    symptomOverride,
                    listOf("Score is position within the user's comparable load history, not injury probability.")
                )
            }
        }
        val overrideStatus = when (symptomOverride) {
            TissueSymptomOverride.NONE -> null
            TissueSymptomOverride.CAUTION -> TissueCanonicalStatus.HIGH
            TissueSymptomOverride.BLOCK -> TissueCanonicalStatus.VERY_HIGH
        }
        return if (overrideStatus != null && overrideStatus.severity > modeled.status.severity) {
            modeled.copy(
                status = overrideStatus,
                diagnostics = modeled.diagnostics + "Symptom/function override takes immediate precedence."
            )
        } else {
            modeled
        }
    }
}

object TissueContributorService {
    fun forLoadUnit(residuals: List<TissueEventResidual>): List<TissueExerciseContribution> {
        val ranked = residuals.groupBy { it.event.exerciseStableKey }.map { (stableKey, rows) ->
            TissueExerciseContribution(
                exerciseStableKey = stableKey,
                exerciseName = rows.first().event.exerciseName,
                currentContribution = rows.sumOf { it.currentResidualRange.upper },
                latestContributingEventTime = rows.maxOf { it.event.performedTime.latestEpochMillis ?: Long.MIN_VALUE }
            )
        }.sortedWith(
            compareByDescending<TissueExerciseContribution>(TissueExerciseContribution::currentContribution)
                .thenByDescending(TissueExerciseContribution::latestContributingEventTime)
                .thenBy(TissueExerciseContribution::exerciseStableKey)
        )
        if (ranked.size < 2) return ranked
        return if (ranked[0].currentContribution >= ranked[1].currentContribution * 1.5) {
            ranked.take(1)
        } else {
            ranked.take(2)
        }
    }

    fun forJoint(residuals: List<TissueEventResidual>): List<TissueExerciseContribution> =
        residuals.groupBy { it.event.exerciseStableKey }.map { (stableKey, exerciseRows) ->
            val childContributions = exerciseRows.groupBy { it.event.key }.values.map { child ->
                child.sumOf { it.currentResidualRange.upper }
            }.sortedDescending()
            TissueExerciseContribution(
                exerciseStableKey = stableKey,
                exerciseName = exerciseRows.first().event.exerciseName,
                currentContribution = childContributions.firstOrNull() ?: 0.0,
                latestContributingEventTime = exerciseRows.maxOf {
                    it.event.performedTime.latestEpochMillis ?: Long.MIN_VALUE
                }
            ) to Pair(childContributions.count { it > 0.0 }, childContributions.take(3).sum())
        }.sortedWith(
            compareByDescending<Pair<TissueExerciseContribution, Pair<Int, Double>>> { it.first.currentContribution }
                .thenByDescending { it.second.first }
                .thenByDescending { it.second.second }
                .thenByDescending { it.first.latestContributingEventTime }
                .thenBy { it.first.exerciseStableKey }
        ).map { it.first }.take(2)
}

object TissueRankingService {
    fun loadUnits(states: List<TissueDimensionState>): List<TissueDimensionState> =
        states.sortedWith(
            compareByDescending<TissueDimensionState> { it.status.severity }
                .thenByDescending { it.normalizedScore ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.rawResidual.upper }
                .thenByDescending(TissueDimensionState::latestPositiveContributionTime)
                .thenBy { it.key.loadUnitStableKey }
                .thenBy { it.key.loadDimension }
        )

    fun joints(states: List<TissueJointComplexSummary>): List<TissueJointComplexSummary> =
        states.sortedWith(
            compareByDescending<TissueJointComplexSummary> { it.status.severity }
                .thenByDescending { it.displayScore ?: Double.NEGATIVE_INFINITY }
                .thenByDescending(TissueJointComplexSummary::highOrVeryHighChildCount)
                .thenByDescending { it.highestChild?.rawResidual?.upper ?: 0.0 }
                .thenByDescending(TissueJointComplexSummary::latestPositiveContributionTime)
                .thenBy(TissueJointComplexSummary::jointComplexStableKey)
        )
}

class TissueCurrentStateAggregator(
    private val catalog: TissueRcvCatalog
) {
    fun aggregate(
        residuals: List<TissueEventResidual>,
        observationDays: Long,
        historyByKey: Map<TissueRcvLoadKey, List<Double>> = emptyMap(),
        symptomOverrides: Map<TissueRcvLoadKey, TissueSymptomOverride> = emptyMap()
    ): TissueCurrentState {
        val deduplicated = residuals.groupBy { it.event.eventId }.values.map { duplicates ->
            duplicates.maxBy { it.currentResidualRange.upper }
        }
        val byKey = deduplicated.groupBy { it.event.key }
        val states = mutableListOf<TissueDimensionState>()
        byKey.forEach { (key, rows) ->
            val calibration = TissueCalibrationPolicy.classify(
                currentResidual = rows.sumOf { it.currentResidualRange.upper },
                comparableHistory = historyByKey[key].orEmpty(),
                observationDays = observationDays,
                symptomOverride = symptomOverrides[key] ?: TissueSymptomOverride.NONE
            )
            states += state(key, rows, calibration)
        }
        catalog.loadUnits.values.filter { unit ->
            states.none { it.key.loadUnitStableKey == unit.stableKey }
        }.forEach { unit ->
            val key = TissueRcvLoadKey(unit.stableKey, "UNOBSERVED")
            val calibration = TissueCalibrationPolicy.classify(
                currentResidual = 0.0,
                comparableHistory = historyByKey[key].orEmpty(),
                observationDays = observationDays,
                symptomOverride = symptomOverrides[key] ?: TissueSymptomOverride.NONE
            )
            states += TissueDimensionState(
                key = key,
                jointComplexStableKey = unit.jointComplexStableKey,
                tissueClass = unit.tissueClass,
                rawResidual = TissueResidualRange(0.0, 0.0),
                channelResiduals = emptyMap(),
                status = calibration.status,
                normalizedScore = calibration.normalizedScore,
                latestPositiveContributionTime = Long.MIN_VALUE,
                contributors = emptyList(),
                timestampPrecisions = emptySet(),
                evidenceGrades = emptySet(),
                symptomOverride = calibration.symptomOverride,
                diagnostics = calibration.diagnostics
            )
        }
        val rankedLoadUnits = TissueRankingService.loadUnits(states)
        val joints = jointSummaries(rankedLoadUnits, deduplicated)
        val worst = rankedLoadUnits.firstOrNull()?.status ?: TissueCanonicalStatus.UNAVAILABLE
        return TissueCurrentState(
            loadUnits = rankedLoadUnits,
            jointComplexes = joints,
            ofiSummary = TissueOfiSummary(worst, joints.take(3)),
            diagnostics = if (residuals.size == deduplicated.size) emptyList() else {
                listOf("Duplicate derived event IDs were collapsed without increasing debt.")
            }
        )
    }

    private fun state(
        key: TissueRcvLoadKey,
        rows: List<TissueEventResidual>,
        calibration: TissueCalibrationResult
    ): TissueDimensionState {
        val channels = rows.flatMap { it.channelResiduals.keys }.associateWith { channel ->
            TissueResidualRange(
                rows.sumOf { it.channelResiduals[channel]?.lower ?: 0.0 },
                rows.sumOf { it.channelResiduals[channel]?.upper ?: 0.0 }
            )
        }
        return TissueDimensionState(
            key = key,
            jointComplexStableKey = rows.first().event.jointComplexStableKey,
            tissueClass = rows.first().event.tissueClass,
            rawResidual = TissueResidualRange(
                rows.sumOf { it.currentResidualRange.lower },
                rows.sumOf { it.currentResidualRange.upper }
            ),
            channelResiduals = channels,
            status = calibration.status,
            normalizedScore = calibration.normalizedScore,
            latestPositiveContributionTime = rows.filter { it.currentResidualRange.upper > 0.0 }
                .maxOfOrNull { it.event.performedTime.latestEpochMillis ?: Long.MIN_VALUE } ?: Long.MIN_VALUE,
            contributors = TissueContributorService.forLoadUnit(rows),
            timestampPrecisions = rows.mapTo(linkedSetOf()) { it.event.performedTime.precision },
            evidenceGrades = rows.mapTo(linkedSetOf()) { it.event.evidenceGrade },
            symptomOverride = calibration.symptomOverride,
            diagnostics = (rows.flatMap(TissueEventResidual::diagnostics) + calibration.diagnostics).distinct()
        )
    }

    private fun jointSummaries(
        loadUnits: List<TissueDimensionState>,
        residuals: List<TissueEventResidual>
    ): List<TissueJointComplexSummary> {
        val byJoint = loadUnits.groupBy(TissueDimensionState::jointComplexStableKey)
        return TissueRankingService.joints(catalog.jointComplexes.values.map { joint ->
            val children = TissueRankingService.loadUnits(byJoint[joint.stableKey].orEmpty())
            val highest = children.firstOrNull()
            val jointResiduals = residuals.filter { it.event.jointComplexStableKey == joint.stableKey }
            TissueJointComplexSummary(
                jointComplexStableKey = joint.stableKey,
                nameKo = joint.nameKo,
                status = highest?.status ?: TissueCanonicalStatus.UNAVAILABLE,
                displayScore = children.mapNotNull(TissueDimensionState::normalizedScore).maxOrNull(),
                highOrVeryHighChildCount = children.count {
                    it.status == TissueCanonicalStatus.HIGH || it.status == TissueCanonicalStatus.VERY_HIGH
                },
                highestChild = highest,
                childStates = children,
                contributors = TissueContributorService.forJoint(jointResiduals),
                latestPositiveContributionTime = children.maxOfOrNull(
                    TissueDimensionState::latestPositiveContributionTime
                ) ?: Long.MIN_VALUE
            )
        })
    }
}
