package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ProgramTimingParityV05018Test {
    private val policy = ProgramPrescriptionPolicy()

    @Test
    fun timingBoundariesAndDefaultRestRemainAtTheCurrentOracle() {
        val minuteOracle = listOf(15, 25, 26, 30, 31, 40, 41, 60, 61, 80, 81, 120)
            .joinToString("|") { minutes ->
                "$minutes:${policy.exerciseCount(minutes)}:${policy.warmupReserveSeconds(minutes)}"
            }
        assertEquals(
            "15:3:300|25:3:300|26:4:300|30:4:300|31:4:480|40:4:480|41:5:480|" +
                "60:5:480|61:6:600|80:6:600|81:7:600|120:7:600",
            minuteOracle
        )

        val candidate = candidate(defaultRestSeconds = 120)
        val prescription = policy.prescribe(candidate, ProgramExerciseRole.ANCHOR, week(), gate())
        assertEquals(45 + 4 * (5 * 4) + 3 * 120, policy.estimateItemDurationSeconds(candidate, prescription))
        assertEquals(2, policy.fitRequiredPrescription(candidate, prescription, remainingSeconds = 300).setCount)
        assertEquals(1, policy.fitRequiredPrescription(candidate, prescription, remainingSeconds = 40).setCount)
    }

    @Test
    fun threeThroughSevenDaySchedulesAndCandidateShortageMatchFrozenOracle() {
        val exercises = seedExercises()
        val rendered = buildString {
            (3..7).forEach { days ->
                val skeleton = ProgramAutoBuilder().build(request(days), exercises)
                appendLine("DAYS=$days;SCHEDULE=${skeleton.weekDaySchedule.toSortedMap()}")
                skeleton.items.forEach { item ->
                    appendLine(
                        listOf(
                            item.weekNumber,
                            item.dayOfWeek,
                            item.orderIndex,
                            item.exerciseStableKey,
                            item.selectionRole,
                            item.trainingSlot,
                            item.setCount,
                            item.reps,
                            item.seconds,
                            item.restSeconds
                        ).joinToString("|")
                    )
                }
            }
            val shortage = runCatching { ProgramAutoBuilder().build(request(days = 3), emptyList()) }
                .exceptionOrNull()
            appendLine("SHORTAGE=${shortage?.javaClass?.simpleName}:${shortage?.message}")
        }

        assertEquals(FROZEN_ORACLE_SHA256, rendered.sha256())
    }

    @Test
    fun currentPublicBuilderStillNormalizesRemovedExclusionInputs() {
        val exercises = seedExercises()
        val base = ProgramAutoBuilder().build(request(days = 5), exercises)
        val excluded = ProgramAutoBuilder().build(
            request(days = 5).copy(
                excludedExerciseText = exercises.joinToString(",") { it.name },
                excludedExerciseStableKeys = exercises.map(Exercise::stableKey).toSet()
            ),
            exercises
        )

        assertEquals(base.items.map { it.signature() }, excluded.items.map { it.signature() })
        assertTrue(excluded.request.excludedExerciseText.isBlank())
        assertTrue(excluded.request.excludedExerciseStableKeys.isEmpty())
    }

    private fun candidate(defaultRestSeconds: Int): ProgramCandidate {
        val exercise = Exercise(
            name = "Timing parity lift",
            category = "Strength",
            defaultRestSeconds = defaultRestSeconds,
            stableKey = "timing_parity_lift"
        )
        return ProgramCandidate(exercise, RuntimeExerciseMetadataDefaults.forExercise(exercise), canonical = true)
    }

    private fun week(): ProgramWeekPlan = ProgramWeekPlan(
        weekIndex = 1,
        weekType = ProgramWeekType.BUILD.name,
        volumeMultiplier = 1.0,
        intensityMultiplier = 1.0,
        heavyExposureLimit = 2,
        lowerBodyFatigueLimit = 8.0,
        axialLoadLimit = 2,
        plyometricLimit = 1,
        deloadFlag = false,
        targetRpeMin = 6.0,
        targetRpeMax = 8.0
    )

    private fun gate(): ProgramFatigueGate = ProgramFatigueGate(
        band = ProgramFatigueBand.GREEN,
        volumeFactor = 1.0,
        rpeCap = 9,
        allowsHeavyLower = true,
        allowsHighImpact = true,
        allowsHighIntensityCod = true,
        lowerBodyRestricted = false
    )

    private fun request(days: Int): ProgramSkeletonRequest = ProgramSkeletonRequest(
        name = "Timing parity",
        goal = ProgramGoal.BADMINTON_SUPPORT,
        weeklyTrainingDays = days,
        sessionMinutes = 45,
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = 0.30,
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = 4
    )

    private fun seedExercises(): List<Exercise> {
        val lines = seedFile().readLines(Charsets.UTF_8).filter(String::isNotBlank)
        val header = SeedData.parseCsvLine(lines.first()).map { it.removePrefix("\uFEFF") }
        return lines.drop(1)
            .map(SeedData::parseCsvLine)
            .map { values -> header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap() }
            .filter { it["row_type"] == "exercise" }
            .map { row ->
                Exercise(
                    name = row["exercise_name"].orEmpty(),
                    category = row["category"].orEmpty(),
                    defaultRestSeconds = row["default_rest_seconds"]?.toIntOrNull() ?: 60,
                    stableKey = row["stable_key"].orEmpty(),
                    equipment = row["equipment_tags"].orEmpty(),
                    isActive = true
                )
            }
    }

    private fun seedFile(): File = sequenceOf(
        File("src/main/assets/training_settings_seed.csv"),
        File("app/src/main/assets/training_settings_seed.csv")
    ).firstOrNull(File::exists) ?: error("Missing training settings seed.")

    private fun ProgramSkeletonItem.signature(): String =
        "$weekNumber|$dayOfWeek|$orderIndex|$exerciseStableKey|$selectionRole|$trainingSlot|$setCount|$reps|$seconds|$restSeconds"

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    private companion object {
        const val FROZEN_ORACLE_SHA256 = "45601660EA8189A5ED39D6EB1786900E6205B9EA6B787A1FED2F5C100A1CBD77"
    }
}
