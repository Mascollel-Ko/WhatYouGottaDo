package com.training.trackplanner

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.data.personalized.*

internal enum class PlanningSummarySection { OBSERVED_STATE, IDENTIFIED_NEEDS, PLAN_RESPONSE, FINAL_ALLOCATION, LIMITATIONS }
internal enum class SummaryEvidence { ADAPTATION_GAP, MOVEMENT_REPRESENTATION, BADMINTON_REPRESENTATION, SECONDARY_TARGET, FALLBACK }
internal enum class NeedSignal { ATTENTION, OPTIONAL_DEVELOPMENT, UNDERREPRESENTED, ABSENT, DIRECT_DROP }
internal enum class FoundationNeed { RESISTANCE, BADMINTON }
internal sealed interface SummaryNeedKey {
    data class Movement(val movement: MovementCoverage) : SummaryNeedKey
    data class Badminton(val objective: BadmintonObjective) : SummaryNeedKey
    data class Foundation(val domain: FoundationNeed) : SummaryNeedKey
}
internal data class SummaryNeed(val key: SummaryNeedKey, val signals: Set<NeedSignal>, val evidence: Set<SummaryEvidence>)
internal data class SummaryObserved(val behavior: ObservedTrainingBehavior, val style: StrengthProgrammingStyle?, val confidence: PlanningConfidence?)
internal sealed interface SummaryResponse {
    data class Transition(val stableKey: String, val structures: Set<StructureTreatment>, val doses: Set<DoseTreatment>) : SummaryResponse
    data object CapacityExpansion : SummaryResponse
}
internal enum class SummaryLimitationKind { RECOVERY, TISSUE, UNRESOLVED_STRENGTH, UNRESOLVED_BADMINTON, STARTING_LOAD, COURT_LOAD, SESSION_TIME, SCHEDULING, FATIGUE_WARNING }
internal data class SummaryLimitation(val kind: SummaryLimitationKind, val amount: Int? = null)
internal data class SummaryAllocation(
    val weeks: Int, val days: Int, val baselineSets: Double?, val targetSets: Int?, val plannedSets: Int?,
    val badmintonTarget: Int?, val badmintonPlanned: Int?, val athleticTarget: Int?, val athleticPlanned: Int?,
    val frequencyExpansionUnits: Int?
)
/** Five owned sections, with no mutable planner result or raw reason strings exposed to Compose. */
internal data class PlanningSummaryUiModel(
    val observedState: SummaryObserved,
    val identifiedNeeds: List<SummaryNeed>,
    val planResponse: List<SummaryResponse>,
    val finalAllocation: SummaryAllocation,
    val limitations: List<SummaryLimitation>
)

internal object PlanningSummaryPresenter {
    // Exact finite canonical tokens, including the existing gap-code protocol. No name/substring inference.
    private val needKeys: Map<String, SummaryNeedKey> = buildMap {
        MovementCoverage.entries.forEach { movement ->
            put(movement.name, SummaryNeedKey.Movement(movement))
            put("HYPERTROPHY_REBALANCE_${movement.name}", SummaryNeedKey.Movement(movement))
        }
        BadmintonObjective.entries.forEach { objective ->
            val key = SummaryNeedKey.Badminton(objective)
            put(objective.name, key)
            listOf("BADMINTON_DROP_", "BADMINTON_UNDERREPRESENTED_", "BADMINTON_DEVELOP_").forEach { put(it + objective.name, key) }
        }
        put("RESISTANCE_FOUNDATIONAL_ONRAMP", SummaryNeedKey.Foundation(FoundationNeed.RESISTANCE))
        put("BADMINTON_FOUNDATIONAL_ONRAMP", SummaryNeedKey.Foundation(FoundationNeed.BADMINTON))
    }
    private val dropCodes = BadmintonObjective.entries.map { "BADMINTON_DROP_${it.name}" }.toSet()
    private val optionalCodes = BadmintonObjective.entries.map { "BADMINTON_DEVELOP_${it.name}" }.toSet()
    private val fallbackNeedCodes = needKeys.mapKeys { "GAP_${it.key}" }

