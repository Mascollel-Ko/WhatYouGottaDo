package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
import com.training.trackplanner.data.personalized.PlanningHistorySnapshot
import com.training.trackplanner.data.personalized.AthletePlanningState
import com.training.trackplanner.data.personalized.ProgramProjectionValidator
import com.training.trackplanner.data.personalized.PrimaryStrengthAnchorSpacingPolicy
import com.training.trackplanner.data.personalized.placementSessionFits
import java.security.MessageDigest

/** Current builder contract recorded on accepted canonical programs. */
internal const val CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.69.0"

/** A typed view of TrainingProgram.stableKey; it is independent of exercise stable keys. */
@JvmInline
value class CanonicalProgramLineageId(val value: String) {
    init { require(value.isNotBlank()) }
}

internal data class CanonicalOwnerIdentity(
    val stableKey: String,
    val selectionRole: String
) {
    init {
        require(stableKey.isNotBlank())
        require(selectionRole.isNotBlank())
    }
}

internal enum class CanonicalIncumbentSourceType {
    CURRENT_PERSISTED_PROGRAM
}

internal data class CanonicalIncumbentProgramSource(
    val programId: Long,
    val lineageId: CanonicalProgramLineageId,
    val builderProtocolVersion: String,
    val plannerRuntimeVersion: String,
    val sourceType: CanonicalIncumbentSourceType = CanonicalIncumbentSourceType.CURRENT_PERSISTED_PROGRAM
)

internal data class CanonicalIncumbentPlacementPreservation(
    val owner: CanonicalOwnerIdentity,
    val week: Int,
    val producedDay: Int,
    val producedOrder: Int,
    val preservedDay: Int,
    val preservedOrder: Int,
    val sourceLineageId: CanonicalProgramLineageId,
    val sourceSnapshotToken: CanonicalIncumbentSourceSnapshotToken,
    val feasibility: CanonicalIncumbentFeasibilityEvidence
)

/** Hash of the persisted source read before generation; it is checked before replacing that source. */
@JvmInline
value class CanonicalIncumbentSourceSnapshotToken(val value: String) {
    init { require(value.matches(Regex("[0-9a-f]{64}"))) }
}

internal data class CanonicalIncumbentOwnerWeek(
    val owner: CanonicalOwnerIdentity,
    val week: Int
)

internal data class CanonicalIncumbentPlacement(
    val owner: CanonicalOwnerIdentity,
    val week: Int,
    val day: Int,
    val order: Int,
    val source: CanonicalIncumbentProgramSource
)

internal enum class CanonicalIncumbentIndexStatus {
    AVAILABLE,
    NO_EXISTING_PROGRAM,
    SOURCE_IDENTITY_INVALID,
    SOURCE_VERSION_UNKNOWN,
    SOURCE_VERSION_INCOMPATIBLE
}

