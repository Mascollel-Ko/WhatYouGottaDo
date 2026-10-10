package com.training.trackplanner.data

import java.security.MessageDigest

internal class UnexplainedCanonicalIncumbentMutationException(
    val reasonCode: String,
    val stableKey: String,
    val selectionRole: String,
    val week: Int
) : IllegalStateException("$reasonCode:$stableKey#$selectionRole:w$week")

/**
 * Prevents replacing an existing saved canonical plan with a draft that silently drops or
 * changes an exact owner-week. B7/B8 evidence is accepted only when the persisted owner-week
 * was the exact CONTROL material that B7/B8 adjudicated and the final draft has not changed.
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
        expectedSourceSnapshotToken: CanonicalIncumbentSourceSnapshotToken
    ) {
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

        val evidence = finalDraft.incumbentSaveMutationEvidence
        val evidenceUsable = evidence != null &&
            evidence.sourceSnapshotToken == expectedSourceSnapshotToken &&
            evidence.finalDraftFingerprint == draftFingerprint(finalDraft) &&
            evidence.readiness.status == com.training.trackplanner.data.personalized.StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW &&
            evidence.readiness.materializationIntegrityPassed &&
            evidence.readiness.changeProvenanceClosed &&
            evidence.readiness.collateralRegressionFree &&
            evidence.readiness.changeAttributions.none {
                it.source == com.training.trackplanner.data.personalized.StimulusExperimentalChangeAttributionSource.UNEXPLAINED ||
                    it.source == com.training.trackplanner.data.personalized.StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT
            } &&
            evidence.cutover.status == com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER &&
            evidence.route.productionRoutingActive &&
            evidence.route.selectedSource != com.training.trackplanner.data.personalized.StimulusProductionProgramSource.CONTROL &&
            evidence.route.b8Status == evidence.cutover.status && evidence.route.b8Scope == evidence.cutover.scope

        // New identities are governed by the generation's B6/B7/B8/B9 contract. This guard
        // protects only rows that already existed in the saved program from silent loss/change.
        before.keys.sortedWith(compareBy(OwnerWeek::stableKey, OwnerWeek::selectionRole, OwnerWeek::week))
            .forEach { ownerWeek ->
                val oldRows = before[ownerWeek].orEmpty().sortedWith(PHYSICAL_ORDER)
                val newRows = after[ownerWeek].orEmpty().sortedWith(PHYSICAL_ORDER)
                if (oldRows == newRows) return@forEach

                val comparatorRows = evidence?.controlRows.orEmpty().mapNotNull { row ->
                    if (row.exerciseStableKey != ownerWeek.stableKey || row.selectionRole != ownerWeek.selectionRole ||
                        row.weekNumber != ownerWeek.week
                    ) null else skeletonMaterial(row)
                }.map { it.copy(progression = null) }.sortedWith(PHYSICAL_ORDER)
                val ownerCause = evidence?.readiness?.changeAttributions.orEmpty().singleOrNull {
                    it.stableKey == ownerWeek.stableKey && it.selectionRole == ownerWeek.selectionRole &&
                        it.source != com.training.trackplanner.data.personalized.StimulusExperimentalChangeAttributionSource.UNEXPLAINED &&
                        it.source != com.training.trackplanner.data.personalized.StimulusExperimentalChangeAttributionSource.INCONCLUSIVE_DISPLACEMENT &&
                        it.targetIds.isNotEmpty() && it.reasonCodes.isNotEmpty() && it.evidenceSources.isNotEmpty()
                }
                val exactComparatorMatch = comparatorRows == oldRows.map { it.copy(progression = null) }.sortedWith(PHYSICAL_ORDER)
                val ownerIsB8Authorized = evidence?.cutover?.let { cutover ->
                    (cutover.authorizedOwnerIdentities + cutover.authorizedAuthorityIdentities.map { it.owner })
                        .any { it.stableKey == ownerWeek.stableKey && it.selectionRole == ownerWeek.selectionRole }
                } == true
                val exactSelectedReplacement = if (newRows.isEmpty() && ownerCause != null && evidence != null) {
                    ownerCause.source == com.training.trackplanner.data.personalized.StimulusExperimentalChangeAttributionSource.USER_APPROVED_EXACT_EXERCISE_REPLACEMENT &&
                        evidence.readiness.userApprovedReplacementEdges.any { edge ->
                            val weekControl = edge.controlRows.filter { it.weekNumber == ownerWeek.week }
                                .map { skeletonMaterial(it).copy(progression = null) }.sortedWith(PHYSICAL_ORDER)
                            val weekReplacement = edge.replacementRows.filter { it.weekNumber == ownerWeek.week }
                                .map { skeletonMaterial(it).copy(progression = null) }.sortedWith(PHYSICAL_ORDER)
                            val finalReplacement = finalDraft.items.filter {
                                it.weekNumber == ownerWeek.week &&
                                    it.exerciseStableKey == edge.replacementOwner.stableKey &&
                                    it.selectionRole == edge.replacementOwner.selectionRole
                            }.map { skeletonMaterial(it).copy(progression = null) }.sortedWith(PHYSICAL_ORDER)
                            edge.displacedControlOwner.stableKey == ownerWeek.stableKey &&
                                edge.displacedControlOwner.selectionRole == ownerWeek.selectionRole &&
                                ownerCause.replacementOwner == edge.replacementOwner &&
                                ownerCause.replacementEvidenceId == edge.optionId &&
                                edge.targetId in ownerCause.targetIds &&
                                weekControl == oldRows.map { it.copy(progression = null) }.sortedWith(PHYSICAL_ORDER) &&
                                weekReplacement.isNotEmpty() && weekReplacement == finalReplacement &&
                                (evidence.cutover.authorizedOwnerIdentities + evidence.cutover.authorizedAuthorityIdentities.map { it.owner })
                                    .any { it.stableKey == edge.replacementOwner.stableKey && it.selectionRole == edge.replacementOwner.selectionRole }
                        }
                } else false
                val exactMutationAuthorization = if (newRows.isEmpty()) exactSelectedReplacement else ownerIsB8Authorized
                if (!evidenceUsable || ownerCause == null || !exactComparatorMatch || !exactMutationAuthorization) {
                    val reason = when {
                        !evidenceUsable -> "CANONICAL_INCUMBENT_MUTATION_WITHOUT_B7_B8_B9_AUTHORITY"
                        ownerCause == null -> "CANONICAL_INCUMBENT_OWNER_CAUSAL_EVIDENCE_MISSING"
                        !exactComparatorMatch -> "CANONICAL_INCUMBENT_SOURCE_NOT_EXACTLY_B7_CONTROL"
                        else -> "CANONICAL_INCUMBENT_OWNER_NOT_EXACTLY_AUTHORIZED_BY_B8"
                    }
                    throw UnexplainedCanonicalIncumbentMutationException(
                        reason, ownerWeek.stableKey, ownerWeek.selectionRole, ownerWeek.week
                    )
                }
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