    fun present(decision: PersonalizedPlanningDecision): PlanningSummaryUiModel {
        val needs = linkedMapOf<SummaryNeedKey, SummaryNeed>()
        fun need(key: SummaryNeedKey?, signal: NeedSignal, source: SummaryEvidence) {
            if (key == null) return
            val previous = needs[key]
            needs[key] = SummaryNeed(key, previous?.signals.orEmpty() + signal, previous?.evidence.orEmpty() + source)
        }
        decision.adaptationGaps.forEach { gap ->
            val signal = when (gap.code) {
                in dropCodes -> NeedSignal.DIRECT_DROP
                in optionalCodes -> NeedSignal.OPTIONAL_DEVELOPMENT
                else -> signal(gap.representationState) ?: NeedSignal.ATTENTION
            }
            need(needKeys[gap.code], signal, SummaryEvidence.ADAPTATION_GAP)
        }
        decision.movementRepresentations.forEach { row ->
            signal(row.representationState)?.let { need(needKeys[row.movementCoverage], it, SummaryEvidence.MOVEMENT_REPRESENTATION) }
        }
        decision.badmintonObjectiveRepresentations.forEach { row ->
            val signal = if (row.directDrop) NeedSignal.DIRECT_DROP else signal(row.representationState)
            signal?.let { need(needKeys[row.objective], it, SummaryEvidence.BADMINTON_REPRESENTATION) }
        }
        decision.secondaryTargets.forEach { need(needKeys[it], NeedSignal.ATTENTION, SummaryEvidence.SECONDARY_TARGET) }
        // Known fallback facts only. Structured facts own the row; never dump reasons or debug codes.
        decision.reasonCodes.forEach { code ->
            val key = fallbackNeedCodes[code]
            if (key != null && key !in needs) need(key, NeedSignal.ATTENTION, SummaryEvidence.FALLBACK)
        }
        val responses = decision.anchorTransitions.groupBy { it.stableKey }.map { (key, rows) ->
            SummaryResponse.Transition(key, rows.mapTo(linkedSetOf()) { it.structureTreatment }, rows.mapTo(linkedSetOf()) { it.doseTreatment })
        }.toMutableList<SummaryResponse>()
        if ("MINIMAL_CAPACITY_EXPANSION" in decision.reasonCodes) responses += SummaryResponse.CapacityExpansion

        val limits = linkedMapOf<SummaryLimitationKind, SummaryLimitation>()
        fun limit(kind: SummaryLimitationKind, amount: Int? = null) { limits.putIfAbsent(kind, SummaryLimitation(kind, amount)) }
        if (decision.trainingStateAssessment?.state in setOf(TrainingState.HARD_RESTRICTION, TrainingState.ACCUMULATING_STRAIN, TrainingState.MALADAPTATION_PATTERN)) limit(SummaryLimitationKind.RECOVERY)
        if (decision.strengthIntent == StrengthIntent.UNRESOLVED.name) limit(SummaryLimitationKind.UNRESOLVED_STRENGTH)
        if (decision.badmintonIntent == BadmintonPlanningIntent.UNRESOLVED.name) limit(SummaryLimitationKind.UNRESOLVED_BADMINTON)
        if (decision.genericCourtLoad > 0) limit(SummaryLimitationKind.COURT_LOAD)
        decision.planningBudget?.execution?.capacity?.requestedSessionMinutes?.takeIf { it > 0 }?.let { limit(SummaryLimitationKind.SESSION_TIME, it) }
        if (decision.residualCompletion?.exactShortfalls?.any { it.shortfall > 0 } == true || decision.frequencyExpansion?.rollbackActions?.isNotEmpty() == true) limit(SummaryLimitationKind.SCHEDULING)
        if (decision.authorizedScheduling?.decisions?.any { it.ofiWarnings.isNotEmpty() } == true) limit(SummaryLimitationKind.FATIGUE_WARNING)
        // Reviewed legacy prose fallback: exact known messages, localized anew. Unknown prose stays in audit data.
        (decision.constraints + decision.reasons).forEach { reason ->
            when (reason) {
                "확인되지 않은 새 운동의 시작 중량은 RPE 기반으로 결정하며 기계 중량을 프리웨이트 중량으로 변환하지 않습니다." -> limit(SummaryLimitationKind.STARTING_LOAD)
                "조직 회복 상태가 높은 기여 운동의 증량을 제한합니다." -> limit(SummaryLimitationKind.TISSUE)
                "고중량 선호가 미해결이어서 새로운 고중량 특화를 추가하지 않은 임시 계획입니다." -> limit(SummaryLimitationKind.UNRESOLVED_STRENGTH)
                "배드민턴 계획 의도가 미해결이어서 배드민턴 드릴을 새로 추가하지 않았습니다." -> limit(SummaryLimitationKind.UNRESOLVED_BADMINTON)
            }
        }
        val budget = decision.planningBudget
        return PlanningSummaryUiModel(
            SummaryObserved(ObservedTrainingBehavior.entries.firstOrNull { it.name == decision.observedTrainingBehavior } ?: ObservedTrainingBehavior.UNKNOWN,
                StrengthProgrammingStyle.entries.firstOrNull { it.name == decision.strengthStyle }, PlanningConfidence.entries.firstOrNull { it.name == decision.confidence }),
            needs.values.toList(), responses,
            SummaryAllocation(decision.planningHorizonWeeks, decision.weeklyFrequency, budget?.baselineResistanceSets, budget?.targetResistanceSets,
                budget?.plannedResistanceSets, budget?.targetStructuredBadmintonBouts, budget?.plannedStructuredBadmintonBouts,
                budget?.targetAthleticPerformanceBouts, budget?.plannedAthleticPerformanceBouts, decision.frequencyExpansion?.finalExpandedUnits),
            limits.values.toList()
        )
    }

    private fun signal(state: RepresentationState?): NeedSignal? = when (state) {
        RepresentationState.ABSENT -> NeedSignal.ABSENT
        RepresentationState.STRONG_UNDERREPRESENTATION_SIGNAL, RepresentationState.UNDERREPRESENTATION_SIGNAL -> NeedSignal.UNDERREPRESENTED
        else -> null
    }
}
