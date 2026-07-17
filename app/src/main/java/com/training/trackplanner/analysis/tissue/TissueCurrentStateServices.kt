package com.training.trackplanner.analysis.tissue

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
            val childContributions = exerciseRows.groupBy { it.event.key.loadUnitStableKey }.values.map { child ->
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
                .thenByDescending { it.relativeBandPosition ?: Double.NEGATIVE_INFINITY }
                .thenByDescending(TissueDimensionState::relevantForProvenance)
                .thenByDescending { it.rawResidual.upper }
                .thenByDescending(TissueDimensionState::latestPositiveContributionTime)
                .thenBy { it.key.loadUnitStableKey }
        )

    fun joints(states: List<TissueJointComplexSummary>): List<TissueJointComplexSummary> =
        states.sortedWith(
            compareByDescending<TissueJointComplexSummary> { it.status.severity }
                .thenByDescending { it.relativeBandPosition ?: Double.NEGATIVE_INFINITY }
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
        effectiveBaselinesByUnit: Map<String, TissueEffectiveBaseline> = emptyMap(),
        historyByUnit: Map<String, List<Double>> = emptyMap(),
        symptomOverrides: Map<String, TissueSymptomOverride> = emptyMap(),
        diagnostics: List<String> = emptyList()
    ): TissueCurrentState {
        val deduplicated = residuals.groupBy { it.event.eventId }.values.map { duplicates ->
            duplicates.maxBy { it.currentResidualRange.upper }
        }
        val byUnit = deduplicated.groupBy { it.event.key.loadUnitStableKey }
        val states = catalog.loadUnits.values.map { unit ->
            val rows = byUnit[unit.stableKey].orEmpty()
            state(
                unit = unit,
                rows = rows,
                recentResidualHistory = historyByUnit[unit.stableKey].orEmpty(),
                baseline = effectiveBaselinesByUnit[unit.stableKey],
                symptomOverride = symptomOverrides[unit.stableKey] ?: TissueSymptomOverride.NONE
            )
        }
        val rankedLoadUnits = TissueRankingService.loadUnits(states)
        val joints = jointSummaries(rankedLoadUnits, deduplicated)
        val worst = rankedLoadUnits.firstOrNull()?.status ?: TissueCanonicalStatus.UNAVAILABLE
        return TissueCurrentState(
            loadUnits = rankedLoadUnits,
            jointComplexes = joints,
            ofiSummary = TissueOfiSummary(worst, joints.take(3)),
            baselineProvenance = provenance(rankedLoadUnits),
            diagnostics = buildList {
                addAll(diagnostics)
                if (residuals.size != deduplicated.size) {
                    add("Duplicate derived event IDs were collapsed without increasing debt.")
                }
            }.distinct()
        )
    }

    private fun state(
        unit: TissueRcvLoadUnit,
        rows: List<TissueEventResidual>,
        recentResidualHistory: List<Double>,
        baseline: TissueEffectiveBaseline?,
        symptomOverride: TissueSymptomOverride
    ): TissueDimensionState {
        val rawResidual = TissueResidualRange(
            rows.sumOf { it.currentResidualRange.lower },
            rows.sumOf { it.currentResidualRange.upper }
        )
        val classification = TissueRelativeStateClassifier.classify(rawResidual.upper, baseline, symptomOverride)
        val channels = rows.flatMap { it.channelResiduals.keys }.associateWith { channel ->
            TissueResidualRange(
                rows.sumOf { it.channelResiduals[channel]?.lower ?: 0.0 },
                rows.sumOf { it.channelResiduals[channel]?.upper ?: 0.0 }
            )
        }
        val relevant = rawResidual.upper > (baseline?.boundaries?.meaningfulFloor ?: 0.0) ||
            (baseline?.calibrationWeight?.weightedDistinctExposureDays ?: 0.0) > 0.0 ||
            baseline?.personalBaseline?.isValid == true
        return TissueDimensionState(
            key = TissueRcvLoadKey(unit.stableKey, "UNIT_TOTAL"),
            loadUnitName = unit.nameKo,
            educationalInfo = catalog.educationalInfo.getValue(unit.stableKey),
            jointComplexStableKey = unit.jointComplexStableKey,
            tissueClass = unit.tissueClass,
            rawResidual = rawResidual,
            recentResidualHistory = recentResidualHistory,
            channelResiduals = channels,
            status = classification.status,
            relativeBandPosition = classification.relativeBandPosition,
            effectiveWeight = baseline?.effectiveWeight ?: 0.0,
            baselineProvenance = baseline?.provenance ?: TissueBaselineProvenance.PRIOR_ONLY,
            relevantForProvenance = relevant,
            latestPositiveContributionTime = rows.filter { it.currentResidualRange.upper > 0.0 }
                .maxOfOrNull { it.event.performedTime.latestEpochMillis ?: Long.MIN_VALUE } ?: Long.MIN_VALUE,
            contributors = TissueContributorService.forLoadUnit(rows),
            timestampPrecisions = rows.mapTo(linkedSetOf()) { it.event.performedTime.precision },
            evidenceGrades = rows.mapTo(linkedSetOf()) { it.event.evidenceGrade },
            symptomOverride = classification.symptomOverride,
            diagnostics = buildList {
                addAll(rows.flatMap(TissueEventResidual::diagnostics))
                addAll(baseline?.diagnostics.orEmpty().map(TissueBaselineDiagnostic::message))
                addAll(classification.diagnostics)
            }.distinct()
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
                educationalInfo = catalog.educationalInfo.getValue(joint.stableKey),
                status = highest?.status ?: TissueCanonicalStatus.UNAVAILABLE,
                relativeBandPosition = children.mapNotNull(TissueDimensionState::relativeBandPosition).maxOrNull(),
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

    private fun provenance(states: List<TissueDimensionState>): TissueBaselineProvenance {
        val relevant = states.filter(TissueDimensionState::relevantForProvenance)
        if (relevant.isEmpty()) return TissueBaselineProvenance.PRIOR_ONLY
        return when {
            relevant.all { it.effectiveWeight <= TissueEffectiveBaselinePolicy.WEIGHT_EPSILON } ->
                TissueBaselineProvenance.PRIOR_ONLY
            relevant.all { it.effectiveWeight >= 1.0 - TissueEffectiveBaselinePolicy.WEIGHT_EPSILON } ->
                TissueBaselineProvenance.PERSONAL_ONLY
            else -> TissueBaselineProvenance.MIXED
        }
    }
}
