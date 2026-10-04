package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalIncumbentPlacementIndexTest {
    private val owner = CanonicalOwnerIdentity("barbell_bench_press", "CANONICAL_STIMULUS_QUALITY_STRENGTH")
    private val lineage = "user_program_lineage-a"

    private fun program(
        id: Long = 9,
        stableKey: String = lineage,
        builder: String? = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
        runtime: String? = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
    ) = TrainingProgram(
        id = id,
        stableKey = stableKey,
        name = "accepted plan",
        durationDays = 14,
        canonicalBuilderProtocolVersion = builder,
        canonicalPlannerRuntimeVersion = runtime
    )

    private fun item(
        id: Long = 1,
        programId: Long = 9,
        stableKey: String = owner.stableKey,
        role: String? = owner.selectionRole,
        week: Int = 1,
        day: Int = 2,
        order: Int = 1
    ) = TrainingProgramItem(
        id = id,
        programId = programId,
        weekNumber = week,
        dayOfWeek = day,
        orderIndex = order,
        exerciseStableKey = stableKey,
        exerciseName = "Bench press",
        category = "STRENGTH",
        selectionRole = role
    )

    private fun skeletonItem(
        stableKey: String = owner.stableKey,
        role: String = owner.selectionRole,
        week: Int = 1,
        day: Int = 4,
        order: Int = 1
    ) = ProgramSkeletonItem(
        localId = "row-$stableKey-$role-$week",
        weekNumber = week,
        dayOfWeek = day,
        orderIndex = order,
        exerciseStableKey = stableKey,
        exerciseName = "Bench press",
        category = "STRENGTH",
        restSeconds = 120,
        prescription = "2 x 6",
        setCount = 2,
        reps = 6,
        weightKg = 0.0,
        seconds = 0,
        selectionReason = "test",
        weightSource = "TEST",
        selectionRole = role
    )

    private fun index(
        program: TrainingProgram? = program(),
        expectedId: Long? = 9,
        rows: List<TrainingProgramItem> = listOf(item())
    ) = CanonicalIncumbentPlacementIndex.fromPersistedProgram(program, expectedId, rows)

    @Test
    fun exactLineageOwnerRoleAndProgramWeekProduceDeterministicPlacement() {
        val reversed = listOf(item(id = 3, week = 2, day = 4, order = 2), item(id = 1, week = 1, day = 2))
        val result = index(rows = reversed)
        assertEquals(CanonicalIncumbentIndexStatus.AVAILABLE, result.status)
        assertEquals(CanonicalProgramLineageId(lineage), result.source?.lineageId)
        assertEquals(CanonicalIncumbentSourceType.CURRENT_PERSISTED_PROGRAM, result.source?.sourceType)
        assertEquals(listOf(1, 2), result.placements.map { it.week })
        assertEquals(2, result.placement(owner, 1)?.day)
        assertEquals(4, result.placement(owner, 2)?.day)
        assertEquals(result, index(rows = reversed.reversed()))
    }

    @Test
    fun lineageIsProgramStableKeyAndNotDatabaseRowId() {
        val original = program(id = 9)
        val restoredDatabaseRow = original.copy(id = 700)
        val restored = CanonicalIncumbentPlacementIndex.fromPersistedProgram(
            restoredDatabaseRow, 700, listOf(item(programId = 700))
        )
        assertEquals(CanonicalIncumbentIndexStatus.AVAILABLE, restored.status)
        assertEquals(CanonicalProgramLineageId(lineage), restored.source?.lineageId)
        assertEquals(9L, original.id)
        assertEquals(700L, restored.source?.programId)
    }

    @Test
    fun missingLegacyRoleAndSameKeyWrongRoleFailClosed() {
        val missing = index(rows = listOf(item(role = null)))
        assertEquals(CanonicalIncumbentIndexStatus.AVAILABLE, missing.status)
        assertEquals(1, missing.omittedRowsWithoutExactRole)
        assertTrue(missing.placements.isEmpty())

        val wrongRoleIndex = index(rows = listOf(item(role = "COVERAGE_HORIZONTAL_PUSH")))
        val shadow = CanonicalIncumbentPlacementShadowEvaluator.evaluate(
            wrongRoleIndex,
            listOf(skeletonItem(role = "CANONICAL_STIMULUS_QUALITY_STRENGTH")),
            mapOf(CanonicalIncumbentOwnerWeek(owner, 1) to CanonicalIncumbentFeasibility.HARD_VALID)
        )
        assertTrue(shadow.rows.isEmpty())
    }

    @Test
    fun duplicateExactOwnerWeekIsAmbiguousAndNeverChoosesIterationOrder() {
        val result = index(rows = listOf(item(day = 2, order = 1), item(id = 2, day = 4, order = 2)))
        assertEquals(setOf(CanonicalIncumbentOwnerWeek(owner, 1)), result.ambiguousOwnerWeeks)
        assertNull(result.placement(owner, 1))
        assertTrue(result.placements.isEmpty())
    }

    @Test
    fun missingStaleOrMismatchedProgramSourcesAreUnavailable() {
        assertEquals(CanonicalIncumbentIndexStatus.NO_EXISTING_PROGRAM, index(expectedId = null).status)
        assertEquals(CanonicalIncumbentIndexStatus.SOURCE_IDENTITY_INVALID, index(expectedId = 10).status)
        assertEquals(CanonicalIncumbentIndexStatus.SOURCE_VERSION_UNKNOWN, index(program = program(builder = null)).status)
        assertEquals(
            CanonicalIncumbentIndexStatus.SOURCE_VERSION_INCOMPATIBLE,
            index(program = program(builder = "3.53.0")).status
        )
        val wrongProgramRow = index(rows = listOf(item(programId = 10)))
        assertEquals(CanonicalIncumbentIndexStatus.AVAILABLE, wrongProgramRow.status)
        assertEquals(1, wrongProgramRow.omittedInvalidRows)
        assertTrue(wrongProgramRow.placements.isEmpty())
    }

    @Test
    fun shadowOnlyUsesExactCurrentOwnerAndExplicitHardFeasibility() {
        val result = CanonicalIncumbentPlacementShadowEvaluator.evaluate(
            index(),
            listOf(skeletonItem(day = 4)),
            mapOf(CanonicalIncumbentOwnerWeek(owner, 1) to CanonicalIncumbentFeasibility.HARD_VALID)
        )
        assertEquals(1, result.rows.size)
        assertEquals(CanonicalIncumbentRecommendation.PRESERVE_INCUMBENT, result.rows.single().recommendation)
        assertEquals(2, result.rows.single().incumbentDay)
        assertEquals(4, result.rows.single().productionDay)

        val unresolved = CanonicalIncumbentPlacementShadowEvaluator.evaluate(index(), listOf(skeletonItem()))
        assertEquals(CanonicalIncumbentRecommendation.NO_DECISION_UNRESOLVED, unresolved.rows.single().recommendation)
        val removed = CanonicalIncumbentPlacementShadowEvaluator.evaluate(index(), emptyList())
        assertTrue(removed.rows.isEmpty())
    }
}
