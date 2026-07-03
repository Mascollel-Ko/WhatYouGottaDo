package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramPrescriptionPolicyBehaviorTest {
    private val policy = ProgramPrescriptionPolicy()

    @Test
    fun exerciseCount_usesCurrentMinuteBands() {
        val expected = mapOf(
            15 to 3,
            25 to 3,
            26 to 4,
            40 to 4,
            41 to 5,
            60 to 5,
            61 to 6,
            80 to 6,
            81 to 7
        )

        expected.forEach { (minutes, count) ->
            assertEquals(count, policy.exerciseCount(minutes))
        }
    }

    @Test
    fun warmupReserve_usesCurrentMinuteBands() {
        assertEquals(5 * 60, policy.warmupReserveSeconds(30))
        assertEquals(8 * 60, policy.warmupReserveSeconds(31))
        assertEquals(8 * 60, policy.warmupReserveSeconds(60))
        assertEquals(10 * 60, policy.warmupReserveSeconds(61))
    }

    @Test
    fun prescription_usesCurrentRoleSetsRepsAndVolumeFactors() {
        val week = week(volumeMultiplier = 1.0)
        val gate = gate(volumeFactor = 1.0)

        val anchor = policy.prescribe(candidate(), ProgramExerciseRole.ANCHOR, week, gate)
        assertEquals(4, anchor.setCount)
        assertEquals(5, anchor.reps)

        val prehab = policy.prescribe(candidate(), ProgramExerciseRole.PREHAB, week, gate)
        assertEquals(2, prehab.setCount)
        assertEquals(15, prehab.reps)

        val core = policy.prescribe(candidate(), ProgramExerciseRole.CORE, week, gate)
        assertEquals(3, core.setCount)
        assertEquals(10, core.reps)

        val highImpactTransfer = policy.prescribe(highImpactCandidate(), ProgramExerciseRole.TRANSFER, week, gate)
        assertEquals(3, highImpactTransfer.setCount)
        assertEquals(5, highImpactTransfer.reps)

        val reduced = policy.prescribe(
            candidate(),
            ProgramExerciseRole.ANCHOR,
            week(volumeMultiplier = 0.50),
            gate(volumeFactor = 0.25)
        )
        assertEquals(1, reduced.setCount)
    }

    @Test
    fun prescription_timedExerciseUsesCurrentSecondsAndLabelFormat() {
        val week = week()
        val gate = gate()

        val sportLike = policy.prescribe(
            timedCandidate(progressMetricType = "SESSION_DURATION", activityKind = "SPORT_SESSION"),
            ProgramExerciseRole.SUPPORT,
            week,
            gate
        )
        assertEquals(0, sportLike.reps)
        assertEquals(15 * 60, sportLike.seconds)
        assertEquals("3세트 · 900초", sportLike.label)

        val transfer = policy.prescribe(
            timedCandidate(progressMetricType = "REPS_OR_TIME"),
            ProgramExerciseRole.TRANSFER,
            week,
            gate
        )
        assertEquals(20, transfer.seconds)
        assertEquals("4세트 · 20초", transfer.label)

        val defaultTimed = policy.prescribe(
            timedCandidate(progressMetricType = "QUALITY_BASED"),
            ProgramExerciseRole.ACCESSORY,
            week,
            gate
        )
        assertEquals(30, defaultTimed.seconds)
        assertEquals("3세트 · 30초", defaultTimed.label)
    }

    @Test
    fun prescription_capsRpeByFatigueGate() {
        val prescription = policy.prescribe(
            candidate(),
            ProgramExerciseRole.ANCHOR,
            week(targetRpeMax = 8.6),
            gate(rpeCap = 7)
        )

        assertEquals(7, prescription.rpe)
    }

    @Test
    fun durationEstimate_usesCurrentWorkSetupAndRestRules() {
        val repCandidate = candidate(defaultRestSeconds = 120)
        val repPrescription = policy.prescribe(repCandidate, ProgramExerciseRole.ANCHOR, week(), gate())
        assertEquals(45 + 4 * (5 * 4) + 3 * 120, policy.estimateItemDurationSeconds(repCandidate, repPrescription))

        val timedCandidate = timedCandidate(progressMetricType = "REPS_OR_TIME", defaultRestSeconds = 30)
        val timedPrescription = policy.prescribe(timedCandidate, ProgramExerciseRole.TRANSFER, week(), gate())
        assertEquals(45 + 4 * 20 + 3 * 30, policy.estimateItemDurationSeconds(timedCandidate, timedPrescription))
    }

    @Test
    fun fitRequiredPrescription_reducesSetsToCurrentLargestFittingCount() {
        val candidate = candidate(defaultRestSeconds = 120)
        val prescription = policy.prescribe(candidate, ProgramExerciseRole.ANCHOR, week(), gate())

        val reduced = policy.fitRequiredPrescription(candidate, prescription, remainingSeconds = 300)
        assertEquals(2, reduced.setCount)
        assertEquals(5, reduced.reps)
        assertEquals(8, reduced.rpe)

        val minimum = policy.fitRequiredPrescription(candidate, prescription, remainingSeconds = 40)
        assertEquals(1, minimum.setCount)
        assertEquals("4×5", minimum.label)
    }

    private fun candidate(defaultRestSeconds: Int = 90): ProgramCandidate {
        val exercise = Exercise(
            id = 1,
            name = "Test lift",
            category = "Strength",
            defaultRestSeconds = defaultRestSeconds,
            stableKey = "test_lift"
        )
        return ProgramCandidate(
            exercise = exercise,
            metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise),
            canonical = true
        )
    }

    private fun highImpactCandidate(): ProgramCandidate {
        val exercise = Exercise(
            id = 2,
            name = "Test jump",
            category = "Power",
            stableKey = "test_jump"
        )
        return ProgramCandidate(
            exercise = exercise,
            metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
                jointImpactStressTags = MetadataTokenField.parse("PLYOMETRIC")
            ),
            canonical = true
        )
    }

    private fun timedCandidate(
        progressMetricType: String,
        activityKind: String = "EXERCISE",
        defaultRestSeconds: Int = 60
    ): ProgramCandidate {
        val exercise = Exercise(
            id = 3,
            name = "Timed test",
            category = "Timed",
            defaultRestSeconds = defaultRestSeconds,
            stableKey = "timed_test"
        )
        return ProgramCandidate(
            exercise = exercise,
            metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
                progressMetricType = progressMetricType,
                activityKind = activityKind
            ),
            canonical = true
        )
    }

    private fun week(
        volumeMultiplier: Double = 1.0,
        targetRpeMax: Double = 8.0
    ): ProgramWeekPlan =
        ProgramWeekPlan(
            weekIndex = 1,
            weekType = ProgramWeekType.BUILD.name,
            volumeMultiplier = volumeMultiplier,
            intensityMultiplier = 1.0,
            heavyExposureLimit = 2,
            lowerBodyFatigueLimit = 8.0,
            axialLoadLimit = 2,
            plyometricLimit = 1,
            deloadFlag = false,
            targetRpeMin = 6.0,
            targetRpeMax = targetRpeMax
        )

    private fun gate(
        volumeFactor: Double = 1.0,
        rpeCap: Int = 9
    ): ProgramFatigueGate =
        ProgramFatigueGate(
            band = ProgramFatigueBand.GREEN,
            volumeFactor = volumeFactor,
            rpeCap = rpeCap,
            allowsHeavyLower = true,
            allowsHighImpact = true,
            allowsHighIntensityCod = true,
            lowerBodyRestricted = false
        )
}