internal data class CanonicalIncumbentPlacementIndex(
    val status: CanonicalIncumbentIndexStatus,
    val source: CanonicalIncumbentProgramSource?,
    val placements: List<CanonicalIncumbentPlacement>,
    val ambiguousOwnerWeeks: Set<CanonicalIncumbentOwnerWeek>,
    val omittedRowsWithoutExactRole: Int,
    val omittedInvalidRows: Int,
    val sourceSnapshotToken: CanonicalIncumbentSourceSnapshotToken? = null
) {
    fun placement(owner: CanonicalOwnerIdentity, week: Int): CanonicalIncumbentPlacement? {
        if (status != CanonicalIncumbentIndexStatus.AVAILABLE) return null
        val key = CanonicalIncumbentOwnerWeek(owner, week)
        if (key in ambiguousOwnerWeeks) return null
        return placements.singleOrNull { it.owner == owner && it.week == week }
    }

    companion object {
        fun unavailable(
            status: CanonicalIncumbentIndexStatus,
            sourceSnapshotToken: CanonicalIncumbentSourceSnapshotToken? = null
        ) = CanonicalIncumbentPlacementIndex(
            status = status,
            source = null,
            placements = emptyList(),
            ambiguousOwnerWeeks = emptySet(),
            omittedRowsWithoutExactRole = 0,
            omittedInvalidRows = 0,
            sourceSnapshotToken = sourceSnapshotToken
        )

        fun fromPersistedProgram(
            program: TrainingProgram?,
            expectedProgramId: Long?,
            rows: List<TrainingProgramItem>,
            expectedBuilderProtocolVersion: String = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
            expectedPlannerRuntimeVersion: String = PERSONALIZED_PLANNER_PROTOCOL,
            sourceSnapshotToken: CanonicalIncumbentSourceSnapshotToken? = null
        ): CanonicalIncumbentPlacementIndex {
            if (expectedProgramId == null) return unavailable(CanonicalIncumbentIndexStatus.NO_EXISTING_PROGRAM, sourceSnapshotToken)
            if (program == null || expectedProgramId <= 0 || program.id != expectedProgramId || program.stableKey.isBlank()) {
                return unavailable(CanonicalIncumbentIndexStatus.SOURCE_IDENTITY_INVALID, sourceSnapshotToken)
            }
            val builderVersion = program.canonicalBuilderProtocolVersion
            val runtimeVersion = program.canonicalPlannerRuntimeVersion
            if (builderVersion.isNullOrBlank() || runtimeVersion.isNullOrBlank()) {
                return unavailable(CanonicalIncumbentIndexStatus.SOURCE_VERSION_UNKNOWN, sourceSnapshotToken)
            }
            val sourceContract = builderVersion to runtimeVersion
            val supportedContracts = setOf(
                expectedBuilderProtocolVersion to expectedPlannerRuntimeVersion,
                C35_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C35_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C34_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C34_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C33_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C33_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C32_1_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C32_1_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C32_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C32_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C24_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C24_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C23_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C23_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C22_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C22_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C21_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C21_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C20_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C20_PERSONALIZED_PLANNER_PROTOCOL_VERSION,
                C19_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION to C19_PERSONALIZED_PLANNER_PROTOCOL_VERSION
            )
            if (sourceContract !in supportedContracts) {
                return unavailable(CanonicalIncumbentIndexStatus.SOURCE_VERSION_INCOMPATIBLE, sourceSnapshotToken)
            }

            val source = CanonicalIncumbentProgramSource(
                programId = program.id,
                lineageId = CanonicalProgramLineageId(program.stableKey),
                builderProtocolVersion = builderVersion,
                plannerRuntimeVersion = runtimeVersion
            )
            val validRows = rows.mapNotNull { row ->
                val role = row.selectionRole?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                if (row.programId != program.id || row.exerciseStableKey.isBlank() ||
                    row.weekNumber <= 0 || row.dayOfWeek !in 1..7 || row.orderIndex <= 0
                ) return@mapNotNull null
                CanonicalIncumbentPlacement(
                    owner = CanonicalOwnerIdentity(row.exerciseStableKey, role),
                    week = row.weekNumber,
                    day = row.dayOfWeek,
                    order = row.orderIndex,
                    source = source
                )
            }
            val ambiguous = validRows.groupBy { CanonicalIncumbentOwnerWeek(it.owner, it.week) }
                .filterValues { it.size > 1 }.keys.toSortedSet(
                    compareBy<CanonicalIncumbentOwnerWeek>({ it.owner.stableKey }, { it.owner.selectionRole }, { it.week })
                )
            val placements = validRows
                .filterNot { CanonicalIncumbentOwnerWeek(it.owner, it.week) in ambiguous }
                .sortedWith(compareBy({ it.owner.stableKey }, { it.owner.selectionRole }, { it.week }, { it.day }, { it.order }))
            return CanonicalIncumbentPlacementIndex(
                status = CanonicalIncumbentIndexStatus.AVAILABLE,
                source = source,
                placements = placements,
                ambiguousOwnerWeeks = ambiguous,
                omittedRowsWithoutExactRole = rows.count { it.selectionRole.isNullOrBlank() },
                omittedInvalidRows = rows.count { row ->
                    row.selectionRole?.isNotBlank() == true &&
                        (row.programId != program.id || row.exerciseStableKey.isBlank() || row.weekNumber <= 0 ||
                            row.dayOfWeek !in 1..7 || row.orderIndex <= 0)
                },
                sourceSnapshotToken = sourceSnapshotToken
            )
        }
    }
}

/** C19 persisted exact lineage/roles before C20 changed placement behavior. */
private const val C19_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.54.0"
private const val C19_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.14.6_KOTLIN_1"
/** C35 persisted programs remain valid incumbent sources after C36's exact B7 capacity-attribution contract. */
private const val C35_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.68.0"
private const val C35_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.15.10_KOTLIN_1"
/** C34 exact replacement-attribution programs remain compatible after C35 separates aggregate and regional H semantics. */
private const val C34_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.67.0"
private const val C34_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.15.9_KOTLIN_1"
/** C33 programs remain compatible after C34 adds exact movement-removal attribution evidence. */
private const val C33_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.66.0"
private const val C33_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.15.8_KOTLIN_1"
/** C32 exact Strength exposure programs remain compatible after the C32.1 intent/evidence split. */
private const val C32_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.64.0"
private const val C32_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.15.6_KOTLIN_1"
/** C32.1 planned-intent and uncertain-exposure programs remain compatible after C33 integration. */
private const val C32_1_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.65.0"
private const val C32_1_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.15.7_KOTLIN_1"
/** C20 persisted exact incumbent sources remain eligible after the C21 planner contract bump. */
private const val C20_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.55.0"
private const val C20_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.14.7_KOTLIN_1"
/** C21 source programs remain eligible after C22 closes the task-owner materialization boundary. */
private const val C21_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.56.0"
private const val C21_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.14.8_KOTLIN_1"
/** C22 programs remain compatible incumbent sources after the C23 task-shape contract addition. */
private const val C22_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.57.0"
private const val C22_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.14.9_KOTLIN_1"
private const val C23_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.58.0"
private const val C23_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.15.0_KOTLIN_1"
private const val C24_CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.59.0"
private const val C24_PERSONALIZED_PLANNER_PROTOCOL_VERSION = "RECORD_BASED_PLANNER_0.15.1_KOTLIN_1"

