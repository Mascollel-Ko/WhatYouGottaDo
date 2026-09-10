package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonRequest
import kotlin.math.roundToInt

/** Own-history weekly dose, own transition and canonical prescription; never a proxy's training load. */
internal fun retainedIncumbentSupply(snapshot: PlanningHistorySnapshot, state: AthletePlanningState,
    gaps: List<AdaptationGap>, request: ProgramSkeletonRequest, existing: List<CapacityCandidateTrace>,
    prescriptions: PersonalizedPrescriptionPlanner): List<CapacityCandidateTrace> {
    val result = mutableListOf<CapacityCandidateTrace>()
    val existingKeys = existing.mapTo(mutableSetOf()) { it.item.stableKey }
    for (candidate in state.fullEligibleIncumbentRanking.filterNot { it.baseSelected }) {
        val anchor = candidate.anchor
        val key = anchor.stableKey
        val equipment = snapshot.exercises[key]?.equipment.orEmpty().split('|', ',').map(String::trim).filter(String::isNotBlank)
        if (key in existingKeys || key in request.excludedExerciseStableKeys || snapshot.explicitlyRestricted(key) ||
            key !in snapshot.exercises || !postProcessTissueAllowed(snapshot, state, key) ||
            snapshot.metadata[key]?.planningEligibility !in setOf("PROGRAM_SELECTABLE", "SELECTABLE") ||
            (request.availableEquipment.isNotEmpty() && equipment.any { it != "BODYWEIGHT" && it !in request.availableEquipment })) continue
        val features = StrengthStyleFeatureAnalyzer().analyze(snapshot, key)
        val ownState = state.copy(anchors = listOf(anchor), styleFeaturesByAnchor = state.styleFeaturesByAnchor + (key to features))
        val transition = AdaptationTransitionPlanner().decide(anchor, ownState, gaps)
        val weekly = (maxOf(.20, anchor.sets.toDouble() / features.weeksObserved.coerceAtLeast(1)) *
            maxOf(.25, transition.continuityScore) * transition.localDoseFactor).roundToInt().coerceAtLeast(1)
        val items = ExerciseContinuityPlanner().select(ownState, mapOf(key to transition), mapOf(key to weekly), request.weeklyTrainingDays)
        for (item in items) {
            val rx = prescriptions.prescribe(snapshot, state.strengthIntent, item, item.style)
            if (rx.sets.isEmpty()) continue
            result += CapacityCandidateTrace(existing.size + result.size + 1, item, rx, 0, true,
                when (candidate.cutReason) {
                    IncumbentCutReason.MOVEMENT_ANCHOR_CUTOFF -> CandidateRejectionReason.MOVEMENT_ANCHOR_CUTOFF
                    IncumbentCutReason.GLOBAL_ANCHOR_CUTOFF -> CandidateRejectionReason.GLOBAL_ANCHOR_CUTOFF
                    IncumbentCutReason.BASE_SELECTED -> error("BASE is not retained supply")
                })
        }
    }
    return result
}
