package com.training.trackplanner.data

import java.util.UUID

/** Atomic repository snapshot: never hydrate items before their sets/bindings have loaded. */
data class ProgramEditorSnapshot(
    val program: TrainingProgram,
    val items: List<TrainingProgramItem>,
    val sets: List<TrainingProgramItemSet>,
    val bindings: List<ProgramProgressionItem>,
    val tracks: List<ProgramProgressionTrack>
)

data class ProgressionDraftContext(val eligibleKeys: Set<String>, val oneRmSnapshots: Map<String, Double>)

internal fun canonicalProgressionOneRm(exercise: Exercise, history: List<WorkoutEntryWithSets>, metadata: RuntimeExerciseMetadata?): Double? =
    history.sortedByDescending { it.entry.date }.firstNotNullOfOrNull { record ->
        com.training.trackplanner.analysis.features.ExerciseAnalysisMapper.fromRecord(exercise, record.entry, record.sets, metadata).estimated1Rm
    }

internal fun progressionAuthority(binding: DraftProgressionBinding): ProgressionAuthority = when {
    binding.linkMode != ProgressionLinkMode.AUTO -> ProgressionAuthority.USER_EXPLICIT
    binding.signature.style.isNotBlank() || binding.signature.variant.isNotBlank() ||
        binding.signature.anchorSetIndex != null || binding.signature.plannerRole != ProgressionRole.AUTO -> ProgressionAuthority.PLANNER_EXPLICIT
    else -> ProgressionAuthority.AUTO_INFERRED
}

internal fun GeneratedProgramSkeleton.hydrateProgression(snapshot: ProgramEditorSnapshot): GeneratedProgramSkeleton {
    val bindings = snapshot.bindings.associateBy { "existing-${it.programItemId}" }
    val next = items.map { item ->
        val binding = bindings[item.localId] ?: return@map item
        require(binding.signature.exerciseStableKey == item.exerciseStableKey)
        require(snapshot.tracks.single { it.id == binding.trackId }.exerciseStableKey == item.exerciseStableKey)
        item.copy(progressionBinding = DraftProgressionBinding(binding.trackId, binding.logicalItemId, binding.linkMode, binding.signature, persisted = true),
            trainingSlot = binding.signature.trainingSlot, dayIntensity = binding.signature.dayIntensity,
            progressionStyle = binding.signature.style, progressionVariant = binding.signature.variant,
            progressionAnchorSetIndex = binding.signature.anchorSetIndex, progressionRole = binding.signature.plannerRole)
    }
    return copy(items = next, progressionSessions = snapshot.tracks.filter { track -> next.any { it.progressionBinding?.sessionKey == track.id } }.map { track ->
        DraftProgressionSession(track, next.mapNotNull { it.progressionBinding }.filter { it.sessionKey == track.id }.minBy { progressionAuthority(it).ordinal }.let(::progressionAuthority))
    })
}

/** Uses the existing classifier unchanged. User bindings bypass comparability, never identity checks. */
internal fun GeneratedProgramSkeleton.reconcileProgression(eligibleKeys: Set<String>, oneRmSnapshots: Map<String, Double> = emptyMap()): GeneratedProgramSkeleton {
    val sessions = progressionSessions.associateBy { it.key }.toMutableMap()
    val assigned = mutableListOf<ProgramSkeletonItem>()
    for (item in items.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))) {
        if (item.exerciseStableKey !in eligibleKeys) {
            assigned += item.copy(progressionBinding = null)
            continue
        }
        val old = item.progressionBinding
        val signature = ProgressionTrackInference.signature(item.exerciseStableKey, ProgramSetPrescriptionResolver.resolve(item),
            oneRmKg = if (old?.persisted == true) old.signature.oneRmSnapshotKg else old?.signature?.oneRmSnapshotKg ?: oneRmSnapshots[item.exerciseStableKey],
            style = item.progressionStyle, variant = item.progressionVariant,
            slot = item.trainingSlot, intensity = item.dayIntensity, anchor = item.progressionAnchorSetIndex).copy(plannerRole = item.progressionRole)
        val oldSession = old?.let { sessions[it.sessionKey] ?: error("Missing draft progression session") }
        require(oldSession == null || oldSession.track.exerciseStableKey == item.exerciseStableKey)
        val retain = old != null && (old.linkMode != ProgressionLinkMode.AUTO || old.signature == signature || ProgressionTrackInference.sameTrack(old.signature, signature))
        val candidate = assigned.mapNotNull { it.progressionBinding }.firstOrNull {
            it.linkMode == ProgressionLinkMode.AUTO && ProgressionTrackInference.sameTrack(it.signature, signature)
        }
        val session = when {
            retain -> requireNotNull(oldSession)
            candidate != null -> sessions.getValue(candidate.sessionKey)
            else -> DraftProgressionSession(ProgramProgressionTrack(programStableKey = oldSession?.track?.programStableKey ?: "draft",
                exerciseStableKey = item.exerciseStableKey, label = item.exerciseName,
                basePolicy = signature.basePolicy, anchorSetIndex = signature.anchorSetIndex,
                needsReview = signature.basePolicy == ProgressionBase.REVIEW,
                roleOverride = oldSession?.track?.roleOverride ?: ProgressionRole.AUTO,
                mode = oldSession?.track?.mode ?: ProgressionMode.APP, rule = oldSession?.track?.rule ?: ProgressionRule()),
                if (signature.style.isNotBlank()) ProgressionAuthority.PLANNER_EXPLICIT else ProgressionAuthority.AUTO_INFERRED)
        }
        sessions[session.key] = session
        assigned += item.copy(progressionBinding = DraftProgressionBinding(session.key, old?.logicalItemId ?: UUID.randomUUID().toString(),
            old?.linkMode ?: ProgressionLinkMode.AUTO, signature, old?.persisted ?: false))
    }
    val active = assigned.mapNotNull { it.progressionBinding }.groupBy { it.sessionKey }
    val nextSessions = active.map { (id, members) ->
        val session = sessions.getValue(id)
        resolveProgressionSession(session, members, active.values.count { it.first().signature.exerciseStableKey == session.track.exerciseStableKey })
    }
    return copy(items = assigned, progressionSessions = nextSessions)
}

