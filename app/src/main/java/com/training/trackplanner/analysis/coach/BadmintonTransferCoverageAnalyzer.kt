package com.training.trackplanner.analysis.coach

import com.training.trackplanner.analysis.badminton.BadmintonTransferAxis
import com.training.trackplanner.analysis.badminton.BadmintonTransferConstants
import com.training.trackplanner.analysis.badminton.BadmintonTransferScoreCalculator
import com.training.trackplanner.analysis.badminton.BadmintonTransferWindowSnapshot
import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

class BadmintonTransferCoverageAnalyzer(
    runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
    private val scoreCalculator: BadmintonTransferScoreCalculator =
        BadmintonTransferScoreCalculator(runtimeMetadataCatalog)
) {
    fun analyze(
        today: LocalDate,
        exercises: List<Exercise>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        latestFatigueState: DailyFatigueState?
    ): BadmintonTransferCoverageSummary = analyze(
        recent = scoreCalculator.calculateWindow(today, 14, exercises, entriesWithSets),
        baseline = scoreCalculator.calculateWindow(today, 28, exercises, entriesWithSets),
        latestFatigueState = latestFatigueState
    )

    fun analyze(
        recent: BadmintonTransferWindowSnapshot,
        baseline: BadmintonTransferWindowSnapshot,
        latestFatigueState: DailyFatigueState?
    ): BadmintonTransferCoverageSummary {
        if (recent.sampleEntryCount <= 0 || recent.totalStimulus <= BadmintonTransferConstants.EPSILON) {
            return BadmintonTransferCoverageSummary.insufficient()
        }
        val statuses = BadmintonTransferAxis.entries.map { axis ->
            val stimulus = recent.axisStimulus[axis] ?: 0.0
            val recentShare = recent.axisShare[axis] ?: 0.0
            val baselineShare = baseline.axisShare[axis]?.takeIf { baseline.totalStimulus > BadmintonTransferConstants.EPSILON }
            val targetShare = BadmintonTransferConstants.shareThreshold(axis)
            val highShare = maxOf(0.20, targetShare * 1.8)
            val relatedFatigue = relatedFatigueScore(axis, latestFatigueState)
            val repeated = (recent.axisEntryCounts[axis] ?: 0) >= 2
            val elevatedFromBaseline = baselineShare == null || recentShare >= baselineShare * 1.15
            val lowShare = recentShare < targetShare * 0.5
            val absoluteLowerFoundationPresent =
                axis == BadmintonTransferAxis.LOWER_BODY_STRENGTH &&
                    stimulus >= BadmintonTransferConstants.LOWER_BODY_FOUNDATION_ABSOLUTE_STIMULUS_THRESHOLD
            val status = when {
                stimulus <= BadmintonTransferConstants.EPSILON -> TransferAxisStatusType.MISSING
                recent.sampleEntryCount < 3 -> TransferAxisStatusType.BALANCED
                lowShare && !absoluteLowerFoundationPresent -> TransferAxisStatusType.LOW
                recentShare >= highShare && relatedFatigue >= 75 && repeated && elevatedFromBaseline ->
                    TransferAxisStatusType.OVERLOADED
                recentShare >= highShare -> TransferAxisStatusType.HIGH
                else -> TransferAxisStatusType.BALANCED
            }
            BadmintonTransferAxisStatus(
                axis = axis,
                label = axis.displayName,
                status = status,
                recentShare = recentShare,
                baselineShare = baselineShare,
                detail = detail(status, recent.windowDays, absoluteLowerFoundationPresent && lowShare)
            )
        }
        val lowAxes = statuses
            .filter { it.status in setOf(TransferAxisStatusType.MISSING, TransferAxisStatusType.LOW) }
            .sortedWith(compareBy<BadmintonTransferAxisStatus> { it.status != TransferAxisStatusType.MISSING }.thenBy { it.recentShare })
            .take(4)
        val cautionAxes = statuses
            .filter { it.status in setOf(TransferAxisStatusType.OVERLOADED, TransferAxisStatusType.HIGH) }
            .sortedWith(compareByDescending<BadmintonTransferAxisStatus> { it.status == TransferAxisStatusType.OVERLOADED }.thenByDescending { it.recentShare })
            .take(3)
        val headline = when {
            cautionAxes.any { it.status == TransferAxisStatusType.OVERLOADED } ->
                "${cautionAxes.first().label} 비중과 관련 피로가 함께 높게 잡힙니다."
            lowAxes.isNotEmpty() ->
                "최근 ${recent.windowDays}일 ${lowAxes.take(2).joinToString("과 ") { it.label }} 자극이 부족합니다."
            else -> "최근 전이 축은 대체로 균형적입니다."
        }
        return BadmintonTransferCoverageSummary(
            recentWindowDays = recent.windowDays,
            baselineWindowDays = baseline.windowDays,
            statuses = statuses,
            lowAxes = lowAxes,
            cautionAxes = cautionAxes,
            headline = headline,
            isDataSufficient = true
        )
    }

    private fun relatedFatigueScore(axis: BadmintonTransferAxis, state: DailyFatigueState?): Int {
        if (state == null) return 0
        return when (axis) {
            BadmintonTransferAxis.DECELERATION_LANDING -> maxOf(state.jointTendonImpactScore, state.localMuscularScore)
            BadmintonTransferAxis.UNILATERAL_STABILITY -> maxOf(state.movementFocusScore, state.jointTendonImpactScore)
            BadmintonTransferAxis.LATERAL_MOVEMENT -> maxOf(state.movementFocusScore, state.jointTendonImpactScore)
            BadmintonTransferAxis.ROTATION_CONTROL -> maxOf(state.movementFocusScore, state.localMuscularScore)
            BadmintonTransferAxis.LOWER_BODY_STRENGTH -> maxOf(state.systemicMuscularScore, state.localMuscularScore)
            BadmintonTransferAxis.RACKET_SUPPORT -> maxOf(state.localMuscularScore, state.jointTendonImpactScore)
            BadmintonTransferAxis.AEROBIC_FOOTWORK -> maxOf(state.systemicMuscularScore, state.recoveryPressureScore)
            BadmintonTransferAxis.LOW_FATIGUE_CONTROL -> state.recoveryPressureScore
        }
    }

    private fun detail(
        status: TransferAxisStatusType,
        windowDays: Int,
        absoluteLowerFoundationPresent: Boolean = false
    ): String = when {
        absoluteLowerFoundationPresent ->
            "최근 전이 자극 중 비중은 낮지만, 하체 근력 운동 자체는 수행 중입니다."
        else -> when (status) {
        TransferAxisStatusType.MISSING -> "최근 ${windowDays}일 기록이 없습니다."
        TransferAxisStatusType.LOW -> "최근 ${windowDays}일 비중이 낮습니다."
        TransferAxisStatusType.BALANCED -> "최근 기록 기준 범위에 있습니다."
        TransferAxisStatusType.HIGH -> "최근 비중이 높게 누적되었습니다."
        TransferAxisStatusType.OVERLOADED -> "최근 비중과 관련 피로축이 함께 높게 잡힙니다."
    }
}
}
