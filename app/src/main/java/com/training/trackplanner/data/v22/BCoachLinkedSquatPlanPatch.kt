package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.WorkoutSet
import kotlin.math.abs

/**
 * Pure immutable conversion from an explicitly approved Gate G proposal to a change-set.
 * Never changes actual confirmed work and never manufactures/removes sets.
 */
internal object BCoachLinkedSquatPlanPatch {
    data class Patch(
        val nextSets: List<WorkoutSet>,
        val nextPrescriptions: List<ProgramPrescriptionSet>
    )

    fun build(
        proposal: BCoachLinkedSquatNextWeekProposal,
        currentSets: List<WorkoutSet>,
        originalTargets: List<ProgramPrescriptionSet>
    ): Patch? {
        if (!proposal.canApply) return null
        val kg = proposal.proposedLoadKg ?: return null
        val reps = proposal.proposedReps ?: return null
        val count = proposal.proposedSets ?: return null
        if (!kg.isFinite() || kg <= 0.0 || reps <= 0 || count < 1 ||
            currentSets.size != count || originalTargets.size != count ||
            currentSets.any { it.confirmed }
        ) return null
        val byIndex = originalTargets.associateBy { it.plannedSetIndex }
        if (byIndex.size != count || byIndex.containsKey(null)) return null
        val affected = proposal.affectedSetIndices.ifEmpty { currentSets.map { it.setIndex } }
            .distinct()
        if (proposal.loadsBySetIndex.isNotEmpty() &&
            (proposal.loadsBySetIndex.keys != affected.toSet() ||
                proposal.loadsBySetIndex.values.any { !it.isFinite() || it <= 0.0 })) return null
        if (proposal.repsBySetIndex.isNotEmpty() && (proposal.repsBySetIndex.keys != affected.toSet() ||
            proposal.repsBySetIndex.values.any { it !in 1..20 })) return null
        if (affected.isEmpty() || affected.any { it !in currentSets.map(WorkoutSet::setIndex) })
            return null
        val next = currentSets.sortedBy { it.setIndex }.map { set ->
            val target = byIndex[set.setIndex] ?: return null
            if (!target.originalExists || set.entryId != proposal.targetEntryId ||
                target.entryId != proposal.targetEntryId ||
                target.plannedReps != target.originalReps ||
                abs(target.plannedKg - target.originalKg) > 1e-8 ||
                target.plannedSeconds != target.originalSeconds ||
                target.plannedLoadState != target.originalLoadState ||
                set.reps != target.plannedReps ||
                abs(set.weightKg - target.plannedKg) > 1e-8 ||
                set.seconds != target.plannedSeconds ||
                set.loadState != target.plannedLoadState
            ) return null
            if (set.setIndex !in affected) {
                set to target
            } else {
                val nextKg = proposal.loadsBySetIndex[set.setIndex] ?: kg
                val nextReps = proposal.repsBySetIndex[set.setIndex] ?: reps
                val nextSet = set.copy(
                    reps = nextReps, weightKg = nextKg,
                    estimatedLoadKg = nextKg, freshRir = null, expectedRir = null, relativeIntensity = null,
                    loadState = ProgramLoadState.EXPLICIT_LOAD,
                    manualWeight = proposal.loadsBySetIndex.isEmpty(),
                    loadSource = if (proposal.loadsBySetIndex.isEmpty()) set.loadSource else ProgramLoadSource.PERSONAL_MODEL
                )
                val nextTarget = target.copy(
                    plannedReps = nextReps, plannedKg = nextKg,
                    plannedLoadState = ProgramLoadState.EXPLICIT_LOAD
                )
                nextSet to nextTarget
            }
        }
        return Patch(
            nextSets = next.map { it.first },
            nextPrescriptions = next.map { it.second }
        )
    }
}