internal fun resolveProgressionSession(session: DraftProgressionSession, members: List<DraftProgressionBinding>, count: Int): DraftProgressionSession {
    val track = session.track
    val signatures = members.map { it.signature }
    val role = when {
        track.roleOverride != ProgressionRole.AUTO -> track.roleOverride
        signatures.all { it.plannerRole == ProgressionRole.MAIN } -> ProgressionRole.MAIN
        signatures.all { it.plannerRole == ProgressionRole.ASSISTANCE } -> ProgressionRole.ASSISTANCE
        count == 1 -> ProgressionRole.MAIN
        else -> ProgressionRole.AUTO
    }
    val explicit = members.any { it.linkMode != ProgressionLinkMode.AUTO }
    val manual = signatures.all { it.style.isBlank() }
    val base = if (explicit && manual) signatures.map { it.basePolicy }.distinct().singleOrNull() ?: ProgressionBase.REVIEW else track.basePolicy
    val drift = explicit && manual && signatures.drop(1).any { !ProgressionTrackInference.sameTrack(signatures.first(), it) }
    return session.copy(source = members.minBy { progressionAuthority(it).ordinal }.let(::progressionAuthority),
        track = track.copy(role = role, basePolicy = base,
            needsReview = base == ProgressionBase.REVIEW || drift || (!explicit && count > 1 && manual && role == ProgressionRole.AUTO),
            rule = if (track.mode == ProgressionMode.APP) ProgressionRule(rpeThreshold = if (role == ProgressionRole.ASSISTANCE) 9.0 else 8.0) else track.rule))
}

internal fun GeneratedProgramSkeleton.configureProgressionSession(
    localId: String, linkMode: ProgressionLinkMode, selectedKey: String?, role: ProgressionRole,
    mode: ProgressionMode, rule: ProgressionRule
): GeneratedProgramSkeleton {
    rule.validate()
    val item = items.single { it.localId == localId }
    val binding = requireNotNull(item.progressionBinding)
    val original = progressionSessions.single { it.key == binding.sessionKey }
    val selected = when (linkMode) {
        ProgressionLinkMode.EXISTING -> progressionSessions.single { it.key == selectedKey }
        ProgressionLinkMode.SEPARATE -> original.copy(track = original.track.copy(id = UUID.randomUUID().toString()))
        ProgressionLinkMode.AUTO -> items.mapNotNull { it.progressionBinding }.firstOrNull {
            it.logicalItemId != binding.logicalItemId && it.linkMode == ProgressionLinkMode.AUTO && ProgressionTrackInference.sameTrack(it.signature, binding.signature)
        }?.let { other -> progressionSessions.single { it.key == other.sessionKey } } ?: original
        ProgressionLinkMode.OFF -> original
    }
    require(selected.track.exerciseStableKey == item.exerciseStableKey) { "Progression sessions require the same exact exercise key" }
    val updated = selected.copy(track = selected.track.copy(roleOverride = role,
        mode = mode, rule = rule))
    return copy(items = items.map { if (it.localId == localId) it.copy(progressionBinding = binding.copy(sessionKey = selected.key, linkMode = linkMode)) else it },
        progressionSessions = progressionSessions.filterNot { it.key == selected.key } + updated)
        .reconcileProgression(items.filter { it.progressionBinding != null }.mapTo(mutableSetOf()) { it.exerciseStableKey })
}
