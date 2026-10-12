package com.training.trackplanner.data

import java.security.MessageDigest

internal class UnexplainedCanonicalIncumbentMutationException(
    val reasonCode: String,
    val stableKey: String,
    val selectionRole: String,
    val week: Int
) : IllegalStateException("$reasonCode:$stableKey#$selectionRole:w$week")

/**
 * Prevents replacing an existing saved canonical plan until the user confirms the preview save.
 * The comparison baseline is the actual persisted program snapshot, never a generated CONTROL.
 */
internal object CanonicalIncumbentSaveMutationGuard {
    private data class OwnerWeek(val stableKey: String, val selectionRole: String, val week: Int)

    private data class ProgressionIdentity(
        val sessionKey: String,
        val logicalItemId: String,
        val linkMode: ProgressionLinkMode,
        val signature: ProgressionSignature
    )

    private data class PhysicalRow(
        val week: Int,
        val day: Int,
        val order: Int,
        val stableKey: String,
        val role: String,
        val exerciseName: String,
        val category: String,
        val restSeconds: Int,
        val prescription: String,
        val setCount: Int,
        val reps: Int,
        val weightKg: Double,
        val seconds: Int,
        val trainingSlot: String?,
        val dayIntensity: String?,
        val weightSource: String?,
        val taskProtocolSemanticsJson: String?,
        val sets: List<ProgramSetPrescription>,
        val progression: ProgressionIdentity?
    )

    fun requireNoUnprovenMutation(
        persistedRows: List<TrainingProgramItem>,
        persistedSets: List<TrainingProgramItemSet>,
        persistedProgression: List<ProgramProgressionItem>,
        finalDraft: GeneratedProgramSkeleton,
        expectedSourceSnapshotToken: CanonicalIncumbentSourceSnapshotToken,
        userApprovedReplacement: Boolean = false
    ) {
        require(finalDraft.incumbentSourceSnapshotToken == expectedSourceSnapshotToken) {
            "CANONICAL_INCUMBENT_SOURCE_SNAPSHOT_MISMATCH"
        }
        val setsByItem = persistedSets.groupBy(TrainingProgramItemSet::programItemId)
        val progressionByItem = persistedProgression.associateBy(ProgramProgressionItem::programItemId)
        val before = persistedRows.map { row ->
            val role = row.selectionRole.orEmpty()
            OwnerWeek(row.exerciseStableKey, role, row.weekNumber) to persistedMaterial(
                row, setsByItem[row.id].orEmpty(), progressionByItem[row.id]
            )
        }.groupBy({ it.first }, { it.second })
        val after = finalDraft.items.map { row ->
            val ownerWeek = OwnerWeek(row.exerciseStableKey, row.selectionRole, row.weekNumber)
            val material = skeletonMaterial(row)
            val projectedProgression = if (row.progressionBinding != null) {
                material.progression
            } else {
                val unchangedSource = persistedRows.singleOrNull { source ->
                    source.exerciseStableKey == row.exerciseStableKey &&
                        source.selectionRole.orEmpty() == row.selectionRole &&
                        source.weekNumber == row.weekNumber &&
                        samePhysicalPrescription(source, setsByItem[source.id].orEmpty(), row)
                }
                unchangedSource?.let { progressionByItem[it.id]?.let(::progressionIdentity) }
            }
            ownerWeek to material.copy(progression = projectedProgression)
        }.groupBy({ it.first }, { it.second })

        val changedOwnerWeek = (before.keys + after.keys).distinct()
            .sortedWith(compareBy(OwnerWeek::stableKey, OwnerWeek::selectionRole, OwnerWeek::week))
            .firstOrNull { ownerWeek ->
                before[ownerWeek].orEmpty().sortedWith(PHYSICAL_ORDER) != after[ownerWeek].orEmpty().sortedWith(PHYSICAL_ORDER)
            } ?: return
        if (!userApprovedReplacement) {
            throw UnexplainedCanonicalIncumbentMutationException(
                "CANONICAL_INCUMBENT_REPLACEMENT_REQUIRES_EXPLICIT_USER_APPROVAL",
                changedOwnerWeek.stableKey,
                changedOwnerWeek.selectionRole,
                changedOwnerWeek.week
            )
        }
    }

