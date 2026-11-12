package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramSkeletonItem
import java.time.LocalDate

/** Transient, read-only comparison for a generated B program. It is NOT saved as a prescription. */
data class BCoachSquatShadowProgramComparison(
    val status: String,
    val snapshotDate: LocalDate?,
    val observedReference: String?,
    val plannedSquatRows: List<PlannedSquatRow>,
    val experimentCandidates: List<ExperimentalSquatCandidate>,
    val experiments: List<ExperimentalSquatDecision>,
    val notices: List<String>,
    /** Gate F: deterministic curve-based ranking; no persistence or program mutation. */
    val curveRanking: BCoachSquatCurveProgressionReport? = null
) {
    data class PlannedSquatRow(
        val week: Int,
        val day: Int,
        val sets: Int,
        val reps: Int,
        val loadKg: Double?,
        val weightSource: String
    )

    data class ExperimentalSquatCandidate(
        val action: String,
        val loadKg: Double,
        val reps: Int,
        val sets: Int,
        val targetRirMass: Double?,
        val followUpSetsUnmodeled: Int
    )

    data class ExperimentalSquatDecision(
        val scenario: String,
        val coefficientPolicy: String,
        val tentativeWinner: String
    )
}

/**
 * Gate D never overwrites the generator's output. It pairs EVERY generated squat occurrence
 * with a limited, one-session Gate B/C exploration of the user's latest confirmed squat.
 *
 * Generated B program weights are usually USER_CALIBRATION_REQUIRED; those are displayed as
 * unresolved, not replaced by shadow kg. The two columns are not a week-by-week prediction.
 */
internal object BCoachSquatShadowProgramComparator {
    fun compare(
        asOf: LocalDate,
        plannedItems: List<ProgramSkeletonItem>,
        pipeline: BCoachSquatShadowPipelineResult
    ): BCoachSquatShadowProgramComparison {
        val rows = plannedItems.filter { it.exerciseStableKey == "barbell_back_squat" }
            .sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
            .map { item ->
                val prescribed = item.setPrescriptions.takeIf { it.isNotEmpty() }
                val resolved = prescribed?.takeIf { sets ->
                    sets.all { it.loadState == com.training.trackplanner.data.ProgramLoadState.EXPLICIT_LOAD &&
                        it.weightKg.isFinite() && it.weightKg > 0.0 }
                }
                BCoachSquatShadowProgramComparison.PlannedSquatRow(
                    week = item.weekNumber,
                    day = item.dayOfWeek,
                    sets = prescribed?.size ?: item.setCount,
                    reps = prescribed?.map { it.reps }?.distinct()?.singleOrNull() ?: item.reps,
                    loadKg = resolved?.map { it.weightKg }?.distinct()?.singleOrNull()
                        ?: item.weightKg.takeIf { resolved == null && prescribed == null && it.isFinite() && it > 0.0 },
                    weightSource = item.weightSource
                )
            }

        if (rows.isEmpty()) return BCoachSquatShadowProgramComparison(
            status = "NO_SQUAT_IN_GENERATED_PROGRAM",
            snapshotDate = asOf, observedReference = null,
            plannedSquatRows = emptyList(), experimentCandidates = emptyList(),
            experiments = emptyList(),
            notices = listOf("현재 생성 프로그램에 백스쿼트가 없어 비교 대상이 없습니다.")
        )
        if (pipeline is BCoachSquatShadowPipelineResult.ModelUnavailable)
            return BCoachSquatShadowProgramComparison(
                status = "MODEL_UNAVAILABLE",
                snapshotDate = asOf, observedReference = null,
                plannedSquatRows = rows, experimentCandidates = emptyList(),
                experiments = emptyList(),
                notices = listOf(
                    "근력 모델 상태를 읽을 수 없습니다: ${pipeline.reason}",
                    "원래 생성 프로그램은 그대로 유지됩니다."
                )
            )
        pipeline as BCoachSquatShadowPipelineResult.ShadowReport
        val ref = pipeline.relative.anchorSet
        val selector = pipeline.selection
        val curveRanking = BCoachSquatCurveProgressionRanker.evaluate(
            input = pipeline,
            targetRirMin = 1.0,
            targetRirMax = 3.0,
            doseTargetSets = rows.firstOrNull()?.sets
        )
        val comparison = BCoachSquatShadowProgramComparison(
            status = when {
                pipeline.relative.status != BCoachSquatRelativeShadow.ResultStatus.READY -> "ANCHOR_UNAVAILABLE"
                selector.state == BCoachSquatShadowSelector.DecisionState.GRID_CONFLICT_REVIEW -> "GRID_REVIEW_REQUIRED"
                selector.state == BCoachSquatShadowSelector.DecisionState.WEIGHT_OR_OMEGA_SENSITIVE -> "SENSITIVE_SHADOW"
                selector.state == BCoachSquatShadowSelector.DecisionState.STABLE_SHADOW_ONLY -> "STABLE_EXPERIMENT_ONLY"
                else -> "EXPERIMENT_INCOMPLETE"
            },
            snapshotDate = asOf,
            observedReference = ref?.let { "${it.loadKg}kg × ${it.repetitions}회 / RPE ${it.observedRpe ?: "미기록"}" },
            plannedSquatRows = rows,
            experimentCandidates = pipeline.relative.candidates
                .filter { it.action in BCoachSquatShadowSelector.frozenBenchmark }
                .map { candidate ->
                    BCoachSquatShadowProgramComparison.ExperimentalSquatCandidate(
                        action = candidate.action.name,
                        loadKg = candidate.totalLoadKg,
                        reps = candidate.repetitions,
                        sets = candidate.plannedSetCount,
                        targetRirMass = candidate.targetRirMixtureMass.takeIf {
                            candidate.evaluationStatus == BCoachSquatRelativeShadow.EvaluationStatus.READY
                        },
                        followUpSetsUnmodeled = candidate.followUpSetsNotPredicted
                    )
                },
            experiments = selector.results.map { decision ->
                BCoachSquatShadowProgramComparison.ExperimentalSquatDecision(
                    scenario = decision.omegaBand.name,
                    coefficientPolicy = "EXPERIMENT_${decision.weightsIndex + 1}",
                    tentativeWinner = decision.winner.name
                )
            },
            notices = buildList {
                add("Shadow 실험: 기존 생성 프로그램의 중량·반복·세트는 변경되지 않았습니다.")
                add("위 후보는 마지막 실제 스쿼트 세션 기준의 단기 비교입니다. 각 주차의 중량 처방이 아닙니다.")
                add("반복·중량 진행의 효과 계수는 검증되지 않았으며, 후속 세트의 피로는 예측하지 않습니다.")
                if (rows.any { it.loadKg == null }) add("생성 프로그램의 스쿼트 중량은 미설정입니다. Shadow 값을 대신 저장하지 않습니다.")
                addAll(pipeline.relative.notes.filter { it.contains("GRID_CONFLICT") || it.contains("FOLLOW_UP") })
                addAll(selector.warnings.filter { it.contains("REVERSAL") || it.contains("GRID_REVIEW") })
            }.distinct(),
            curveRanking = curveRanking
        )
        return comparison
    }
}