/** Deterministic token over all persisted program/item/set state that a generated replacement can overwrite. */
internal object CanonicalIncumbentSourceSnapshotFingerprint {
    fun create(
        program: TrainingProgram,
        rows: List<TrainingProgramItem>,
        sets: List<TrainingProgramItemSet>
    ): CanonicalIncumbentSourceSnapshotToken {
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(value: String?) {
            val bytes = (value ?: "<null>").toByteArray(Charsets.UTF_8)
            digest.update(byteArrayOf(
                (bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(),
                (bytes.size ushr 8).toByte(), bytes.size.toByte()
            ))
            digest.update(bytes)
        }
        fun record(kind: String, values: List<Any?>) {
            field(kind)
            values.forEach { field(it?.toString()) }
        }

        record("program", listOf(
            program.id, program.stableKey, program.name, program.durationDays, program.createdAt, program.goal,
            program.weeklyTrainingDays, program.sessionMinutes, program.availableEquipment, program.excludedExerciseText,
            program.badmintonTransferRatio, program.sportStrengthRatio, program.periodizationType, program.updatedAt,
            program.canonicalBuilderProtocolVersion, program.canonicalPlannerRuntimeVersion
        ))
        val setsByItem = sets.groupBy(TrainingProgramItemSet::programItemId)
        rows.sortedWith(compareBy(TrainingProgramItem::weekNumber, TrainingProgramItem::dayOfWeek,
            TrainingProgramItem::orderIndex, TrainingProgramItem::exerciseStableKey,
            { it.selectionRole.orEmpty() }, TrainingProgramItem::id)).forEach { row ->
            record("item", listOf(
                row.id, row.programId, row.weekNumber, row.dayOfWeek, row.orderIndex, row.exerciseStableKey,
                row.exerciseName, row.category, row.restSeconds, row.prescription, row.setCount, row.reps,
            row.weightKg, row.seconds, row.trainingSlot, row.dayIntensity, row.weightSource, row.selectionRole,
            row.taskProtocolSemanticsJson
            ))
            setsByItem[row.id].orEmpty().sortedBy(TrainingProgramItemSet::setIndex).forEach { set ->
                record("set", listOf(set.id, set.programItemId, set.setIndex, set.reps, set.weightKg,
                    set.seconds, set.targetRpeMin, set.loadState))
            }
        }
        val hex = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        return CanonicalIncumbentSourceSnapshotToken(hex)
    }
}

internal enum class CanonicalIncumbentFeasibility {
    HARD_VALID,
    HARD_INVALID,
    UNRESOLVED
}

internal enum class CanonicalIncumbentRecommendation {
    PRESERVE_INCUMBENT,
    INCUMBENT_REJECTED_HARD_CONSTRAINT,
    NO_DECISION_UNRESOLVED
}

internal data class CanonicalIncumbentShadowRow(
    val owner: CanonicalOwnerIdentity,
    val week: Int,
    val incumbentDay: Int,
    val incumbentOrder: Int,
    val productionDay: Int,
    val productionOrder: Int,
    val feasibility: CanonicalIncumbentFeasibility,
    val recommendation: CanonicalIncumbentRecommendation,
    val evidence: CanonicalIncumbentFeasibilityEvidence? = null
)

internal data class CanonicalIncumbentPlacementShadow(
    val indexStatus: CanonicalIncumbentIndexStatus,
    val rows: List<CanonicalIncumbentShadowRow>,
    val combinedFeasibility: CanonicalIncumbentFeasibilityEvidence? = null,
    val combinedAnchorSetConflict: Boolean = false,
    val shadowRows: List<ProgramSkeletonItem> = emptyList(),
    val projectionCallCount: Int = 0,
    val sourceLineageId: String? = null,
    val sourceSnapshotToken: CanonicalIncumbentSourceSnapshotToken? = null,
    /** Canonical EXP rows before C20 activation; retained for immutable audit comparisons. */
    val productionRows: List<ProgramSkeletonItem> = emptyList(),
    val dayOfiProjectionCallCount: Int = 0,
    val tissueProjectionCallCount: Int = 0
)

/** Read-only shadow. It can report a recommendation only when an exact hard-feasibility result exists. */
internal object CanonicalIncumbentPlacementShadowEvaluator {
    fun evaluate(
        index: CanonicalIncumbentPlacementIndex,
        currentRows: List<ProgramSkeletonItem>,
        feasibilityByOwnerWeek: Map<CanonicalIncumbentOwnerWeek, CanonicalIncumbentFeasibility> = emptyMap(),
        evidenceByOwnerWeek: Map<CanonicalIncumbentOwnerWeek, CanonicalIncumbentFeasibilityEvidence> = emptyMap(),
        combinedFeasibility: CanonicalIncumbentFeasibilityEvidence? = null,
        combinedAnchorSetConflict: Boolean = false,
        shadowRows: List<ProgramSkeletonItem> = currentRows,
        projectionCallCount: Int = 0,
        dayOfiProjectionCallCount: Int = 0,
        tissueProjectionCallCount: Int = 0
    ): CanonicalIncumbentPlacementShadow {
        if (index.status != CanonicalIncumbentIndexStatus.AVAILABLE) {
            return CanonicalIncumbentPlacementShadow(index.status, emptyList(), combinedFeasibility,
                combinedAnchorSetConflict, currentRows, projectionCallCount, index.source?.lineageId?.value,
                index.sourceSnapshotToken, currentRows, dayOfiProjectionCallCount, tissueProjectionCallCount)
        }
        val current = currentRows.mapNotNull { row ->
            val role = row.selectionRole.takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (row.weekNumber <= 0 || row.dayOfWeek !in 1..7 || row.orderIndex <= 0) return@mapNotNull null
            CanonicalIncumbentOwnerWeek(CanonicalOwnerIdentity(row.exerciseStableKey, role), row.weekNumber) to row
        }.groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { (_, rowsForOwnerWeek) -> rowsForOwnerWeek.single() }
        val rows = index.placements.mapNotNull { incumbent ->
            val key = CanonicalIncumbentOwnerWeek(incumbent.owner, incumbent.week)
            val production = current[key] ?: return@mapNotNull null // Removed owners are never forced back.
            val feasibility = feasibilityByOwnerWeek[key] ?: CanonicalIncumbentFeasibility.UNRESOLVED
            val recommendation = when (feasibility) {
                CanonicalIncumbentFeasibility.HARD_VALID -> CanonicalIncumbentRecommendation.PRESERVE_INCUMBENT
                CanonicalIncumbentFeasibility.HARD_INVALID -> CanonicalIncumbentRecommendation.INCUMBENT_REJECTED_HARD_CONSTRAINT
                CanonicalIncumbentFeasibility.UNRESOLVED -> CanonicalIncumbentRecommendation.NO_DECISION_UNRESOLVED
            }
            CanonicalIncumbentShadowRow(
                owner = incumbent.owner,
                week = incumbent.week,
                incumbentDay = incumbent.day,
                incumbentOrder = incumbent.order,
                productionDay = production.dayOfWeek,
                productionOrder = production.orderIndex,
                feasibility = feasibility,
                recommendation = recommendation,
                evidence = evidenceByOwnerWeek[key]
            )
        }.sortedWith(compareBy({ it.owner.stableKey }, { it.owner.selectionRole }, { it.week }))
        return CanonicalIncumbentPlacementShadow(index.status, rows, combinedFeasibility,
            combinedAnchorSetConflict, shadowRows, projectionCallCount, index.source?.lineageId?.value,
            index.sourceSnapshotToken, currentRows, dayOfiProjectionCallCount, tissueProjectionCallCount)
    }
}

internal enum class CanonicalIncumbentHardConstraint {
    PROGRAM_PROJECTION,
    DAY_NOT_AVAILABLE,
    SAME_EXERCISE_TWICE_IN_DAY,
    SESSION_TIME_CAPACITY,
    DAY_OFI_OR_AXIS_GATE,
    TISSUE_HARD_GATE,
    PRIMARY_STRENGTH_SPACING,
    OWNER_FREQUENCY_CHANGED,
    PRESCRIPTION_CHANGED
}

internal enum class CanonicalIncumbentUnresolvedConstraint {
    CURRENT_OWNER_AMBIGUOUS,
    DAY_PROJECTION_UNAVAILABLE,
    TISSUE_PROJECTION_UNAVAILABLE,
    TISSUE_INPUT_UNRESOLVED,
    TISSUE_PROJECTION_NOT_CANONICAL,
    INCUMBENT_DAY_SCHEDULE_UNAVAILABLE
}

internal data class CanonicalIncumbentFeasibilityEvidence(
    val status: CanonicalIncumbentFeasibility,
    val hardReasons: List<CanonicalIncumbentHardConstraint>,
    val unresolvedReasons: List<CanonicalIncumbentUnresolvedConstraint>,
    val details: List<String> = emptyList()
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("status", status.name)
        .put("hardReasons", org.json.JSONArray(hardReasons.map(Enum<*>::name).distinct().sorted()))
        .put("unresolvedReasons", org.json.JSONArray(unresolvedReasons.map(Enum<*>::name).distinct().sorted()))
        .put("details", org.json.JSONArray(details.distinct().sorted()))
}

internal data class CanonicalIncumbentLiveFeasibility(
    val byOwnerWeek: Map<CanonicalIncumbentOwnerWeek, CanonicalIncumbentFeasibilityEvidence>,
    val combinedHardValidAnchors: CanonicalIncumbentFeasibilityEvidence,
    val combinedAnchorSetConflict: Boolean,
    val shadowRows: List<ProgramSkeletonItem>,
    val projectionCallCount: Int,
    val dayOfiProjectionCallCount: Int = 0,
    val tissueProjectionCallCount: Int = 0
)

internal enum class CanonicalIncumbentActivationStatus {
    ACTIVATED,
    NO_ELIGIBLE_HARD_VALID_ANCHORS,
    SOURCE_UNAVAILABLE,
    SOURCE_SNAPSHOT_MISSING,
    COMBINED_ANCHORS_NOT_HARD_VALID,
    NON_ANCHOR_PLACEMENT_WOULD_CHANGE
}

internal data class CanonicalIncumbentPlacementActivation(
    val status: CanonicalIncumbentActivationStatus,
    val program: GeneratedProgramSkeleton,
    val preservations: List<CanonicalIncumbentPlacementPreservation>,
    val rejectionDetails: List<String> = emptyList()
)

/** Applies only the exact, live-proven hard-valid anchors. All other rows keep the canonical result. */
internal object CanonicalIncumbentPlacementActivator {
    fun activate(
        index: CanonicalIncumbentPlacementIndex,
        program: GeneratedProgramSkeleton,
        feasibility: CanonicalIncumbentLiveFeasibility
    ): CanonicalIncumbentPlacementActivation {
        fun unchanged(status: CanonicalIncumbentActivationStatus, detail: String? = null) =
            CanonicalIncumbentPlacementActivation(status, program, emptyList(), listOfNotNull(detail))

        if (index.status != CanonicalIncumbentIndexStatus.AVAILABLE || index.source == null) {
            return unchanged(CanonicalIncumbentActivationStatus.SOURCE_UNAVAILABLE)
        }
        val snapshotToken = index.sourceSnapshotToken
            ?: return unchanged(CanonicalIncumbentActivationStatus.SOURCE_SNAPSHOT_MISSING)
        if (feasibility.combinedAnchorSetConflict ||
            feasibility.combinedHardValidAnchors.status != CanonicalIncumbentFeasibility.HARD_VALID
        ) return unchanged(CanonicalIncumbentActivationStatus.COMBINED_ANCHORS_NOT_HARD_VALID)

        val validKeys = feasibility.byOwnerWeek.entries
            .filter { it.value.status == CanonicalIncumbentFeasibility.HARD_VALID }
            .map { it.key }
            .toSortedSet(compareBy({ it.owner.stableKey }, { it.owner.selectionRole }, { it.week }))
        if (validKeys.isEmpty()) return unchanged(CanonicalIncumbentActivationStatus.NO_ELIGIBLE_HARD_VALID_ANCHORS)

        val candidateById = feasibility.shadowRows.associateBy(ProgramSkeletonItem::localId)
        val currentById = program.items.associateBy(ProgramSkeletonItem::localId)
        if (candidateById.keys != currentById.keys || candidateById.size != feasibility.shadowRows.size ||
            currentById.size != program.items.size
        ) return unchanged(CanonicalIncumbentActivationStatus.NON_ANCHOR_PLACEMENT_WOULD_CHANGE, "ROW_IDENTITY_SET_CHANGED")

        val changed = program.items.filter { before ->
            val after = candidateById.getValue(before.localId)
            before.dayOfWeek != after.dayOfWeek || before.orderIndex != after.orderIndex
        }
        val nonPlacementMutation = program.items.any { before ->
            val after = candidateById.getValue(before.localId)
            before.copy(dayOfWeek = after.dayOfWeek, orderIndex = after.orderIndex) != after
        }
        if (nonPlacementMutation) return unchanged(
            CanonicalIncumbentActivationStatus.NON_ANCHOR_PLACEMENT_WOULD_CHANGE, "NON_PLACEMENT_FIELD_CHANGED"
        )

        val anchorDestinations = validKeys.mapNotNull { key ->
            val incumbent = index.placement(key.owner, key.week) ?: return@mapNotNull null
            Triple(key.week, incumbent.day, incumbent.order)
        }.toSet()
        val nonAnchorPlacementChangesAreExactSlotConflicts = changed.all { before ->
            val key = CanonicalIncumbentOwnerWeek(CanonicalOwnerIdentity(before.exerciseStableKey, before.selectionRole), before.weekNumber)
            if (key in validKeys) {
                val incumbent = index.placement(key.owner, key.week) ?: return@all false
                candidateById.getValue(before.localId).let { after ->
                    after.dayOfWeek == incumbent.day && after.orderIndex == incumbent.order
                }
            } else {
                val after = candidateById.getValue(before.localId)
                // A continuity anchor may reserve an already occupied order slot. The shadow may
                // move that exact colliding row's order within the same day so both rows remain
                // distinct. It may not move an unrelated row across days or cause a wider cascade.
                before.dayOfWeek == after.dayOfWeek && before.orderIndex != after.orderIndex &&
                    Triple(before.weekNumber, before.dayOfWeek, before.orderIndex) in anchorDestinations
            }
        }
        if (!nonAnchorPlacementChangesAreExactSlotConflicts) {
            val details = changed.mapNotNull { before ->
                val after = candidateById.getValue(before.localId)
                val key = CanonicalIncumbentOwnerWeek(
                    CanonicalOwnerIdentity(before.exerciseStableKey, before.selectionRole), before.weekNumber
                )
                if (key in validKeys) {
                    val incumbent = index.placement(key.owner, key.week)
                    if (incumbent == null || after.dayOfWeek != incumbent.day || after.orderIndex != incumbent.order) {
                        "ANCHOR_NOT_AT_EXACT_INCUMBENT:${before.exerciseStableKey}#${before.selectionRole}:w${before.weekNumber}"
                    } else null
                } else if (before.dayOfWeek != after.dayOfWeek || before.orderIndex == after.orderIndex ||
                    Triple(before.weekNumber, before.dayOfWeek, before.orderIndex) !in anchorDestinations
                ) {
                    "NON_ANCHOR_NOT_EXACT_SLOT_CONFLICT:${before.exerciseStableKey}#${before.selectionRole}:w${before.weekNumber}:${before.dayOfWeek}/${before.orderIndex}->${after.dayOfWeek}/${after.orderIndex}"
                } else null
            }.distinct().sorted()
            return unchanged(CanonicalIncumbentActivationStatus.NON_ANCHOR_PLACEMENT_WOULD_CHANGE, details.joinToString(";"))
        }
        val duplicateFinalOrders = feasibility.shadowRows.groupBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
            .filterValues { it.size > 1 }
        if (duplicateFinalOrders.isNotEmpty()) {
            return unchanged(CanonicalIncumbentActivationStatus.NON_ANCHOR_PLACEMENT_WOULD_CHANGE,
                "DUPLICATE_FINAL_ORDER:${duplicateFinalOrders.keys.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))}")
        }

        val preservations = changed.filter { before ->
            CanonicalIncumbentOwnerWeek(
                CanonicalOwnerIdentity(before.exerciseStableKey, before.selectionRole), before.weekNumber
            ) in validKeys
        }.map { before ->
            val owner = CanonicalOwnerIdentity(before.exerciseStableKey, before.selectionRole)
            val key = CanonicalIncumbentOwnerWeek(owner, before.weekNumber)
            val incumbent = requireNotNull(index.placement(owner, before.weekNumber))
            val evidence = requireNotNull(feasibility.byOwnerWeek[key])
            CanonicalIncumbentPlacementPreservation(
                owner = owner,
                week = before.weekNumber,
                producedDay = before.dayOfWeek,
                producedOrder = before.orderIndex,
                preservedDay = incumbent.day,
                preservedOrder = incumbent.order,
                sourceLineageId = incumbent.source.lineageId,
                sourceSnapshotToken = snapshotToken,
                feasibility = evidence
            )
        }.sortedWith(compareBy({ it.owner.stableKey }, { it.owner.selectionRole }, { it.week }))
        val stabilized = if (preservations.isEmpty()) program else {
            val finalFingerprint = com.training.trackplanner.data.personalized.personalizedProgramFingerprint(
                program.request, feasibility.shadowRows
            )
            program.copy(
                items = feasibility.shadowRows,
                personalizedDecision = program.personalizedDecision?.copy(
                    canonicalPlacementFinalFingerprint = finalFingerprint
                )
            )
        }
        return CanonicalIncumbentPlacementActivation(
            status = CanonicalIncumbentActivationStatus.ACTIVATED,
            program = stabilized,
            preservations = preservations
        )
    }
}

/** Evaluates incumbent placements with the same OFI/tissue projections used by placement review. */
internal object CanonicalIncumbentPlacementFeasibilityEvaluator {
    private data class ProjectionCallCounts(var dayOfi: Int = 0, var tissue: Int = 0)

