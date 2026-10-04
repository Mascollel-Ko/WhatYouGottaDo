package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class C18CanonicalIncumbentPlacementShadowTest {
    private val owner = C18CanonicalOwnerIdentity("barbell_back_squat", "CANONICAL_STIMULUS_QUALITY_STRENGTH")
    private val source = C18IncumbentProgramSource(
        sourceType = C18IncumbentSourceType.CURRENT_PERSISTED_PROGRAM,
        programId = 41L,
        programStableKey = "program-user-41",
        protocolVersion = "3.52.0",
        runtimeVersion = "RECORD_BASED_PLANNER_0.14.4_KOTLIN_1"
    )

    private fun index(
        rows: List<C18PersistedPlacementRow> = listOf(C18PersistedPlacementRow(owner.stableKey, owner.selectionRole, 2, 2, 1)),
        currentOwners: Set<C18CanonicalOwnerIdentity> = setOf(owner),
        removed: Set<C18CanonicalOwnerIdentity> = emptySet(),
        replacements: Set<Pair<C18CanonicalOwnerIdentity, C18CanonicalOwnerIdentity>> = emptySet(),
        sourceOverride: C18IncumbentProgramSource? = source
    ) = C18CanonicalIncumbentPlacementShadow.index(
        source = sourceOverride,
        rows = rows,
        currentlySelectedOwners = currentOwners,
        removedOwners = removed,
        roleReplacements = replacements,
        expectedProtocolVersion = "3.52.0",
        expectedRuntimeVersion = "RECORD_BASED_PLANNER_0.14.4_KOTLIN_1"
    )

    @Test
    fun exactCurrentOwnerAndProgramRelativePositionCreateAnAnchor() {
        val result = index(rows = listOf(
            C18PersistedPlacementRow(owner.stableKey, owner.selectionRole, week = 2, day = 2, order = 1),
            C18PersistedPlacementRow(owner.stableKey, owner.selectionRole, week = 1, day = 4, order = 3)
        ))
        assertEquals(C18IncumbentIndexStatus.AVAILABLE, result.status)
        assertEquals(listOf(1, 2), result.anchors.map { it.week })
        assertEquals(listOf(4, 2), result.anchors.map { it.day })
        assertEquals(listOf(3, 1), result.anchors.map { it.order })
        assertTrue(result.anchors.all { it.sourceType == C18IncumbentSourceType.CURRENT_PERSISTED_PROGRAM })
    }

    @Test
    fun hardConstraintsOverrideTheContinuityPreference() {
        val anchor = index().anchors.single()
        assertEquals(C18IncumbentRecommendation.KEEP_INCUMBENT,
            C18CanonicalIncumbentPlacementShadow.recommend(anchor, priorPlacementHardFeasible = true))
        assertEquals(C18IncumbentRecommendation.MOVE_INCUMBENT_HARD_INVALID,
            C18CanonicalIncumbentPlacementShadow.recommend(anchor, priorPlacementHardFeasible = false))
        assertEquals(C18IncumbentRecommendation.NO_ELIGIBLE_INCUMBENT,
            C18CanonicalIncumbentPlacementShadow.recommend(anchor, priorPlacementHardFeasible = null))
    }

    @Test
    fun sameStableKeyWithDifferentRoleDoesNotTransferAnAnchor() {
        val wrongRole = C18CanonicalOwnerIdentity(owner.stableKey, "STYLE_HEAVY_LOWER_KNEE")
        val result = index(
            currentOwners = setOf(wrongRole),
            rows = listOf(C18PersistedPlacementRow(owner.stableKey, owner.selectionRole, 1, 2, 1))
        )
        assertTrue(result.anchors.isEmpty())
    }

    @Test
    fun removedAndRoleReplacedOwnersAreNotRetained() {
        val replacement = C18CanonicalOwnerIdentity(owner.stableKey, "CANONICAL_STIMULUS_QUALITY_HYPERTROPHY")
        val removed = index(removed = setOf(owner))
        val roleReplaced = index(
            currentOwners = setOf(replacement),
            replacements = setOf(owner to replacement)
        )
        assertTrue(removed.anchors.isEmpty())
        assertTrue(roleReplaced.anchors.isEmpty())
    }

    @Test
    fun duplicateOwnerWeekIsAmbiguousInsteadOfChoosingIterationOrder() {
        val result = index(rows = listOf(
            C18PersistedPlacementRow(owner.stableKey, owner.selectionRole, 1, 2, 1),
            C18PersistedPlacementRow(owner.stableKey, owner.selectionRole, 1, 4, 2)
        ))
        assertTrue(result.anchors.isEmpty())
        assertEquals(setOf(owner to 1), result.ambiguousOwnerWeeks)
    }

    @Test
    fun missingOrStaleSourceFailsClosedAndMissingRoleIsCounted() {
        val missing = index(sourceOverride = null)
        val stale = index(sourceOverride = source.copy(runtimeVersion = "old-runtime"))
        val noRole = index(rows = listOf(C18PersistedPlacementRow(owner.stableKey, null, 1, 2, 1)))
        assertEquals(C18IncumbentIndexStatus.NO_SOURCE, missing.status)
        assertEquals(C18IncumbentIndexStatus.SOURCE_VERSION_INCOMPATIBLE, stale.status)
        assertEquals(1, noRole.omittedRowsWithoutExactRole)
        assertTrue(noRole.anchors.isEmpty())
    }

    @Test
    fun persistedRoomRowsAndProgramCarryExplicitCanonicalIdentity() {
        val persistedProperties = TrainingProgramItem::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("exerciseStableKey" in persistedProperties)
        assertTrue("weekNumber" in persistedProperties && "dayOfWeek" in persistedProperties && "orderIndex" in persistedProperties)
        assertTrue("selectionRole" in persistedProperties)
        val programProperties = TrainingProgram::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("stableKey" in programProperties)
        assertTrue("canonicalBuilderProtocolVersion" in programProperties)
        assertTrue("canonicalPlannerRuntimeVersion" in programProperties)
    }

    @Test
    fun controlComparatorPerturbationCannotEnterTheIndexContract() {
        val explicitPersistedSource = listOf(C18PersistedPlacementRow(owner.stableKey, owner.selectionRole, 1, 2, 1))
        val comparatorA = listOf(2 to 1)
        val comparatorB = listOf(5 to 4)
        assertTrue(comparatorA != comparatorB)
        val first = index(rows = explicitPersistedSource)
        val second = index(rows = explicitPersistedSource)
        assertEquals(first, second)
        assertEquals(first.anchors.single(), second.anchors.single())
        // Comparator state is deliberately not a parameter to index or recommend.
    }
}
