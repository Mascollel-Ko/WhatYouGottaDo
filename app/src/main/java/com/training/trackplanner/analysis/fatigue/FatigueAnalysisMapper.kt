package com.training.trackplanner.analysis.fatigue

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object FatigueAnalysisMapper {
    fun map(
        history: List<DailyFatigueResult>,
        period: FatigueAnalysisPeriod = FatigueAnalysisPeriod.TWO_WEEKS,
        selectedTargets: Set<FatigueTarget> = setOf(FatigueTarget.OVERALL),
        contributionTarget: FatigueTarget = FatigueTarget.OVERALL,
        grouping: ContributionGrouping = ContributionGrouping.REDUNDANCY_GROUP,
        selectedSourceKeys: Set<String> = emptySet(),
        defaultSourcesWhenEmpty: Boolean = true,
        projectedOverallFatigueScore: Double? = null,
        hasRemainingUnconfirmedWork: Boolean = false
    ): FatigueAnalysisUiState {
        if (history.isEmpty()) {
            return FatigueAnalysisUiState(
                detail = FatigueDetailUiState(
                    selectedPeriod = period,
                    selectedFatigueTargets = selectedTargets.ifEmpty { setOf(FatigueTarget.OVERALL) },
                    contributionTarget = contributionTarget,
                    contributionGrouping = grouping,
                    usesWeeklyAggregation = period.usesWeeklyAggregation
                ),
                isLoading = false
            )
        }

        val sorted = history.sortedBy { it.state.date }
        val endDate = sorted.last().state.date
        val periodStartDate = endDate.minusDays(period.weeks * 7L - 1L)
        val firstWorkoutDate = sorted
            .firstOrNull { it.state.confirmedTrainingLoad > 0.0 }
            ?.state
            ?.date
            ?: return emptyState(period, selectedTargets, contributionTarget, grouping)
        val chartStartDate = maxOf(periodStartDate, firstWorkoutDate)
        val window = sorted.filter { it.state.date >= chartStartDate }
        val hasCalculatedFatigue = window.any { result ->
            result.state.confirmedTrainingLoad > 0.0 ||
                result.state.overallFatigueIndex > 0 ||
                result.state.neuromuscularScore > 0 ||
                result.state.systemicMuscularScore > 0 ||
                result.state.localMuscularScore > 0 ||
                result.state.jointTendonImpactScore > 0 ||
                result.state.movementFocusScore > 0 ||
                result.state.recoveryPressureScore > 0
        }
        if (!hasCalculatedFatigue) {
            return emptyState(period, selectedTargets, contributionTarget, grouping)
        }
        val trendSeries = FatigueTarget.entries.map { target ->
            FatigueSeries(
                key = target.name,
                label = target.label,
                points = aggregate(
                    window.map { result -> FatigueTimePoint(result.state.date, result.state.valueFor(target)) },
                    period.usesWeeklyAggregation
                )
            )
        }
        val contributionSeries = contributionSeries(window, contributionTarget, grouping, period)
        val defaultSourceKeys = contributionSeries
            .take(DEFAULT_CONTRIBUTOR_COUNT)
            .map { it.sourceKey }
            .toSet()
        val validSelectedSources = selectedSourceKeys.intersect(contributionSeries.map { it.sourceKey }.toSet())
        val effectiveSelectedSources = if (validSelectedSources.isEmpty() && defaultSourcesWhenEmpty) {
            defaultSourceKeys
        } else {
            validSelectedSources
        }
        val axisItems = axisItems(window.last().state)
        val actualOfiSeries = trendSeries.first { it.key == FatigueTarget.OVERALL.name }.points

        return FatigueAnalysisUiState(
            simple = FatigueSimpleUiState(
                ofiSeries = actualOfiSeries,
                projectedOfiOverlay = projectedOverlay(
                    actual = actualOfiSeries,
                    projectedScore = projectedOverallFatigueScore,
                    enabled = hasRemainingUnconfirmedWork
                ),
                highLoadItems = axisItems.sortedByDescending { it.score }.filter { it.score > 0 }.take(2),
                availableLoadItems = axisItems.sortedBy { it.score }.take(2)
            ),
            detail = FatigueDetailUiState(
                selectedPeriod = period,
                fatigueTrendSeries = trendSeries,
                selectedFatigueTargets = selectedTargets.ifEmpty { setOf(FatigueTarget.OVERALL) },
                contributionTarget = contributionTarget,
                contributionGrouping = grouping,
                contributionSeries = contributionSeries,
                selectedContributionSourceKeys = effectiveSelectedSources,
                defaultContributionSourceKeys = defaultSourceKeys,
                usesWeeklyAggregation = period.usesWeeklyAggregation
            ),
            isLoading = false
        )
    }

    private fun contributionSeries(
        window: List<DailyFatigueResult>,
        target: FatigueTarget,
        grouping: ContributionGrouping,
        period: FatigueAnalysisPeriod
    ): List<FatigueContributionSeries> {
        val sourceKeys = window.flatMap { result ->
            result.groupStates.filter { it.groupType == grouping.groupType }.map { it.groupKey }
        }.distinct()
        val unsorted = sourceKeys.map { sourceKey ->
            val daily = window.map { result ->
                val value = result.groupStates
                    .filter { it.groupType == grouping.groupType && it.groupKey == sourceKey }
                    .sumOf { it.valueFor(target) }
                FatigueTimePoint(result.state.date, value)
            }
            FatigueContributionSeries(
                sourceKey = sourceKey,
                sourceLabel = sourceKey.toDisplayLabel(),
                target = target,
                points = aggregate(daily, period.usesWeeklyAggregation),
                periodContributionPercent = 0
            )
        }.filter { series -> series.points.any { it.value > 0.0 } }
            .sortedByDescending { series -> series.points.sumOf { it.value } }
        val totalContribution = unsorted.sumOf { series -> series.points.sumOf { it.value } }
        return unsorted.map { series ->
            val seriesContribution = series.points.sumOf { it.value }
            series.copy(
                periodContributionPercent = if (totalContribution > 0.0) {
                    ((seriesContribution / totalContribution) * 100.0).toInt().coerceIn(0, 100)
                } else {
                    0
                }
            )
        }
    }

    private fun emptyState(
        period: FatigueAnalysisPeriod,
        selectedTargets: Set<FatigueTarget>,
        contributionTarget: FatigueTarget,
        grouping: ContributionGrouping
    ): FatigueAnalysisUiState = FatigueAnalysisUiState(
        detail = FatigueDetailUiState(
            selectedPeriod = period,
            selectedFatigueTargets = selectedTargets.ifEmpty { setOf(FatigueTarget.OVERALL) },
            contributionTarget = contributionTarget,
            contributionGrouping = grouping,
            usesWeeklyAggregation = period.usesWeeklyAggregation
        ),
        isLoading = false
    )

    internal fun aggregate(points: List<FatigueTimePoint>, weekly: Boolean): List<FatigueTimePoint> {
        if (!weekly) return points
        return points.groupBy { point ->
            point.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }.toSortedMap().map { (weekStart, weekPoints) ->
            FatigueTimePoint(weekStart, weekPoints.map { it.value }.average())
        }
    }

    private fun projectedOverlay(
        actual: List<FatigueTimePoint>,
        projectedScore: Double?,
        enabled: Boolean
    ): List<FatigueTimePoint> {
        if (!enabled || actual.size < 2 || projectedScore == null) return emptyList()
        val previous = actual[actual.lastIndex - 1]
        val current = actual.last()
        return listOf(previous, current.copy(value = projectedScore.coerceIn(0.0, 100.0)))
    }

    private fun axisItems(state: DailyFatigueState): List<FatigueLoadItem> = listOf(
        FatigueLoadItem(FatigueTarget.NEUROMUSCULAR.name, FatigueTarget.NEUROMUSCULAR.label, state.neuromuscularScore),
        FatigueLoadItem(FatigueTarget.SYSTEMIC_MUSCULAR.name, FatigueTarget.SYSTEMIC_MUSCULAR.label, state.systemicMuscularScore),
        FatigueLoadItem(FatigueTarget.LOCAL_MUSCULAR.name, FatigueTarget.LOCAL_MUSCULAR.label, state.localMuscularScore),
        FatigueLoadItem(FatigueTarget.JOINT_TENDON_IMPACT.name, FatigueTarget.JOINT_TENDON_IMPACT.label, state.jointTendonImpactScore),
        FatigueLoadItem(FatigueTarget.MOVEMENT_FOCUS.name, FatigueTarget.MOVEMENT_FOCUS.label, state.movementFocusScore),
        FatigueLoadItem(FatigueTarget.RECOVERY_PRESSURE.name, FatigueTarget.RECOVERY_PRESSURE.label, state.recoveryPressureScore)
    )

    private fun DailyFatigueState.valueFor(target: FatigueTarget): Double = when (target) {
        FatigueTarget.OVERALL -> overallFatigueIndex.toDouble()
        FatigueTarget.NEUROMUSCULAR -> neuromuscularScore.toDouble()
        FatigueTarget.SYSTEMIC_MUSCULAR -> systemicMuscularScore.toDouble()
        FatigueTarget.LOCAL_MUSCULAR -> localMuscularScore.toDouble()
        FatigueTarget.JOINT_TENDON_IMPACT -> jointTendonImpactScore.toDouble()
        FatigueTarget.MOVEMENT_FOCUS -> movementFocusScore.toDouble()
        FatigueTarget.RECOVERY_PRESSURE -> recoveryPressureScore.toDouble()
    }

    private fun GroupFatigueState.valueFor(target: FatigueTarget): Double = when (target) {
        FatigueTarget.OVERALL -> listOf(
            neuromuscularFatigue,
            systemicMuscularFatigue,
            localFatigue,
            jointTendonImpactFatigue,
            movementFocusFatigue,
            recoveryPressure
        ).average()
        FatigueTarget.NEUROMUSCULAR -> neuromuscularFatigue
        FatigueTarget.SYSTEMIC_MUSCULAR -> systemicMuscularFatigue
        FatigueTarget.LOCAL_MUSCULAR -> localFatigue
        FatigueTarget.JOINT_TENDON_IMPACT -> jointTendonImpactFatigue
        FatigueTarget.MOVEMENT_FOCUS -> movementFocusFatigue
        FatigueTarget.RECOVERY_PRESSURE -> recoveryPressure
    }

    private fun String.toDisplayLabel(): String =
        lowercase().split('_').joinToString(" ") { token -> token.replaceFirstChar(Char::uppercase) }

    private const val DEFAULT_CONTRIBUTOR_COUNT = 3
}
