package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL

/** Current builder contract recorded on accepted canonical programs. */
internal const val CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION = "3.54.0"

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
    val omittedInvalidRows: Int
) {
    fun placement(owner: CanonicalOwnerIdentity, week: Int): CanonicalIncumbentPlacement? {
        if (status != CanonicalIncumbentIndexStatus.AVAILABLE) return null
        val key = CanonicalIncumbentOwnerWeek(owner, week)
        if (key in ambiguousOwnerWeeks) return null
        return placements.singleOrNull { it.owner == owner && it.week == week }
    }

    companion object {
        fun unavailable(status: CanonicalIncumbentIndexStatus) = CanonicalIncumbentPlacementIndex(
            status = status,
            source = null,
            placements = emptyList(),
            ambiguousOwnerWeeks = emptySet(),
            omittedRowsWithoutExactRole = 0,
            omittedInvalidRows = 0
        )

        fun fromPersistedProgram(
            program: TrainingProgram?,
            expectedProgramId: Long?,
            rows: List<TrainingProgramItem>,
            expectedBuilderProtocolVersion: String = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
            expectedPlannerRuntimeVersion: String = PERSONALIZED_PLANNER_PROTOCOL
        ): CanonicalIncumbentPlacementIndex {
            if (expectedProgramId == null) return unavailable(CanonicalIncumbentIndexStatus.NO_EXISTING_PROGRAM)
            if (program == null || expectedProgramId <= 0 || program.id != expectedProgramId || program.stableKey.isBlank()) {
                return unavailable(CanonicalIncumbentIndexStatus.SOURCE_IDENTITY_INVALID)
            }
            val builderVersion = program.canonicalBuilderProtocolVersion
            val runtimeVersion = program.canonicalPlannerRuntimeVersion
            if (builderVersion.isNullOrBlank() || runtimeVersion.isNullOrBlank()) {
                return unavailable(CanonicalIncumbentIndexStatus.SOURCE_VERSION_UNKNOWN)
            }
            if (builderVersion != expectedBuilderProtocolVersion || runtimeVersion != expectedPlannerRuntimeVersion) {
                return unavailable(CanonicalIncumbentIndexStatus.SOURCE_VERSION_INCOMPATIBLE)
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
                }
            )
        }
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
    val recommendation: CanonicalIncumbentRecommendation
)

internal data class CanonicalIncumbentPlacementShadow(
    val indexStatus: CanonicalIncumbentIndexStatus,
    val rows: List<CanonicalIncumbentShadowRow>
)

/** Read-only shadow. It can report a recommendation only when an exact hard-feasibility result exists. */
internal object CanonicalIncumbentPlacementShadowEvaluator {
    fun evaluate(
        index: CanonicalIncumbentPlacementIndex,
        currentRows: List<ProgramSkeletonItem>,
        feasibilityByOwnerWeek: Map<CanonicalIncumbentOwnerWeek, CanonicalIncumbentFeasibility> = emptyMap()
    ): CanonicalIncumbentPlacementShadow {
        if (index.status != CanonicalIncumbentIndexStatus.AVAILABLE) {
            return CanonicalIncumbentPlacementShadow(index.status, emptyList())
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
                recommendation = recommendation
            )
        }.sortedWith(compareBy({ it.owner.stableKey }, { it.owner.selectionRole }, { it.week }))
        return CanonicalIncumbentPlacementShadow(index.status, rows)
    }
}
