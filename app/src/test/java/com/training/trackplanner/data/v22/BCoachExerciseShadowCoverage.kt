package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthProxyMode
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ProgramSkeletonItem
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

/**
 * Gate E: read-only coverage audit for generated exercises OTHER than the existing squat slice.
 * This does not rank candidates, infer adaptation, or change any generated/saved prescription.
 * Different exercise types must NOT inherit the squat RIR mixture as a universal model.
 */
data class BCoachExerciseShadowCoverageRow(
    val stableKey: String,
    val exerciseName: String,
    val tier: String,
    val status: String,
    val confirmedObservation: String?,
    val unrankedAlternatives: List<String>,
    val explanation: String
)

internal object BCoachExerciseShadowCoverage {
    fun inspect(
        asOf: LocalDate,
        plannedItems: List<ProgramSkeletonItem>,
        confirmedHistory: List<V22HistoryRow>,
        registry: StrengthPerformanceRegistry,
        exercises: Map<String, Exercise>,
        increments: BBlockEquipmentIncrements?
    ): List<BCoachExerciseShadowCoverageRow> {
        val history = confirmedHistory.asSequence()
            .filter { !it.date.isAfter(asOf) }
            .filter { it.stableKey.isNotBlank() }
            .groupBy { it.stableKey }
        return plannedItems.asSequence()
            .filter { it.exerciseStableKey.isNotBlank() && it.exerciseStableKey != "barbell_back_squat" }
            .distinctBy { it.exerciseStableKey }
            .map { planned ->
                val key = planned.exerciseStableKey
                val observed = history[key].orEmpty().filter { row ->
                    row.reps?.let { it.isFinite() && it > 0 } == true ||
                        row.seconds?.let { it.isFinite() && it > 0 } == true
                }
                val lastDay = observed.maxOfOrNull { it.date }
                // V22HistoryRow lacks a session ID. Never present same-day rows as a full session.
                val dayRows = observed.filter { it.date == lastDay }
                val anchor = dayRows
                    .filter { (it.reps ?: 0.0).let { r -> r in 1.0..20.0 && r == r.toInt().toDouble() } }
                    .maxWithOrNull(compareBy<V22HistoryRow>(
                        { if ((it.weightKg ?: 0.0) > 0.0) it.weightKg!! else 0.0 },
                        { -it.setIndex }
                    ))
                    ?: dayRows.firstOrNull()
                val load = anchor?.weightKg?.takeIf { it.isFinite() && it > 0.0 }
                val reps = anchor?.reps?.takeIf { it.isFinite() && it in 1.0..20.0 &&
                    it == it.toInt().toDouble() }?.toInt()
                val seconds = anchor?.seconds?.takeIf { it.isFinite() && it > 0.0 }
                val observedReps = anchor?.reps?.takeIf { it.isFinite() && it > 0.0 }
                val observation = if (lastDay == null) null else buildString {
                    append(lastDay.toString())
                    when {
                        load != null && reps != null -> append(" · ${load}kg × ${reps}회")
                        seconds != null -> append(" · ${seconds}초")
                        reps != null -> append(" · ${reps}회 (중량정보 없음)")
                        observedReps != null -> append(" · ${observedReps}회 (과제별 지표 검토)")
                    }
                    if (anchor?.rpe != null) append(" · RPE ${anchor.rpe}")
                    append(" (당일 확인 기록; 세션 일치 미검증)")
                }
                val direct = registry.directTarget(key)
                val local = registry.proxyLoadings(key).any {
                    it.proxyMode == StrengthProxyMode.LOCAL_INNOVATION_SHARED_ONLY
                }
                val loadKind = BBlockEquipmentIncrements.kind(key, exercises[key])
                val step = increments?.incrementFor(loadKind)
                val oldLoads = history[key].orEmpty().asSequence()
                    .filter { !it.date.isBefore(asOf.minusDays(56)) }
                    .mapNotNull { it.weightKg?.takeIf { kg -> kg.isFinite() && kg > 0.0 } }
                    .distinct().sorted().toList()
                val gridMismatch = load != null && step != null && oldLoads.size >= 2 &&
                    oldLoads.zipWithNext().any { (a, b) ->
                        val ratio = (b - a) / step
                        abs(ratio - round(ratio)) > 1e-6
                    }
                val alternatives = buildList {
                    if (anchor != null) {
                        add("관측 수준 유지 (처방 승인 아님)")
                        if (reps != null && reps < 20) add("같은 중량에서 반복 +1 검토")
                        if (load != null && reps != null && step != null && !gridMismatch &&
                            direct?.loadSemantics != StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD
                        ) add("표시 중량 +${step}kg 검토 (미평가 후보)")
                        if (seconds != null) add("측정 가능한 유지시간/난도 조정 검토")
                    }
                }
                val (tier, status, message) = when {
                    direct?.loadSemantics == StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD ->
                        Triple("A_CANONICAL", "BODYWEIGHT_AND_POSTERIOR_BRIDGE_REQUIRED",
                            "중량 풀업은 당시 체중+추가중량으로 평가해야 합니다. V22 기록만으로 총부하를 만들어서는 안 됩니다.")
                    direct != null ->
                        Triple("A_CANONICAL", "CANONICAL_SHADOW_BRIDGE_PENDING",
                            "정식 근력 사후분포와 운동별 곡선을 연결해야 합니다. 현재 관측값만으로 RIR 질량이나 자동 증량을 계산하지 않습니다.")
                    local ->
                        Triple("B_LOCAL", "LOCAL_POSTERIOR_ELIGIBILITY_UNVERIFIED",
                            "proxy 관계만으로는 운동별 사후분포가 입증되지 않습니다. local posterior 유효성 확인 전 직접 진행만 참고합니다.")
                    load != null && reps != null ->
                        Triple("C_OBSERVED", if (gridMismatch) "INCREMENT_CONFLICT_REVIEW"
                            else if (step == null) "INCREMENT_UNKNOWN" else "OBSERVATION_ONLY",
                            "비교 가능한 확인기록 기반의 미평가 후보입니다. e1RM/성공률/효과 계수는 추정하지 않습니다.")
                    else ->
                        Triple("D_TASK", if (anchor == null) "NO_COMPARABLE_OBSERVATION"
                            else "TASK_SPECIFIC_REVIEW",
                            "시간·반복·동작품질 등 과제별 지표가 필요합니다. RIR·e1RM 또는 중량증량을 강제하지 않습니다.")
                }
                BCoachExerciseShadowCoverageRow(
                    stableKey = key,
                    exerciseName = planned.exerciseName,
                    tier = tier,
                    status = status,
                    confirmedObservation = observation,
                    unrankedAlternatives = alternatives,
                    explanation = message + if (gridMismatch)
                        " 최근 관측 중량 차이와 현재 표시 증량 단위가 불일치합니다. 사용자 확인 필요."
                    else ""
                )
            }.sortedWith(compareBy({ it.tier }, { it.exerciseName }, { it.stableKey })).toList()
    }
}