    fun evaluate(
        index: CanonicalIncumbentPlacementIndex,
        program: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState
    ): CanonicalIncumbentLiveFeasibility {
        if (index.status != CanonicalIncumbentIndexStatus.AVAILABLE) {
            val unresolved = evidence(unresolved = listOf(CanonicalIncumbentUnresolvedConstraint.CURRENT_OWNER_AMBIGUOUS))
            return CanonicalIncumbentLiveFeasibility(emptyMap(), unresolved, false, program.items, 0)
        }
        val currentByOwnerWeek = program.items.mapNotNull { row ->
            if (row.selectionRole.isBlank()) return@mapNotNull null
            CanonicalIncumbentOwnerWeek(CanonicalOwnerIdentity(row.exerciseStableKey, row.selectionRole), row.weekNumber) to row
        }.groupBy({ it.first }, { it.second })

        val keys = index.placements.map { CanonicalIncumbentOwnerWeek(it.owner, it.week) }.distinct()
            .sortedWith(compareBy({ it.owner.stableKey }, { it.owner.selectionRole }, { it.week }))
        var projectionCalls = 0
        val projectionCallCounts = ProjectionCallCounts()
        val byOwner = linkedMapOf<CanonicalIncumbentOwnerWeek, CanonicalIncumbentFeasibilityEvidence>()
        keys.forEach { key ->
            val anchor = index.placement(key.owner, key.week) ?: return@forEach
            val current = currentByOwnerWeek[key].orEmpty()
            val result = when {
                current.isEmpty() -> null // A removed canonical owner is never brought back.
                current.size != 1 -> evidence(unresolved = listOf(CanonicalIncumbentUnresolvedConstraint.CURRENT_OWNER_AMBIGUOUS))
                else -> {
                    val candidate = applyPlacements(program.items, mapOf(key to (anchor.day to anchor.order)))
                    projectionCalls += 1
                    evaluateCandidate(program, program.items, candidate, snapshot, state, setOf(key.week), projectionCallCounts)
                }
            }
            if (result != null) byOwner[key] = result
        }
        val hardValid = byOwner.filterValues { it.status == CanonicalIncumbentFeasibility.HARD_VALID }.keys
        val allAnchors = hardValid.associateWith { key ->
            val anchor = requireNotNull(index.placement(key.owner, key.week))
            anchor.day to anchor.order
        }
        val combinedRows = applyPlacements(program.items, allAnchors)
        projectionCalls += if (allAnchors.isEmpty()) 0 else 1
        val combined = if (allAnchors.isEmpty()) evidence() else
            evaluateCandidate(program, program.items, combinedRows, snapshot, state,
                hardValid.mapTo(sortedSetOf()) { it.week }, projectionCallCounts)
        val conflict = allAnchors.isNotEmpty() && combined.status != CanonicalIncumbentFeasibility.HARD_VALID
        return CanonicalIncumbentLiveFeasibility(
            byOwnerWeek = byOwner,
            combinedHardValidAnchors = combined,
            combinedAnchorSetConflict = conflict,
            shadowRows = if (conflict) program.items else combinedRows,
            projectionCallCount = projectionCalls,
            dayOfiProjectionCallCount = projectionCallCounts.dayOfi,
            tissueProjectionCallCount = projectionCallCounts.tissue
        )
    }

