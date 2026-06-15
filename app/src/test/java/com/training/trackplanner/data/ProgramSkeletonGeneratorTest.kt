package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramSkeletonGeneratorTest {
    @Test
    fun generatedSkeletonRespectsWeeklyTrainingDays() {
        val skeleton = ProgramSkeletonGenerator().generate(
            request = baseRequest(weeklyTrainingDays = 2),
            exercises = sampleExercises(),
            history = emptyList()
        )

        val weekOneDays = skeleton.items
            .filter { it.weekNumber == 1 }
            .map { it.dayOfWeek }
            .distinct()

        assertEquals(2, weekOneDays.size)
        assertEquals(28, skeleton.durationDays)
        assertTrue(skeleton.items.all { !it.prescription.contains("confirmed=true") })
    }

    @Test
    fun highBadmintonRatioDoesNotReduceTransferWork() {
        val low = ProgramSkeletonGenerator().generate(
            request = baseRequest(badmintonTransferRatio = 0.25),
            exercises = sampleExercises(),
            history = emptyList()
        )
        val high = ProgramSkeletonGenerator().generate(
            request = baseRequest(badmintonTransferRatio = 0.70),
            exercises = sampleExercises(),
            history = emptyList()
        )

        val lowTransfer = low.items.count { it.selectionReason.contains("전이") || it.selectionReason.contains("감속") }
        val highTransfer = high.items.count { it.selectionReason.contains("전이") || it.selectionReason.contains("감속") }

        assertTrue(highTransfer >= lowTransfer)
        assertEquals(ProgramPeriodizationType.BADMINTON_WAVE, high.periodizationType)
    }

    @Test
    fun directHistoryFillsProgramWeight() {
        val squat = strengthExercise(id = 1, name = "스쿼트", pattern = MovementPattern.SQUAT)
        val history = listOf(
            WorkoutEntryWithSets(
                entry = WorkoutEntry(
                    id = 10,
                    date = "2026-06-01",
                    exerciseId = squat.id,
                    exerciseName = squat.name,
                    category = squat.category
                ),
                sets = listOf(
                    WorkoutSet(
                        id = 20,
                        entryId = 10,
                        setIndex = 1,
                        reps = 5,
                        weightKg = 100.0,
                        confirmed = true
                    )
                )
            )
        )

        val skeleton = ProgramSkeletonGenerator().generate(
            request = baseRequest(goal = ProgramGoal.STRENGTH, availableEquipment = setOf("BARBELL")),
            exercises = listOf(squat),
            history = history
        )

        assertTrue(skeleton.items.any { it.weightKg > 0.0 })
        assertTrue(skeleton.items.any { it.weightSource == "DIRECT_HISTORY_HIGH" })
    }

    private fun baseRequest(
        goal: ProgramGoal = ProgramGoal.BADMINTON_SUPPORT,
        weeklyTrainingDays: Int = 3,
        badmintonTransferRatio: Double = 0.40,
        availableEquipment: Set<String> = setOf("BARBELL", "DUMBBELL", "BAND", "BODYWEIGHT")
    ): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = "",
            goal = goal,
            weeklyTrainingDays = weeklyTrainingDays,
            sessionMinutes = 45,
            availableEquipment = availableEquipment,
            excludedExerciseText = "",
            badmintonTransferRatio = badmintonTransferRatio,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO
        )

    private fun sampleExercises(): List<Exercise> =
        listOf(
            strengthExercise(1, "스쿼트", MovementPattern.SQUAT),
            strengthExercise(2, "루마니안 데드리프트", MovementPattern.HINGE),
            transferExercise(3, "래터럴 바운드 투 스틱", MovementPattern.BOUND),
            transferExercise(4, "홉 투 스틱", MovementPattern.HOP),
            prehabExercise(5, "밴드 외회전", MovementPattern.PREHAB),
            prehabExercise(6, "데드버그", MovementPattern.ANTI_ROTATION),
            strengthExercise(7, "벤치프레스", MovementPattern.PUSH_HORIZONTAL),
            strengthExercise(8, "풀업", MovementPattern.PULL_VERTICAL)
        )

    private fun strengthExercise(id: Long, name: String, pattern: MovementPattern): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "근력운동",
            defaultRestSeconds = 90,
            stableKey = "test_$id",
            movementPattern = pattern.name,
            movementCategory = MovementCategory.STRENGTH.name,
            equipment = "BARBELL",
            trainingRole = FatigueTrainingRole.MAIN_STRENGTH.name,
            axialLoadLevel = if (pattern in setOf(MovementPattern.SQUAT, MovementPattern.HINGE)) {
                AxialLoadLevel.HIGH.name
            } else {
                AxialLoadLevel.MODERATE.name
            },
            systemicLoadWeight = 0.8,
            neuralHeavyWeight = 0.7,
            localLoadWeight = 0.7,
            progressMetricType = ProgressMetricType.ESTIMATED_1RM.name,
            estimated1RmEligible = true,
            volumeLoadEligible = true,
            analysisEligibility = AnalysisEligibility.STRENGTH_PROGRESS.name,
            metadataConfidence = MetadataConfidence.HIGH.name
        )

    private fun transferExercise(id: Long, name: String, pattern: MovementPattern): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "기능성운동",
            defaultRestSeconds = 75,
            stableKey = "test_$id",
            movementPattern = pattern.name,
            movementCategory = MovementCategory.PLYOMETRIC.name,
            equipment = "BODYWEIGHT",
            trainingRole = FatigueTrainingRole.PLYOMETRIC.name,
            badmintonTransferStrength = BadmintonTransferStrength.DIRECT.name,
            badmintonTransferRoles = BadmintonTransferRole.DECELERATION.name,
            fatigueCategories = "${FatigueCategory.DECELERATION.name},${FatigueCategory.ELASTIC_SSC.name}",
            decelerationWeight = 0.8,
            elasticSscWeight = 0.8,
            neuralSpeedWeight = 0.7,
            analysisEligibility = AnalysisEligibility.BADMINTON_TRANSFER.name,
            metadataConfidence = MetadataConfidence.HIGH.name
        )

    private fun prehabExercise(id: Long, name: String, pattern: MovementPattern): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "근력운동",
            defaultRestSeconds = 45,
            stableKey = "test_$id",
            movementPattern = pattern.name,
            movementCategory = MovementCategory.PREHAB.name,
            equipment = "BAND",
            trainingRole = FatigueTrainingRole.PREHAB.name,
            badmintonTransferStrength = BadmintonTransferStrength.SUPPORTIVE.name,
            badmintonTransferRoles = BadmintonTransferRole.SHOULDER_CARE.name,
            fatigueCategories = FatigueCategory.LOW_FATIGUE_REHAB.name,
            analysisEligibility = "${AnalysisEligibility.BADMINTON_TRANSFER.name},${AnalysisEligibility.RECOVERY_ONLY.name}",
            metadataConfidence = MetadataConfidence.HIGH.name
        )
}
