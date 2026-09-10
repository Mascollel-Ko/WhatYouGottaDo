package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*

/** Initial draft binding only. User choices and persisted bindings are never regrouped. */
internal fun bindSplitParentProgression(plan: GeneratedProgramSkeleton): GeneratedProgramSkeleton {
    val scheduling = plan.personalizedDecision?.authorizedScheduling ?: return plan
    val splitParents = scheduling.authorized.filter { it.continuity }.associateBy { it.id }
    val sessions = plan.progressionSessions.toMutableList()
    val bindings = mutableMapOf<String, DraftProgressionBinding>()
    plan.items.filter { row -> row.progressionBinding == null && scheduling.localOrigins[row.localId]?.splitGroupId?.isNotBlank() == true }
        .groupBy { scheduling.localOrigins.getValue(it.localId).authorizedDemandId }.forEach { (id, rows) ->
            val parent = splitParents[id] ?: return@forEach
            // Only the requested mandatory straight-set partitions need the explicit parent bridge.
            if (parent.prescription.sets.size !in 6..9) return@forEach
            val first = rows.first()
            val signature = ProgressionTrackInference.signature(first.exerciseStableKey, first.setPrescriptions,
                style = first.progressionStyle, variant = first.progressionVariant, slot = first.trainingSlot,
                intensity = first.dayIntensity, anchor = first.progressionAnchorSetIndex).copy(plannerRole = first.progressionRole)
            val session = DraftProgressionSession(ProgramProgressionTrack(programStableKey = "draft", exerciseStableKey = parent.item.stableKey,
                label = first.exerciseName, role = first.progressionRole, basePolicy = signature.basePolicy,
                needsReview = signature.basePolicy == ProgressionBase.REVIEW), ProgressionAuthority.PLANNER_EXPLICIT)
            sessions += session
            rows.forEach { row ->
                check(row.exerciseStableKey == parent.item.stableKey)
                val own = ProgressionTrackInference.signature(row.exerciseStableKey, row.setPrescriptions,
                    style = row.progressionStyle, variant = row.progressionVariant, slot = row.trainingSlot,
                    intensity = row.dayIntensity, anchor = row.progressionAnchorSetIndex).copy(plannerRole = row.progressionRole)
                bindings[row.localId] = DraftProgressionBinding(session.key, signature = own)
            }
        }
    return plan.copy(items = plan.items.map { row -> bindings[row.localId]?.let { row.copy(progressionBinding = it) } ?: row },
        progressionSessions = sessions)
}