    private fun evaluateCandidate(
        program: GeneratedProgramSkeleton,
        baselineRows: List<ProgramSkeletonItem>,
        candidateRows: List<ProgramSkeletonItem>,
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        weeks: Set<Int>,
        projectionCallCounts: ProjectionCallCounts
    ): CanonicalIncumbentFeasibilityEvidence {
        val hard = linkedSetOf<CanonicalIncumbentHardConstraint>()
        val unresolved = linkedSetOf<CanonicalIncumbentUnresolvedConstraint>()
        val details = linkedSetOf<String>()
        val baseline = program.copy(items = baselineRows)
        val candidate = program.copy(items = candidateRows)
        val baselineErrors = ProgramProjectionValidator().errors(baseline)
        val candidateErrors = ProgramProjectionValidator().errors(candidate)
        if ((candidateErrors - baselineErrors).isNotEmpty()) {
            hard += CanonicalIncumbentHardConstraint.PROGRAM_PROJECTION
            details += (candidateErrors - baselineErrors).map { "PROGRAM_PROJECTION:$it" }
        }

        weeks.sorted().forEach { week ->
            val candidateWeek = candidateRows.filter { it.weekNumber == week }
            val baselineWeek = baselineRows.filter { it.weekNumber == week }
            val allowedDays = program.weekDaySchedule[week]
            if (allowedDays == null) unresolved += CanonicalIncumbentUnresolvedConstraint.INCUMBENT_DAY_SCHEDULE_UNAVAILABLE
            else candidateWeek.filter { it.dayOfWeek !in allowedDays }.forEach { row ->
                hard += CanonicalIncumbentHardConstraint.DAY_NOT_AVAILABLE
                details += "DAY_NOT_AVAILABLE:week=$week:day=${row.dayOfWeek}"
            }
            candidateWeek.groupBy { it.dayOfWeek }.toSortedMap().forEach { (day, rows) ->
                rows.groupBy(ProgramSkeletonItem::exerciseStableKey).filterValues { it.size > 1 }.keys.sorted().forEach { key ->
                    hard += CanonicalIncumbentHardConstraint.SAME_EXERCISE_TWICE_IN_DAY
                    details += "SAME_EXERCISE_TWICE_IN_DAY:week=$week:day=$day:key=$key"
                }
                rows.groupBy(ProgramSkeletonItem::orderIndex).filterValues { it.size > 1 }.keys.sorted().forEach { order ->
                    hard += CanonicalIncumbentHardConstraint.PROGRAM_PROJECTION
                    details += "DUPLICATE_DAY_ORDER:week=$week:day=$day:order=$order"
                }
                val seconds = rows.sumOf(ProgramSkeletonItem::estimatedDurationSeconds)
                if (!placementSessionFits(0, seconds, program.request.sessionMinutes)) {
                    hard += CanonicalIncumbentHardConstraint.SESSION_TIME_CAPACITY
                    details += "SESSION_TIME_CAPACITY:week=$week:day=$day:seconds=$seconds:cap=${program.request.sessionMinutes * 60}"
                }
            }

            val dayProjection = snapshot.planDayProjection
            if (dayProjection == null) {
                unresolved += CanonicalIncumbentUnresolvedConstraint.DAY_PROJECTION_UNAVAILABLE
            } else {
                val baselineLoads = baselineWeek.groupBy { it.dayOfWeek }.toSortedMap().mapValues { (_, rows) ->
                    dayProjection.evaluate(rows.sortedBy(ProgramSkeletonItem::orderIndex))
                }
                val candidateLoads = candidateWeek.groupBy { it.dayOfWeek }.toSortedMap().mapValues { (_, rows) ->
                    dayProjection.evaluate(rows.sortedBy(ProgramSkeletonItem::orderIndex))
                }
                projectionCallCounts.dayOfi += baselineLoads.size + candidateLoads.size
                candidateLoads.forEach { (day, after) ->
                    val before = baselineLoads[day]
                    if (!after.feasible && before?.feasible != false) {
                        hard += CanonicalIncumbentHardConstraint.DAY_OFI_OR_AXIS_GATE
                        details += "DAY_OFI_OR_AXIS_GATE:week=$week:day=$day:ofi=${after.ofi}:axes=${after.axisScores}:cautions=${after.cautionReasons.sorted()}"
                    }
                }
            }

            val tissueProjection = snapshot.planWeekTissueProjection
            if (tissueProjection == null) {
                unresolved += CanonicalIncumbentUnresolvedConstraint.TISSUE_PROJECTION_UNAVAILABLE
            } else {
                projectionCallCounts.tissue += 2
                val before = tissueProjection.evaluate(baselineWeek, 8.5)
                val after = tissueProjection.evaluate(candidateWeek, 8.5)
                if (after.diagnostic != "CANONICAL_RCV_PROJECTION") {
                    unresolved += CanonicalIncumbentUnresolvedConstraint.TISSUE_PROJECTION_NOT_CANONICAL
                    details += "TISSUE_DIAGNOSTIC:${after.diagnostic}"
                } else {
                    val beforeByDay = before.days.associateBy { it.day }
                    after.days.forEach { day ->
                        val prior = beforeByDay[day.day]
                        val newBlocked = day.blockedUnits - prior?.blockedUnits.orEmpty()
                        val newUnresolved = day.unresolvedKeys - prior?.unresolvedKeys.orEmpty()
                        if (newBlocked.isNotEmpty()) {
                            hard += CanonicalIncumbentHardConstraint.TISSUE_HARD_GATE
                            details += "TISSUE_HARD_GATE:week=$week:day=${day.day}:units=${newBlocked.sorted()}"
                        }
                        if (newUnresolved.isNotEmpty()) {
                            unresolved += CanonicalIncumbentUnresolvedConstraint.TISSUE_INPUT_UNRESOLVED
                            details += "TISSUE_INPUT_UNRESOLVED:week=$week:day=${day.day}:keys=${newUnresolved.sorted()}"
                        }
                    }
                }
            }
        }

        val continuityKeys = program.personalizedDecision?.authorizedScheduling?.authorized.orEmpty()
            .filter { it.continuity }.mapTo(sortedSetOf()) { it.item.stableKey }
        val primaryKeys = PrimaryStrengthAnchorSpacingPolicy.keys(snapshot, state, continuityKeys)
        if (!PrimaryStrengthAnchorSpacingPolicy.allowedRows(candidateRows, primaryKeys)) {
            hard += CanonicalIncumbentHardConstraint.PRIMARY_STRENGTH_SPACING
            details += "PRIMARY_STRENGTH_SPACING"
        }
        val baselineFrequency = baselineRows.groupingBy { Triple(it.weekNumber, it.exerciseStableKey, it.selectionRole) }.eachCount()
        val candidateFrequency = candidateRows.groupingBy { Triple(it.weekNumber, it.exerciseStableKey, it.selectionRole) }.eachCount()
        if (baselineFrequency != candidateFrequency) hard += CanonicalIncumbentHardConstraint.OWNER_FREQUENCY_CHANGED
        val baselineMaterial = baselineRows.groupBy { Triple(it.weekNumber, it.exerciseStableKey, it.selectionRole) }
            .mapValues { (_, rows) -> rows.sortedBy(ProgramSkeletonItem::orderIndex).map { it.copy(dayOfWeek = 1, orderIndex = 1) } }
        val candidateMaterial = candidateRows.groupBy { Triple(it.weekNumber, it.exerciseStableKey, it.selectionRole) }
            .mapValues { (_, rows) -> rows.sortedBy(ProgramSkeletonItem::orderIndex).map { it.copy(dayOfWeek = 1, orderIndex = 1) } }
        if (baselineMaterial != candidateMaterial) hard += CanonicalIncumbentHardConstraint.PRESCRIPTION_CHANGED
        return evidence(hard.toList(), unresolved.toList(), details.toList())
    }