    /** Exact set payload match used to retain progression only for an unchanged physical prescription. */
    fun samePhysicalPrescription(
        persisted: TrainingProgramItem,
        persistedSets: List<TrainingProgramItemSet>,
        generated: ProgramSkeletonItem
    ): Boolean {
        val stored = persistedMaterial(persisted, persistedSets, progression = null)
        val planned = skeletonMaterial(generated).copy(progression = null)
        return stored.copy(day = planned.day, order = planned.order) == planned
    }

    fun draftFingerprint(draft: GeneratedProgramSkeleton): String {
        val request = draft.request.copy(
            name = "",
            availableEquipment = draft.request.availableEquipment.toSortedSet()
        )
        val schedule = draft.weekDaySchedule.toSortedMap().entries.joinToString("|") { (week, days) ->
            "$week:${days.sorted().joinToString(",")}"
        }
        val rows = draft.items.sortedWith(compareBy(
            ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek, ProgramSkeletonItem::orderIndex,
            ProgramSkeletonItem::exerciseStableKey, ProgramSkeletonItem::selectionRole, ProgramSkeletonItem::localId
        )).joinToString("\n") { it.toString() }
        val sessions = draft.progressionSessions.sortedBy(DraftProgressionSession::key).joinToString("\n") { it.toString() }
        val weeks = draft.weekPlans.sortedBy(ProgramWeekPlan::weekIndex).joinToString("\n") { it.toString() }
        val decisionFingerprint = draft.personalizedDecision?.copy(
            decisionId = "",
            generatedAtEpochMillis = 0L
        )?.toString().orEmpty()
        val source = listOf(
            request.toString(), draft.durationDays, draft.periodizationType, schedule, weeks, rows, sessions,
            decisionFingerprint
        )
            .joinToString("\u001e")
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun persistedMaterial(
        row: TrainingProgramItem,
        sets: List<TrainingProgramItemSet>,
        progression: ProgramProgressionItem?
    ) = PhysicalRow(
        week = row.weekNumber,
        day = row.dayOfWeek,
        order = row.orderIndex,
        stableKey = row.exerciseStableKey,
        role = row.selectionRole.orEmpty(),
        exerciseName = row.exerciseName,
        category = row.category,
        restSeconds = row.restSeconds,
        prescription = row.prescription,
        setCount = row.setCount,
        reps = row.reps,
        weightKg = row.weightKg,
        seconds = row.seconds,
        trainingSlot = row.trainingSlot,
        dayIntensity = row.dayIntensity,
        weightSource = row.weightSource,
        taskProtocolSemanticsJson = row.taskProtocolSemanticsJson,
        sets = ProgramSetPrescriptionResolver.resolve(row, sets).sortedBy(ProgramSetPrescription::setIndex),
        progression = progression?.let(::progressionIdentity)
    )

    private fun progressionIdentity(item: ProgramProgressionItem) =
        ProgressionIdentity(item.trackId, item.logicalItemId, item.linkMode, item.signature)

    private fun skeletonMaterial(row: ProgramSkeletonItem) = PhysicalRow(
        week = row.weekNumber,
        day = row.dayOfWeek,
        order = row.orderIndex,
        stableKey = row.exerciseStableKey,
        role = row.selectionRole,
        exerciseName = row.exerciseName,
        category = row.category,
        restSeconds = row.restSeconds,
        prescription = row.prescription,
        setCount = row.setCount,
        reps = row.reps,
        weightKg = row.weightKg,
        seconds = row.seconds,
        trainingSlot = row.trainingSlot,
        dayIntensity = row.dayIntensity,
        weightSource = row.weightSource,
        taskProtocolSemanticsJson = row.taskProtocolSemanticsJson,
        sets = ProgramSetPrescriptionResolver.resolve(row),
        progression = row.progressionBinding?.let {
            ProgressionIdentity(it.sessionKey, it.logicalItemId, it.linkMode, it.signature)
        }
    )

    private val PHYSICAL_ORDER = compareBy<PhysicalRow>(
        PhysicalRow::week, PhysicalRow::day, PhysicalRow::order, PhysicalRow::stableKey, PhysicalRow::role,
        PhysicalRow::exerciseName, PhysicalRow::category, PhysicalRow::prescription
    )
}
