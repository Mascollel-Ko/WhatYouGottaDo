package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramPrescriptionPolicyBehaviorTest {
    private val builder = ProgramBuilder()

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
            assertEquals(count, invokeExerciseCount(minutes))
        }
    }

    @Test
    fun warmupReserve_usesCurrentMinuteBands() {
        assertEquals(5 * 60, invokeWarmupReserveSeconds(30))
        assertEquals(8 * 60, invokeWarmupReserveSeconds(31))
        assertEquals(8 * 60, invokeWarmupReserveSeconds(60))
        assertEquals(10 * 60, invokeWarmupReserveSeconds(61))
    }

    @Test
    fun prescription_usesCurrentRoleSetsRepsAndVolumeFactors() {
        val week = week(volumeMultiplier = 1.0)
        val gate = gate(volumeFactor = 1.0)

        val anchor = invokePrescribe(candidate(), ProgramExerciseRole.ANCHOR, week, gate)
        assertEquals(4, anchor.intField("setCount"))
        assertEquals(5, anchor.intField("reps"))

        val prehab = invokePrescribe(candidate(), ProgramExerciseRole.PREHAB, week, gate)
        assertEquals(2, prehab.intField("setCount"))
        assertEquals(15, prehab.intField("reps"))

        val core = invokePrescribe(candidate(), ProgramExerciseRole.CORE, week, gate)
        assertEquals(3, core.intField("setCount"))
        assertEquals(10, core.intField("reps"))

        val highImpactTransfer = invokePrescribe(highImpactCandidate(), ProgramExerciseRole.TRANSFER, week, gate)
        assertEquals(3, highImpactTransfer.intField("setCount"))
        assertEquals(5, highImpactTransfer.intField("reps"))

        val reduced = invokePrescribe(
            candidate(),
            ProgramExerciseRole.ANCHOR,
            week(volumeMultiplier = 0.50),
            gate(volumeFactor = 0.25)
        )
        assertEquals(1, reduced.intField("setCount"))
    }

    @Test
    fun prescription_timedExerciseUsesCurrentSecondsAndLabelFormat() {
        val week = week()
        val gate = gate()

        val sportLike = invokePrescribe(
            timedCandidate(progressMetricType = "SESSION_DURATION", activityKind = "SPORT_SESSION"),
            ProgramExerciseRole.SUPPORT,
            week,
            gate
        )
        assertEquals(0, sportLike.intField("reps"))
        assertEquals(15 * 60, sportLike.intField("seconds"))
        assertEquals("3세트 · 900초", sportLike.stringField("label"))

        val transfer = invokePrescribe(
            timedCandidate(progressMetricType = "REPS_OR_TIME"),
            ProgramExerciseRole.TRANSFER,
            week,
            gate
        )
        assertEquals(20, transfer.intField("seconds"))
        assertEquals("4세트 · 20초", transfer.stringField("label"))

        val defaultTimed = invokePrescribe(
            timedCandidate(progressMetricType = "QUALITY_BASED"),
            ProgramExerciseRole.ACCESSORY,
            week,
            gate
        )
        assertEquals(30, defaultTimed.intField("seconds"))
        assertEquals("3세트 · 30초", defaultTimed.stringField("label"))
    }

    @Test
    fun prescription_capsRpeByFatigueGate() {
        val prescription = invokePrescribe(
            candidate(),
            ProgramExerciseRole.ANCHOR,
            week(targetRpeMax = 8.6),
            gate(rpeCap = 7)
        )

        assertEquals(7, prescription.intField("rpe"))
    }

    @Test
    fun durationEstimate_usesCurrentWorkSetupAndRestRules() {
        val repCandidate = candidate(defaultRestSeconds = 120)
        val repPrescription = invokePrescribe(repCandidate, ProgramExerciseRole.ANCHOR, week(), gate())
        assertEquals(45 + 4 * (5 * 4) + 3 * 120, invokeEstimateItemDuration(repCandidate, repPrescription))

        val timedCandidate = timedCandidate(progressMetricType = "REPS_OR_TIME", defaultRestSeconds = 30)
        val timedPrescription = invokePrescribe(timedCandidate, ProgramExerciseRole.TRANSFER, week(), gate())
        assertEquals(45 + 4 * 20 + 3 * 30, invokeEstimateItemDuration(timedCandidate, timedPrescription))
    }

    @Test
    fun fitRequiredPrescription_reducesSetsToCurrentLargestFittingCount() {
        val candidate = candidate(defaultRestSeconds = 120)
        val prescription = invokePrescribe(candidate, ProgramExerciseRole.ANCHOR, week(), gate())

        val reduced = invokeFitRequiredPrescription(candidate, prescription, remainingSeconds = 300)
        assertEquals(2, reduced.intField("setCount"))
        assertEquals(5, reduced.intField("reps"))
        assertEquals(8, reduced.intField("rpe"))

        val minimum = invokeFitRequiredPrescription(candidate, prescription, remainingSeconds = 40)
        assertEquals(1, minimum.intField("setCount"))
        assertEquals("4×5", minimum.stringField("label"))
    }

    private fun invokeExerciseCount(minutes: Int): Int =
        ProgramBuilder::class.java
            .getDeclaredMethod("exerciseCount", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(builder, minutes) as Int

    private fun invokeWarmupReserveSeconds(minutes: Int): Int =
        ProgramBuilder::class.java
            .getDeclaredMethod("warmupReserveSeconds", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(builder, minutes) as Int

    private fun invokePrescribe(
        candidate: ProgramCandidate,
        role: ProgramExerciseRole,
        week: ProgramWeekPlan,
        gate: ProgramFatigueGate
    ): Any =
        ProgramBuilder::class.java
            .getDeclaredMethod(
                "prescribe",
                ProgramCandidate::class.java,
                ProgramExerciseRole::class.java,
                ProgramWeekPlan::class.java,
                ProgramFatigueGate::class.java
            )
            .apply { isAccessible = true }
            .invoke(builder, candidate, role, week, gate)!!

    private fun invokeEstimateItemDuration(candidate: ProgramCandidate, prescription: Any): Int =
        ProgramBuilder::class.java
            .getDeclaredMethod("estimateItemDurationSeconds", ProgramCandidate::class.java, prescription.javaClass)
            .apply { isAccessible = true }
            .invoke(builder, candidate, prescription) as Int

    private fun invokeFitRequiredPrescription(
        candidate: ProgramCandidate,
        prescription: Any,
        remainingSeconds: Int
    ): Any =
        ProgramBuilder::class.java
            .getDeclaredMethod(
                "fitRequiredPrescription",
                ProgramCandidate::class.java,
                prescription.javaClass,
                Int::class.javaPrimitiveType
            )
            .apply { isAccessible = true }
            .invoke(builder, candidate, prescription, remainingSeconds)!!

    private fun Any.intField(name: String): Int =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.getInt(this)

    private fun Any.stringField(name: String): String =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this) as String

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