    private fun applyPlacements(
        rows: List<ProgramSkeletonItem>,
        requested: Map<CanonicalIncumbentOwnerWeek, Pair<Int, Int>>
    ): List<ProgramSkeletonItem> {
        if (requested.isEmpty()) return rows
        val relocated = rows.map { row ->
            val key = CanonicalIncumbentOwnerWeek(CanonicalOwnerIdentity(row.exerciseStableKey, row.selectionRole), row.weekNumber)
            val destination = requested[key]
            if (destination == null) row else row.copy(dayOfWeek = destination.first)
        }
        return relocated.groupBy { it.weekNumber to it.dayOfWeek }
            .toSortedMap(compareBy<Pair<Int, Int>>({ it.first }, { it.second })).values.flatMap { dayRows ->
            val week = dayRows.first().weekNumber
            val day = dayRows.first().dayOfWeek
            val ordered = dayRows.sortedWith(compareBy(ProgramSkeletonItem::orderIndex)
                .thenBy(ProgramSkeletonItem::exerciseStableKey).thenBy(ProgramSkeletonItem::selectionRole)
                .thenBy(ProgramSkeletonItem::localId))
            val fixedOrderById = ordered.mapNotNull { row ->
                val key = CanonicalIncumbentOwnerWeek(CanonicalOwnerIdentity(row.exerciseStableKey, row.selectionRole), week)
                requested[key]?.takeIf { it.first == day }?.let { row.localId to it.second }
            }.toMap()
            val duplicateFixedOrders = fixedOrderById.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (duplicateFixedOrders.isNotEmpty()) {
                ordered.map { row -> row.copy(orderIndex = fixedOrderById[row.localId] ?: row.orderIndex) }
            } else {
                val used = fixedOrderById.values.toMutableSet()
                val assigned = fixedOrderById.toMutableMap()
                ordered.filterNot { it.localId in fixedOrderById }.forEach { row ->
                    var order = row.orderIndex.takeIf { it > 0 && it !in used } ?: 1
                    while (order in used) order++
                    used += order
                    assigned[row.localId] = order
                }
                ordered.map { row -> row.copy(orderIndex = assigned.getValue(row.localId)) }
            }
        }.sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek,
            ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey, ProgramSkeletonItem::selectionRole))
    }

    private fun evidence(
        hard: List<CanonicalIncumbentHardConstraint> = emptyList(),
        unresolved: List<CanonicalIncumbentUnresolvedConstraint> = emptyList(),
        details: List<String> = emptyList()
    ) = CanonicalIncumbentFeasibilityEvidence(
        status = when {
            hard.isNotEmpty() -> CanonicalIncumbentFeasibility.HARD_INVALID
            unresolved.isNotEmpty() -> CanonicalIncumbentFeasibility.UNRESOLVED
            else -> CanonicalIncumbentFeasibility.HARD_VALID
        },
        hardReasons = hard.distinct().sortedBy(Enum<*>::ordinal),
        unresolvedReasons = unresolved.distinct().sortedBy(Enum<*>::ordinal),
        details = details.distinct().sorted()
    )
}
