package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueLabelResolver
import com.training.trackplanner.analysis.readiness.TrainingGateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FatigueSlotPolicyTest {
    private val policy = FatigueSlotPolicy.DEFAULT

    @Test
    fun nullTrainingGateLeavesTodayItemUnchanged() {
        val item = item(setCount = 4)
        val adjusted = policy.adjustTodayItem(
            item = item,
            candidate = candidate(ProgramSlotId.LOWER_SQUAT_PATTERN),
            gate = policy.gate(null as TrainingGateSnapshot?)
        )

        assertEquals(item, adjusted)
    }

    @Test
    fun volumeFactorBelowOneDoesNotIncreaseTodayVolume() {
        val item = item(setCount = 3)
        val adjusted = policy.adjustTodayItem(
            item = item,
            candidate = candidate(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY),
            gate = gate(volumeFactor = 0.5)
        )

        assertNotNull(adjusted)
        assertTrue(adjusted!!.setCount <= item.setCount)
        assertEquals(2, adjusted.setCount)
    }

    @Test
    fun heavyLowerRestrictionDowngradesMatchingLowerAnchor() {
        val adjusted = policy.adjustTodayItem(
            item = item(setCount = 4),
            candidate = candidate(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN),
            gate = gate(heavyLowerRestricted = true)
        )

        assertNotNull(adjusted)
        assertEquals(1, adjusted!!.setCount)
    }

    @Test
    fun highImpactRestrictionAvoidsMatchingImpactSlot() {
        val adjusted = policy.adjustTodayItem(
            item = item(setCount = 3),
            candidate = candidate(ProgramSlotId.POWER_REACTIVE_LOW_VOLUME),
            gate = gate(highImpactRestricted = true)
        )

        assertNull(adjusted)
    }

    @Test
    fun codReactiveRestrictionAvoidsMatchingReactiveCodSlot() {
        val adjusted = policy.adjustTodayItem(
            item = item(setCount = 3),
            candidate = candidate(ProgramSlotId.BADMINTON_FOOTWORK_REACTION),
            gate = gate(codReactiveRestricted = true)
        )

        assertNull(adjusted)
    }

    @Test
    fun overheadRestrictionAvoidsMatchingOverheadSlot() {
        val adjusted = policy.adjustTodayItem(
            item = item(setCount = 3),
            candidate = candidate(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT),
            gate = gate(overheadRestricted = true)
        )

        assertNull(adjusted)
    }

    @Test
    fun upperPushRestrictionDowngradesMatchingUpperPushSlotAndLeavesUnrelatedSlotsAlone() {
        val item = item(setCount = 4)
        val gate = gate(upperPushRestricted = true)

        val upperPush = policy.adjustTodayItem(
            item = item,
            candidate = candidate(ProgramSlotId.UPPER_PUSH_SUPPORT),
            gate = gate
        )
        val unrelated = policy.adjustTodayItem(
            item = item,
            candidate = candidate(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY),
            gate = gate
        )

        assertNotNull(upperPush)
        assertEquals(1, upperPush!!.setCount)
        assertEquals(item, unrelated)
    }

    @Test
    fun gripForearmRestrictionDowngradesMatchingGripSlot() {
        val item = item(setCount = 4)
        val adjusted = policy.adjustTodayItem(
            item = item,
            candidate = candidate(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT),
            gate = gate(gripForearmRestricted = true)
        )

        assertNotNull(adjusted)
        assertTrue(adjusted!!.setCount < item.setCount)
        assertEquals(1, adjusted.setCount)
    }

    @Test
    fun combinedRestrictionsRemainDeterministic() {
        val item = item(setCount = 5)
        val candidate = candidate(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT)
        val gate = gate(
            highImpactRestricted = true,
            gripForearmRestricted = true,
            volumeFactor = 0.5
        )

        val first = policy.adjustTodayItem(item, candidate, gate)
        val second = policy.adjustTodayItem(item, candidate, gate)

        assertEquals(first, second)
    }

    @Test
    fun unrelatedSlotsRemainAvailable() {
        val item = item(setCount = 4)
        val adjusted = policy.adjustTodayItem(
            item = item,
            candidate = candidate(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY),
            gate = gate(overheadRestricted = true)
        )

        assertEquals(item, adjusted)
    }

    @Test
    fun adjustmentDoesNotMutateStoredProgramItem() {
        val item = item(setCount = 4)

        policy.adjustTodayItem(
            item = item,
            candidate = candidate(ProgramSlotId.LOWER_SQUAT_PATTERN),
            gate = gate(heavyLowerRestricted = true)
        )

        assertEquals(4, item.setCount)
    }

    @Test
    fun resolvedDateBoundaryAppliesGateOnlyToProgramItemsBeingInsertedForToday() {
        val item = item(setCount = 4)
        val today = "2026-06-26"
        val future = "2026-06-27"
        val gate = gate(heavyLowerRestricted = true)
        val candidate = candidate(ProgramSlotId.LOWER_SQUAT_PATTERN)

        // This boundary handles TrainingProgramItem rows before insertion only;
        // existing WorkoutEntry/WorkoutSet rows are not retroactively rewritten here.
        val todayAdjusted = policy.adjustItemForResolvedDate(item, today, today, candidate, gate)
        val futureUnchanged = policy.adjustItemForResolvedDate(item, future, today, candidate, gate)

        assertNotNull(todayAdjusted)
        assertEquals(1, todayAdjusted!!.setCount)
        assertEquals(item, futureUnchanged)
        assertEquals(4, item.setCount)
    }

    @Test
    fun resolvedDateBoundaryPreservesLegacyInsertionWhenGateIsNull() {
        val item = item(setCount = 4)

        val adjusted = policy.adjustItemForResolvedDate(
            item = item,
            itemDate = "2026-06-26",
            todayDate = "2026-06-26",
            candidate = candidate(ProgramSlotId.LOWER_SQUAT_PATTERN),
            gate = null
        )

        assertEquals(item, adjusted)
    }

    @Test
    fun resolvedDateBoundarySkipsAvoidedTodayItemButLeavesFutureItemUnchanged() {
        val item = item(setCount = 3)
        val today = "2026-06-26"
        val future = "2026-06-27"
        val gate = gate(highImpactRestricted = true)
        val candidate = candidate(ProgramSlotId.POWER_REACTIVE_LOW_VOLUME)

        val skippedToday = policy.adjustItemForResolvedDate(item, today, today, candidate, gate)
        val futureUnchanged = policy.adjustItemForResolvedDate(item, future, today, candidate, gate)

        assertNull(skippedToday)
        assertEquals(item, futureUnchanged)
    }

    @Test
    fun trainingGateSnapshotIsNotAProgramBuilderInputForMultiWeekGeneration() {
        val buildMethods = ProgramBuilder::class.java.declaredMethods.filter { method ->
            method.name == "build"
        }

        assertTrue(
            buildMethods.none { method ->
                method.parameterTypes.any { parameter -> parameter == TrainingGateSnapshot::class.java }
            }
        )
    }

    @Test
    fun trainingProgramItemHasNoPlannedTargetRpeFieldToCap() {
        val fieldNames = TrainingProgramItem::class.java.declaredFields.map { field -> field.name }

        assertTrue("target RPE is not a planned program-item field yet", "targetRpe" !in fieldNames)
        assertTrue("planned RPE is not a planned program-item field yet", "plannedRpe" !in fieldNames)
    }

    @Test
    fun dailyFatigueStateUsesRelaxedProgramBands() {
        assertEquals(ProgramFatigueBand.GREEN, policy.gate(fatigueState(51)).band)
        assertEquals(ProgramFatigueBand.YELLOW, policy.gate(fatigueState(52)).band)
        assertEquals(ProgramFatigueBand.YELLOW, policy.gate(fatigueState(68)).band)
        assertEquals(ProgramFatigueBand.ORANGE, policy.gate(fatigueState(69)).band)
        assertEquals(ProgramFatigueBand.ORANGE, policy.gate(fatigueState(86)).band)
        assertEquals(ProgramFatigueBand.RED, policy.gate(fatigueState(87)).band)
    }

    @Test
    fun axisRestrictionsUseRelaxedCutoffs() {
        val jointBelow = policy.gate(fatigueState(ofi = 0, joint = 74))
        val jointAt = policy.gate(fatigueState(ofi = 0, joint = 75))
        val neuralBelow = policy.gate(fatigueState(ofi = 0, neural = 80))
        val localBelow = policy.gate(fatigueState(ofi = 0, local = 80))

        assertTrue(jointBelow.allowsHighImpact)
        assertFalse(jointAt.allowsHighImpact)
        assertEquals(9, neuralBelow.rpeCap)
        assertTrue(localBelow.allowsHeavyLower)
    }

    private fun gate(
        heavyLowerRestricted: Boolean = false,
        highImpactRestricted: Boolean = false,
        codReactiveRestricted: Boolean = false,
        upperPushRestricted: Boolean = false,
        overheadRestricted: Boolean = false,
        gripForearmRestricted: Boolean = false,
        volumeFactor: Double = 1.0,
        rpeCap: Int? = null
    ): ProgramFatigueGate = policy.gate(
        TrainingGateSnapshot(
            overallScore = 72,
            heavyLowerRestricted = heavyLowerRestricted,
            highImpactRestricted = highImpactRestricted,
            codReactiveRestricted = codReactiveRestricted,
            upperPushRestricted = upperPushRestricted,
            overheadRestricted = overheadRestricted,
            gripForearmRestricted = gripForearmRestricted,
            volumeFactor = volumeFactor,
            rpeCap = rpeCap,
            reasons = emptyList()
        )
    )

    private fun item(setCount: Int): TrainingProgramItem =
        TrainingProgramItem(
            programId = 1,
            weekNumber = 1,
            dayOfWeek = 1,
            orderIndex = 1,
            exerciseId = 1,
            exerciseName = "Fixture movement",
            category = "strength",
            setCount = setCount,
            reps = 8
        )

    private fun candidate(vararg slots: ProgramSlotId): ProgramCandidate =
        ProgramCandidate(
            exercise = Exercise(
                id = 1,
                name = slots.joinToString(prefix = "fixture "),
                category = "strength",
                stableKey = "fatigue_slot_fixture_${slots.joinToString("_")}"
            ),
            metadata = null,
            canonical = true,
            slotCapabilities = SlotCapabilityProfile(
                primary = slots.take(1).toSet(),
                secondary = slots.drop(1).toSet(),
                weakMatches = emptySet(),
                source = SlotCapabilitySource.RUNTIME_METADATA,
                confidence = SlotCapabilityConfidence.HIGH
            )
        )

    private fun fatigueState(
        ofi: Int,
        neural: Int = ofi,
        local: Int = ofi,
        joint: Int = ofi
    ): DailyFatigueState =
        DailyFatigueState(
            date = LocalDate.of(2026, 7, 5),
            neuromuscularFatigue = 0.0,
            systemicMuscularFatigue = 0.0,
            localMuscularFatigue = 0.0,
            jointTendonImpactFatigue = 0.0,
            movementFocusFatigue = 0.0,
            recoveryPressure = 0.0,
            neuromuscularScore = neural,
            systemicMuscularScore = ofi,
            localMuscularScore = local,
            jointTendonImpactScore = joint,
            movementFocusScore = ofi,
            recoveryPressureScore = ofi,
            overallFatigueIndex = ofi,
            readinessLabel = FatigueLabelResolver.label(ofi),
            cautionReasons = emptyList(),
            confidence = FatigueConfidence.MEDIUM
        )
}
