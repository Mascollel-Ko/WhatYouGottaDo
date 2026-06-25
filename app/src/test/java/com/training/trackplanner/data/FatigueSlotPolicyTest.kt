package com.training.trackplanner.data

import com.training.trackplanner.analysis.readiness.TrainingGateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
