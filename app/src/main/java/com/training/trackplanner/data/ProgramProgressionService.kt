package com.training.trackplanner.data

import androidx.room.withTransaction
import java.security.MessageDigest
import java.util.UUID

internal class ProgramProgressionService(
    private val db: TrainingDatabase,
    private val restrictedKeys: suspend () -> Set<String> = { emptySet() }
) {
    private val dao = db.programProgressionDao()
    private val workouts = db.workoutDao()

    suspend fun author(programId: Long, generated: Map<Long, ProgramSkeletonItem> = emptyMap(), restored: Map<Long, ProgramProgressionItem> = emptyMap()) = db.withTransaction {
        val program = db.programDao().findProgram(programId) ?: return@withTransaction
        val rows = db.programDao().itemsForProgram(programId)
        val sets = db.programDao().programItemSetsForProgram(programId).groupBy { it.programItemId }
        val previous = dao.items().associateBy { it.programItemId } + restored
        val assigned = mutableListOf<ProgramProgressionItem>()
        val allTracks = dao.tracks().associateBy { it.id }.toMutableMap()
        val roles = db.exerciseRoleRelationDao().allTrainingRoles().groupBy { it.exerciseStableKey }
        val metadata = db.runtimeExerciseMetadataDao().all().associate { it.stableKey to it.toRuntimeMetadata() }
        val history = workouts.entriesWithSetsUntil(java.time.LocalDate.now().toString()).groupBy { it.entry.exerciseStableKey }
        for (item in rows.sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))) {
            val exercise = db.exerciseDao().findByStableKey(item.exerciseStableKey) ?: continue
            val prescriptions = ProgramSetPrescriptionResolver.resolve(item, sets[item.id].orEmpty())
            // Canonical activity kind only; never category/name/equipment guesses.
            if (!progressionEligible(exercise, roles[item.exerciseStableKey].orEmpty().mapTo(mutableSetOf()) { it.trainingRoleCode }) || prescriptions.none { it.weightKg > 0 && it.reps > 0 }) {
                dao.deleteItem(item.id)
                continue
            }
            val old = previous[item.id]?.takeIf { it.signature.exerciseStableKey == item.exerciseStableKey }
            val source = generated[item.id]
            // Reuse the existing confirmed-record e1RM projection. Never use a metadata proxy or fabricate a prior.
            val oneRm = if (old != null) old.signature.oneRmSnapshotKg else history[item.exerciseStableKey].orEmpty()
                .sortedByDescending { it.entry.date }.firstNotNullOfOrNull { record ->
                    com.training.trackplanner.analysis.features.ExerciseAnalysisMapper.fromRecord(exercise, record.entry, record.sets, metadata[item.exerciseStableKey]).estimated1Rm
                }
            val signature = ProgressionTrackInference.signature(item.exerciseStableKey, prescriptions,
                oneRmKg = oneRm,
                style = source?.progressionStyle?.ifBlank { null } ?: old?.signature?.style.orEmpty(),
                variant = source?.progressionVariant?.ifBlank { null } ?: old?.signature?.variant.orEmpty(),
                slot = item.trainingSlot.orEmpty(), intensity = item.dayIntensity.orEmpty(),
                anchor = source?.progressionAnchorSetIndex ?: old?.signature?.anchorSetIndex).copy(
                    plannerRole = source?.progressionRole?.takeUnless { it == ProgressionRole.AUTO } ?: old?.signature?.plannerRole ?: ProgressionRole.AUTO)
            val explicit = old?.takeIf { it.linkMode != ProgressionLinkMode.AUTO }
            val candidate = assigned.firstOrNull { it.linkMode == ProgressionLinkMode.AUTO && ProgressionTrackInference.sameTrack(it.signature, signature) }
            val oldTrack = old?.let { allTracks[it.trackId] }
            val retain = explicit != null || old != null && ProgressionTrackInference.sameTrack(old.signature, signature)
            val track = when {
                retain && oldTrack != null -> oldTrack
                candidate != null -> allTracks.getValue(candidate.trackId)
                else -> ProgramProgressionTrack(programStableKey = program.stableKey, exerciseStableKey = item.exerciseStableKey,
                    label = "${item.exerciseName} · ${signature.repsPattern.split('|').distinct().joinToString("/")}회" + signature.variant.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    basePolicy = signature.basePolicy, anchorSetIndex = signature.anchorSetIndex,
                    needsReview = signature.basePolicy == ProgressionBase.REVIEW,
                    roleOverride = oldTrack?.roleOverride ?: ProgressionRole.AUTO,
                    mode = oldTrack?.mode ?: ProgressionMode.APP, rule = oldTrack?.rule ?: ProgressionRule())
            }
            allTracks[track.id] = track
            dao.putTrack(track)
            val binding = ProgramProgressionItem(item.id, old?.logicalItemId ?: UUID.randomUUID().toString(), track.id,
                explicit?.linkMode ?: ProgressionLinkMode.AUTO, signature)
            assigned += binding
            dao.putItem(binding)
        }
        assigned.groupBy { it.signature.exerciseStableKey }.values.forEach { bindings ->
            val trackIds = bindings.map { it.trackId }.distinct()
            for (id in trackIds) {
                val track = allTracks.getValue(id)
                val signatures = bindings.filter { it.trackId == id }.map { it.signature }
                val explicitStyle = signatures.all { it.style.isNotBlank() }
                val plannerMain = signatures.all { it.plannerRole == ProgressionRole.MAIN }
                val plannerSupport = signatures.all { it.plannerRole == ProgressionRole.ASSISTANCE }
                val role = when {
                    track.roleOverride != ProgressionRole.AUTO -> track.roleOverride
                    plannerMain -> ProgressionRole.MAIN
                    plannerSupport -> ProgressionRole.ASSISTANCE
                    trackIds.size == 1 -> ProgressionRole.MAIN
                    else -> ProgressionRole.AUTO
                }
                val reviewed = bindings.filter { it.trackId == id }.any { it.linkMode != ProgressionLinkMode.AUTO }
                dao.putTrack(track.copy(role = role, needsReview = track.basePolicy == ProgressionBase.REVIEW || (!reviewed && trackIds.size > 1 && !explicitStyle && role == ProgressionRole.AUTO),
                    rule = if (track.mode == ProgressionMode.APP) ProgressionRule(rpeThreshold = if (role == ProgressionRole.ASSISTANCE) 9.0 else 8.0) else track.rule))
            }
        }
        // Draft overrides are resolved against exact draft IDs after automatic authoring of all rows.
        for ((itemId, source) in generated) {
            val settings = source.progressionSettings ?: continue
            if (assigned.none { it.programItemId == itemId }) continue
            val targetItemId = generated.entries.firstOrNull { it.value.localId == settings.targetLocalId }?.key
            val targetTrack = dao.items().firstOrNull { it.programItemId == targetItemId }?.trackId
            configure(itemId, settings.linkMode, targetTrack, settings.role, settings.mode, settings.rule)
        }
    }

    suspend fun configure(itemId: Long, linkMode: ProgressionLinkMode, selectedTrackId: String?, role: ProgressionRole, mode: ProgressionMode, rule: ProgressionRule) = db.withTransaction {
        rule.validate()
        val binding = dao.items().single { it.programItemId == itemId }
        val original = dao.tracks().single { it.id == binding.trackId }
        val available = dao.tracks()
        val missingTarget = linkMode == ProgressionLinkMode.EXISTING && available.none { it.id == selectedTrackId }
        val selected = when {
            missingTarget -> original.copy(id = UUID.randomUUID().toString())
            linkMode == ProgressionLinkMode.EXISTING -> available.single { it.id == selectedTrackId }.also {
                require(it.programStableKey == original.programStableKey && it.exerciseStableKey == original.exerciseStableKey)
            }
            linkMode == ProgressionLinkMode.SEPARATE -> original.copy(id = UUID.randomUUID().toString())
            linkMode == ProgressionLinkMode.AUTO -> dao.items().firstOrNull {
                it.programItemId != itemId && it.linkMode == ProgressionLinkMode.AUTO &&
                    available.firstOrNull { track -> track.id == it.trackId }?.programStableKey == original.programStableKey &&
                    ProgressionTrackInference.sameTrack(it.signature, binding.signature)
            }?.let { other -> available.single { it.id == other.trackId } } ?: original
            else -> original
        }
        dao.putTrack(selected.copy(roleOverride = role, role = if (role == ProgressionRole.AUTO) selected.role else role,
            mode = if (missingTarget) ProgressionMode.DIRECT else if (linkMode == ProgressionLinkMode.OFF) ProgressionMode.OFF else mode,
            rule = rule, needsReview = missingTarget || selected.basePolicy == ProgressionBase.REVIEW))
        dao.putItem(binding.copy(trackId = selected.id, linkMode = if (missingTarget) ProgressionLinkMode.SEPARATE else linkMode))
        // Applied links deliberately retain their old role/rule/intent snapshots.
    }

    suspend fun attach(application: ProgramApplication, item: TrainingProgramItem, entryId: Long, sequence: Int, sets: List<ProgramSetPrescription>) {
        val binding = dao.items().firstOrNull { it.programItemId == item.id }
        val track = binding?.let { b -> dao.tracks().firstOrNull { it.id == b.trackId } }
        dao.putLink(ProgramWorkoutLink(entryId, application.id, application.programStableKey,
            binding?.logicalItemId ?: UUID.randomUUID().toString(), application.programName, item.weekNumber, item.dayOfWeek,
            track?.id ?: "untracked", track?.label.orEmpty(), sequence,
            track?.role ?: ProgressionRole.AUTO,
            if (binding?.linkMode == ProgressionLinkMode.OFF) ProgressionMode.OFF else track?.mode ?: ProgressionMode.OFF,
            track?.basePolicy ?: ProgressionBase.REVIEW, track?.anchorSetIndex,
            track?.needsReview ?: false, track?.rule ?: ProgressionRule()))
        sets.forEach { dao.putPrescription(ProgramPrescriptionSet(entryId, it.setIndex, it.reps, it.weightKg, it.seconds)) }
    }

    suspend fun relocate(link: ProgramWorkoutLink, prescriptions: List<ProgramPrescriptionSet>, newEntryId: Long) {
        dao.putLink(link.copy(entryId = newEntryId))
        prescriptions.forEach { dao.putPrescription(it.copy(entryId = newEntryId)) }
        dao.suggestions().filter { it.sourceEntryId == link.entryId || it.targetEntryId == link.entryId }.forEach {
            dao.putSuggestion(it.copy(sourceEntryId = if (it.sourceEntryId == link.entryId) newEntryId else it.sourceEntryId,
                targetEntryId = if (it.targetEntryId == link.entryId) newEntryId else it.targetEntryId,
                resolution = if (it.resolution == ProgressionResolution.PENDING) ProgressionResolution.STALE else it.resolution))
        }
    }

    /** Called before editing an unconfirmed plan. The first confirmation freezes its targets. */
    suspend fun beforeSetUpdate(old: WorkoutSet?, next: WorkoutSet) {
        if (old == null || old.confirmed || next.confirmed || workouts.confirmedCountForEntry(next.entryId) > 0) return
        dao.prescriptions(next.entryId).firstOrNull { it.plannedSetIndex == next.setIndex }?.let {
            dao.putPrescription(it.copy(plannedReps = next.reps, plannedKg = next.weightKg, plannedSeconds = next.seconds))
        }
    }

    suspend fun setAdded(set: WorkoutSet) {
        val link = dao.link(set.entryId) ?: return
        if (workouts.confirmedCountForEntry(set.entryId) > 0) {
            dao.putLink(link.copy(needsReview = true))
            return
        }
        val nextSourceIndex = (dao.prescriptions(set.entryId).maxOfOrNull { it.setIndex } ?: 0) + 1
        dao.putPrescription(ProgramPrescriptionSet(set.entryId, nextSourceIndex, 0, 0.0, 0,
            set.reps, set.weightKg, set.seconds, set.setIndex, originalExists = false))
    }

    suspend fun beforeSetRemoved(set: WorkoutSet) {
        val link = dao.link(set.entryId) ?: return
        if (workouts.confirmedCountForEntry(set.entryId) > 0) {
            dao.putLink(link.copy(needsReview = true))
            return
        }
        for (row in dao.prescriptions(set.entryId)) {
            val index = row.plannedSetIndex ?: continue
            if (index >= set.setIndex) dao.putPrescription(row.copy(plannedSetIndex = if (index == set.setIndex) null else index - 1))
        }
        if (link.basePolicy == ProgressionBase.ANCHOR) dao.putLink(link.copy(needsReview = true))
    }

    suspend fun refresh() = db.withTransaction {
        val links = dao.links()
        val prescriptions = dao.prescriptions().groupBy { it.entryId }
        val sessions = links.map { ProgressionSession(it, workouts.setsForEntry(it.entryId).sortedBy { s -> s.setIndex }, prescriptions[it.entryId].orEmpty()) }
        val restricted = restrictedKeys()
        val suggestions = dao.suggestions()
        // Including all same-chain evidence makes removals, edits and trend changes observable.
        for (old in suggestions.filter { it.resolution == ProgressionResolution.PENDING }) {
            val target = sessions.firstOrNull { it.link.entryId == old.targetEntryId }
            if (target == null || target.actual.any { it.confirmed } || evidence(target, sessions, restricted) != old.evidenceHash)
                dao.putSuggestion(old.copy(resolution = ProgressionResolution.STALE))
        }
        for (chain in sessions.groupBy { it.link.applicationId to it.link.trackId }.values) {
            val target = chain.sortedBy { it.link.sequence }.firstOrNull { session ->
                session.link.mode != ProgressionMode.OFF && session.actual.isNotEmpty() && session.actual.none { it.confirmed } &&
                    ProgressionEngine.predecessors(session.link, sessions).isNotEmpty() &&
                    chain.none { it.link.sequence < session.link.sequence && it.actual.any { s -> !s.confirmed } }
            } ?: continue
            val prior = ProgressionEngine.predecessors(target.link, sessions).first()
            val hash = evidence(target, sessions, restricted)
            val existing = dao.suggestions().filter { it.targetEntryId == target.link.entryId }
            // Explicit decisions stay authoritative even if their evidence is later edited.
            if (existing.any { it.resolution in setOf(ProgressionResolution.ACCEPTED, ProgressionResolution.KEPT_CURRENT_PLAN, ProgressionResolution.MANUAL_OVERRIDE) }) continue
            if (existing.any { it.evidenceHash == hash && it.resolution == ProgressionResolution.PENDING }) continue
            val key = workouts.findEntryById(target.link.entryId)?.exerciseStableKey
            val verdict = ProgressionEngine.evaluate(target.link, sessions, key in restricted)
            dao.putSuggestion(ProgressionSuggestion(applicationId = target.link.applicationId, trackId = target.link.trackId,
                sourceEntryId = prior.link.entryId, targetEntryId = target.link.entryId, previousActualKg = prior.base(),
                currentPlanKg = ProgressionEngine.base(target.actual, target.link.basePolicy, target.link.anchorSetIndex),
                suggestedKg = verdict.kg, judgmentRpe = verdict.rpe, direction = verdict.direction, reasons = verdict.reason,
                evidenceHash = hash, rule = target.link.rule))
        }
    }

    private suspend fun evidence(target: ProgressionSession, sessions: List<ProgressionSession>, restricted: Set<String>): String {
        val chain = sessions.filter { it.link.applicationId == target.link.applicationId && it.link.trackId == target.link.trackId && it.link.sequence <= target.link.sequence }.sortedBy { it.link.sequence }
        val key = workouts.findEntryById(target.link.entryId)?.exerciseStableKey
        val text = chain.joinToString("\n") { "${it.link}|${it.actual}|${it.targets}" } + "|local=${key in restricted}"
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun resolve(id: String, resolution: ProgressionResolution, manualKg: Double? = null): Boolean = db.withTransaction {
        require(resolution in setOf(ProgressionResolution.ACCEPTED, ProgressionResolution.KEPT_CURRENT_PLAN, ProgressionResolution.MANUAL_OVERRIDE))
        refresh()
        val suggestion = dao.suggestions().firstOrNull { it.id == id && it.resolution == ProgressionResolution.PENDING } ?: return@withTransaction false
        val target = dao.link(suggestion.targetEntryId) ?: return@withTransaction false
        val sets = workouts.setsForEntry(target.entryId)
        if (sets.isEmpty() || sets.any { it.confirmed }) return@withTransaction false
        val kg = when (resolution) {
            ProgressionResolution.ACCEPTED -> suggestion.suggestedKg ?: return@withTransaction false
            ProgressionResolution.MANUAL_OVERRIDE -> manualKg?.takeIf { it.isFinite() && it > 0 } ?: return@withTransaction false
            else -> null
        }
        if (kg != null) {
            val base = suggestion.currentPlanKg ?: return@withTransaction false
            if (base <= 0) return@withTransaction false
            for (set in sets) {
                val weight = if (target.basePolicy == ProgressionBase.UNIFORM) kg else kotlin.math.round(set.weightKg * kg / base * 2) / 2
                workouts.updateSet(set.copy(weightKg = weight, manualWeight = true))
                dao.prescriptions(target.entryId).firstOrNull { it.plannedSetIndex == set.setIndex }?.let {
                    dao.putPrescription(it.copy(plannedKg = weight))
                }
            }
        }
        dao.putSuggestion(suggestion.copy(resolution = resolution, resolvedAt = System.currentTimeMillis(), resolvedKg = kg))
        true
    }
}

internal fun progressionEligible(exercise: Exercise, roles: Set<String>): Boolean =
    exercise.resolvedActivityKind() == ActivityKind.TRAINING_EXERCISE &&
        roles.none { it in setOf("PLYOMETRIC", "SKILL_DRILL", "CONDITIONING") } &&
        (exercise.estimated1RmEligible || exercise.volumeLoadEligible || roles.any { it in setOf("STRENGTH", "HYPERTROPHY") })
