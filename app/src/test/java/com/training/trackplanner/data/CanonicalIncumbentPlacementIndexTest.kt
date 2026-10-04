package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalIncumbentPlacementIndexTest {
    private val owner = CanonicalOwnerIdentity("barbell_bench_press", "CANONICAL_STIMULUS_QUALITY_STRENGTH")
    private val lineage = "user_program_lineage-a"
    private val snapshotToken = CanonicalIncumbentSourceSnapshotToken("a".repeat(64))

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
        rows: List<TrainingProgramItem> = listOf(item()),
        token: CanonicalIncumbentSourceSnapshotToken? = snapshotToken
    ) = CanonicalIncumbentPlacementIndex.fromPersistedProgram(program, expectedId, rows, sourceSnapshotToken = token)

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
    fun c19ExactRoleProgramContractRemainsAnEligibleIncumbentSourceAfterC20Bump() {
        val c19 = program(
            builder = "3.54.0",
            runtime = "RECORD_BASED_PLANNER_0.14.6_KOTLIN_1"
        )
        val result = index(program = c19)
        assertEquals(CanonicalIncumbentIndexStatus.AVAILABLE, result.status)
        assertEquals(2, result.placement(owner, 1)?.day)

        val mismatched = index(program = c19.copy(
            canonicalPlannerRuntimeVersion = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
        ))
        assertEquals(CanonicalIncumbentIndexStatus.SOURCE_VERSION_INCOMPATIBLE, mismatched.status)
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

    @Test
    fun productionActivatorAppliesOnlyCombinedHardValidExactAnchors() {
        val persisted = index()
        val current = skeletonItem(day = 4, order = 2)
        val program = GeneratedProgramSkeleton(
            suggestedName = "fixture",
            durationDays = 14,
            request = ProgramSkeletonRequest(
                name = "fixture", goal = ProgramGoal.STRENGTH, weeklyTrainingDays = 3,
                sessionMinutes = 60, availableEquipment = emptySet(), excludedExerciseText = "",
                badmintonTransferRatio = 0.0, sportStrengthRatio = "AUTO",
                periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = 2
            ),
            periodizationType = ProgramPeriodizationType.STEP_DELOAD,
            weekPlans = listOf(ProgramWeekPlan(1, "BUILD", 1.0, 1.0, 2, 8.0, 2, 1, false)),
            items = listOf(current),
            weekDaySchedule = mapOf(1 to setOf(2, 4))
        )
        val key = CanonicalIncumbentOwnerWeek(owner, 1)
        val valid = CanonicalIncumbentFeasibilityEvidence(
            CanonicalIncumbentFeasibility.HARD_VALID, emptyList(), emptyList()
        )
        val live = CanonicalIncumbentLiveFeasibility(
            byOwnerWeek = mapOf(key to valid),
            combinedHardValidAnchors = valid,
            combinedAnchorSetConflict = false,
            shadowRows = listOf(current.copy(dayOfWeek = 2, orderIndex = 1)),
            projectionCallCount = 2
        )
        val activated = CanonicalIncumbentPlacementActivator.activate(persisted, program, live)
        assertEquals(CanonicalIncumbentActivationStatus.ACTIVATED, activated.status)
        assertEquals(2, activated.program.items.single().dayOfWeek)
        assertEquals(1, activated.program.items.single().orderIndex)
        assertEquals(1, activated.preservations.size)
        assertEquals(CanonicalIncumbentFeasibility.HARD_VALID, activated.preservations.single().feasibility.status)
        assertEquals(snapshotToken, activated.preservations.single().sourceSnapshotToken)

        val unsafe = live.copy(
            byOwnerWeek = mapOf(key to valid.copy(
                status = CanonicalIncumbentFeasibility.HARD_INVALID,
                hardReasons = listOf(CanonicalIncumbentHardConstraint.DAY_NOT_AVAILABLE)
            )),
            shadowRows = listOf(current)
        )
        val released = CanonicalIncumbentPlacementActivator.activate(persisted, program, unsafe)
        assertEquals(CanonicalIncumbentActivationStatus.NO_ELIGIBLE_HARD_VALID_ANCHORS, released.status)
        assertSame(program, released.program)
        assertTrue(released.preservations.isEmpty())
    }

    @Test
    fun productionActivatorAllowsOnlyExactOrderSlotConflictAroundHardValidAnchor() {
        val persisted = index()
        val current = skeletonItem(day = 4, order = 1)
        val collidingNewOwner = skeletonItem(
            stableKey = "ex_new_strength_owner", role = "CANONICAL_STIMULUS_QUALITY_STRENGTH",
            day = 2, order = 1
        )
        val request = ProgramSkeletonRequest(
            name = "fixture", goal = ProgramGoal.STRENGTH, weeklyTrainingDays = 3,
            sessionMinutes = 60, availableEquipment = emptySet(), excludedExerciseText = "",
            badmintonTransferRatio = 0.0, sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = 2
        )
        val program = GeneratedProgramSkeleton(
            suggestedName = "fixture", durationDays = 14, request = request,
            periodizationType = ProgramPeriodizationType.STEP_DELOAD,
            weekPlans = listOf(ProgramWeekPlan(1, "BUILD", 1.0, 1.0, 2, 8.0, 2, 1, false)),
            items = listOf(current, collidingNewOwner), weekDaySchedule = mapOf(1 to setOf(2, 4))
        )
        val key = CanonicalIncumbentOwnerWeek(owner, 1)
        val valid = CanonicalIncumbentFeasibilityEvidence(
            CanonicalIncumbentFeasibility.HARD_VALID, emptyList(), emptyList()
        )
        val live = CanonicalIncumbentLiveFeasibility(
            byOwnerWeek = mapOf(key to valid), combinedHardValidAnchors = valid,
            combinedAnchorSetConflict = false,
            shadowRows = listOf(current.copy(dayOfWeek = 2, orderIndex = 1), collidingNewOwner.copy(orderIndex = 2)),
            projectionCallCount = 2
        )

        val result = CanonicalIncumbentPlacementActivator.activate(persisted, program, live)

        assertEquals(CanonicalIncumbentActivationStatus.ACTIVATED, result.status)
        assertEquals(2, result.program.items.single { it.localId == current.localId }.dayOfWeek)
        assertEquals(1, result.program.items.single { it.localId == current.localId }.orderIndex)
        assertEquals(2, result.program.items.single { it.localId == collidingNewOwner.localId }.orderIndex)
        assertEquals(listOf(owner), result.preservations.map { it.owner })

        val repeated = CanonicalIncumbentPlacementActivator.activate(
            persisted,
            result.program,
            live.copy(shadowRows = result.program.items)
        )
        assertEquals(CanonicalIncumbentActivationStatus.ACTIVATED, repeated.status)
        assertSame(result.program, repeated.program)
        assertTrue(repeated.preservations.isEmpty())
    }

    @Test
    fun productionActivatorFailsClosedOnMissingTokenOrCollateralOrderMutation() {
        val noToken = index(token = null)
        val current = skeletonItem(day = 4, order = 2)
        val program = GeneratedProgramSkeleton(
            suggestedName = "fixture", durationDays = 14,
            request = ProgramSkeletonRequest(
                name = "fixture", goal = ProgramGoal.STRENGTH, weeklyTrainingDays = 3,
                sessionMinutes = 60, availableEquipment = emptySet(), excludedExerciseText = "",
                badmintonTransferRatio = 0.0, sportStrengthRatio = "AUTO",
                periodizationType = ProgramPeriodizationType.AUTO, durationWeeks = 2
            ),
            periodizationType = ProgramPeriodizationType.STEP_DELOAD,
            weekPlans = listOf(ProgramWeekPlan(1, "BUILD", 1.0, 1.0, 2, 8.0, 2, 1, false)),
            items = listOf(current), weekDaySchedule = mapOf(1 to setOf(2, 4))
        )
        val key = CanonicalIncumbentOwnerWeek(owner, 1)
        val valid = CanonicalIncumbentFeasibilityEvidence(CanonicalIncumbentFeasibility.HARD_VALID, emptyList(), emptyList())
        val live = CanonicalIncumbentLiveFeasibility(
            byOwnerWeek = mapOf(key to valid), combinedHardValidAnchors = valid,
            combinedAnchorSetConflict = false, shadowRows = listOf(current.copy(dayOfWeek = 2)), projectionCallCount = 1
        )
        val missing = CanonicalIncumbentPlacementActivator.activate(noToken, program, live)
        assertEquals(CanonicalIncumbentActivationStatus.SOURCE_SNAPSHOT_MISSING, missing.status)
        assertSame(program, missing.program)

        val other = current.copy(localId = "other", exerciseStableKey = "ex_other", selectionRole = "OTHER")
        val withCollateralMove = live.copy(shadowRows = listOf(current.copy(dayOfWeek = 2), other.copy(orderIndex = 3)))
        val rejected = CanonicalIncumbentPlacementActivator.activate(
            index(rows = listOf(item(), item(id = 2, stableKey = "ex_other", role = "OTHER", order = 2))),
            program.copy(items = listOf(current, other)),
            withCollateralMove
        )
        assertEquals(CanonicalIncumbentActivationStatus.NON_ANCHOR_PLACEMENT_WOULD_CHANGE, rejected.status)
        assertSame(program.items.first(), rejected.program.items.first())
    }
}
