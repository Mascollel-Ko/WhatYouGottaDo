package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.readiness.FatigueDetailSection
import com.training.trackplanner.analysis.readiness.FatigueDetailType
import com.training.trackplanner.analysis.readiness.FatigueLevel
import com.training.trackplanner.analysis.readiness.ReadinessStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class BadmintonTransferAnalysisEngineTest {
    private val today = LocalDate.parse("2026-06-15")

    @Test
    fun firstCardSentenceIsSingleRecommendationWithoutNumbers() {
        val exercise = lateralExercise()
        val summary = BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = listOf(
                record(exercise, today.minusDays(1), listOf(set(reps = 8, confirmed = true)))
            ),
            readinessSummary = readiness(ReadinessStatus.READY)
        )

        val sentence = summary.metrics.recommendationSentence
        assertTrue(sentence.startsWith("오늘은 "))
        assertTrue(sentence.endsWith("추천드립니다."))
        assertEquals(1, sentence.count { char -> char == '.' })
        assertFalse(sentence.any { char -> char.isDigit() })
        assertFalse(sentence.contains("%"))
    }

    @Test
    fun calculatesTransferShareFromStructuredMetadataOnly() {
        val exercise = lateralExercise(name = "Renamed without classification words")
        val summary = BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = listOf(
                record(exercise, today.minusDays(1), listOf(set(reps = 10, confirmed = true)))
            ),
            readinessSummary = readiness(ReadinessStatus.READY)
        )

        assertTrue((summary.metrics.axisShare7d[BadmintonTransferAxis.LATERAL_MOVEMENT] ?: 0.0) > 0.0)
        assertTrue((summary.metrics.axisShare7d[BadmintonTransferAxis.DECELERATION_LANDING] ?: 0.0) > 0.0)
        assertTrue(summary.metrics.topTransferExercises7d.single().exerciseName == exercise.name)
        assertNoExerciseNameParsingInBadmintonPackage()
    }

    @Test
    fun plannedSetsDoNotFeedTransferStimulus() {
        val exercise = lateralExercise()
        val confirmedOnly = BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = listOf(
                record(
                    exercise,
                    today.minusDays(1),
                    confirmedSets = listOf(set(reps = 5, confirmed = true)),
                    plannedSets = emptyList()
                )
            ),
            readinessSummary = readiness(ReadinessStatus.READY)
        )
        val withPlanned = BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = listOf(
                record(
                    exercise,
                    today.minusDays(1),
                    confirmedSets = listOf(set(reps = 5, confirmed = true)),
                    plannedSets = listOf(set(reps = 100, weightKg = 200.0, confirmed = false))
                )
            ),
            readinessSummary = readiness(ReadinessStatus.READY)
        )

        assertEquals(
            confirmedOnly.metrics.totalTransferStimulus7d,
            withPlanned.metrics.totalTransferStimulus7d,
            0.001
        )
    }

    @Test
    fun highFatigueAvoidsStrongTransferRecommendation() {
        val exercise = lateralExercise()
        val summary = BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = listOf(
                record(exercise, today.minusDays(1), listOf(set(reps = 12, confirmed = true)))
            ),
            readinessSummary = readiness(ReadinessStatus.FATIGUED, FatigueLevel.HIGH)
        )

        assertEquals(BadmintonTransferAxis.LOW_FATIGUE_CONTROL, summary.metrics.recommendedAxis)
        assertEquals("오늘은 저피로 보완운동을 추천드립니다.", summary.metrics.recommendationSentence)
    }

    @Test
    fun veryHighFatiguePrioritizesRecovery() {
        val exercise = lateralExercise()
        val summary = BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = listOf(
                record(exercise, today.minusDays(1), listOf(set(reps = 12, confirmed = true)))
            ),
            readinessSummary = readiness(ReadinessStatus.LIMITED, FatigueLevel.VERY_HIGH)
        )

        assertEquals(null, summary.metrics.recommendedAxis)
        assertEquals("오늘은 배드민턴 전이 운동보다 회복을 우선 추천드립니다.", summary.metrics.recommendationSentence)
    }

    @Test
    fun emptyRecordsFallbackWithoutCrash() {
        val summary = BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = listOf(lateralExercise()),
            entriesWithSets = emptyList(),
            readinessSummary = readiness(ReadinessStatus.READY)
        )

        assertEquals(0.0, summary.metrics.totalTransferStimulus7d, 0.001)
        assertEquals("오늘은 하체 기초근력 운동을 추천드립니다.", summary.metrics.recommendationSentence)
        assertEquals(AnalysisConfidence.LOW, summary.confidence)
    }

    @Test
    fun lowerBodyFoundationExercisesMapToLowerBodyStrengthAxis() {
        val exercises = listOf(
            transferExercise("스쿼트", "squat", "KNEE_DOMINANT|LOWER_BODY_STRENGTH|SQUAT_PATTERN", "LOWER_BODY"),
            transferExercise("데드리프트", "deadlift", "HIP_HINGE|POSTERIOR_CHAIN_STRENGTH", "HINGE"),
            transferExercise("루마니안 데드리프트", "romanian_deadlift", "HIP_HINGE|POSTERIOR_CHAIN_STRENGTH", "HINGE"),
            transferExercise("워킹 런지", "walking_lunge", "KNEE_DOMINANT|LOWER_BODY_STRENGTH|LUNGE_PATTERN|UNILATERAL_LOWER", "LOWER_BODY"),
            transferExercise("리버스 런지", "reverse_lunge", "KNEE_DOMINANT|LOWER_BODY_STRENGTH|LUNGE_PATTERN|UNILATERAL_LOWER", "LOWER_BODY"),
            transferExercise("레그 프레스", "leg_press", "KNEE_DOMINANT|LOWER_BODY_STRENGTH", "LOWER_BODY")
        )

        exercises.forEach { exercise ->
            val axes = BadmintonTransferMetadataMapper.transferAxes(AnalysisFeatureExtractor.fromExercise(exercise))
            assertTrue("${exercise.name} should contribute to lower-body foundation", BadmintonTransferAxis.LOWER_BODY_STRENGTH in axes)
        }
    }

    @Test
    fun upperAndAccessoryStrengthDoesNotMapToLowerBodyStrengthAxis() {
        val exercises = listOf(
            transferExercise("벤치프레스", "bench_press", "HORIZONTAL_PUSH|UPPER_BODY_STRENGTH", "PUSH"),
            transferExercise("숄더프레스", "shoulder_press", "VERTICAL_PUSH|UPPER_BODY_STRENGTH", "PUSH"),
            transferExercise("풀업", "pull_up", "VERTICAL_PULL|UPPER_BODY_STRENGTH", "PULL"),
            transferExercise("로우", "row", "HORIZONTAL_PULL|UPPER_BODY_STRENGTH", "PULL"),
            transferExercise("해머 컬", "hammer_curl", "ELBOW_FLEXION|ARM_ACCESSORY", "PULL"),
            transferExercise("플라이", "fly", "CHEST_ISOLATION|UPPER_ACCESSORY", "PUSH"),
            transferExercise("딥스", "dips", "HORIZONTAL_PUSH|UPPER_BODY_STRENGTH", "PUSH"),
            transferExercise("풀오버", "pullover", "PULLOVER|UPPER_ACCESSORY", "PULL"),
            transferExercise("푸시다운", "pushdown", "ELBOW_EXTENSION|ARM_ACCESSORY", "PUSH")
        )

        exercises.forEach { exercise ->
            val axes = BadmintonTransferMetadataMapper.transferAxes(AnalysisFeatureExtractor.fromExercise(exercise))
            assertFalse("${exercise.name} should not contribute to lower-body foundation", BadmintonTransferAxis.LOWER_BODY_STRENGTH in axes)
        }
    }

    @Test
    fun topExerciseContributionsKeepLowerBodyFoundationAxis() {
        val exercises = listOf(
            transferExercise("스쿼트", "squat", "KNEE_DOMINANT|LOWER_BODY_STRENGTH|SQUAT_PATTERN", "LOWER_BODY", id = 11),
            transferExercise("루마니안 데드리프트", "romanian_deadlift", "HIP_HINGE|POSTERIOR_CHAIN_STRENGTH", "HINGE", id = 12),
            transferExercise("워킹 런지", "walking_lunge", "KNEE_DOMINANT|LOWER_BODY_STRENGTH|LUNGE_PATTERN|UNILATERAL_LOWER", "LOWER_BODY", id = 13)
        )
        val entries = exercises.map { exercise ->
            record(exercise, today.minusDays(1), listOf(set(reps = 8, weightKg = 80.0, confirmed = true)))
        }

        val summary = BadmintonTransferScoreCalculator().calculate(today, exercises, entries)

        assertTrue(summary.topTransferExercises7d.isNotEmpty())
        assertTrue(summary.topTransferExercises7d.all { contribution ->
            BadmintonTransferAxis.LOWER_BODY_STRENGTH in contribution.axes
        })
    }

    @Test
    fun recommendationSkipsLowerBodyWhenAbsoluteFoundationStimulusExists() {
        val recommendation = BadmintonTransferRecommendationBuilder().build(
            snapshot = transferSnapshot(
                mapOf(
                    BadmintonTransferAxis.DECELERATION_LANDING to 100.0,
                    BadmintonTransferAxis.UNILATERAL_STABILITY to 100.0,
                    BadmintonTransferAxis.LATERAL_MOVEMENT to 100.0,
                    BadmintonTransferAxis.ROTATION_CONTROL to 100.0,
                    BadmintonTransferAxis.LOWER_BODY_STRENGTH to
                        BadmintonTransferConstants.LOWER_BODY_FOUNDATION_ABSOLUTE_STIMULUS_THRESHOLD
                )
            ),
            readinessSummary = readiness(ReadinessStatus.READY),
            exercises = emptyList()
        )

        assertEquals(BadmintonTransferAxis.LOW_FATIGUE_CONTROL, recommendation.recommendedAxis)
    }

    @Test
    fun recommendationKeepsLowerBodyWhenAbsoluteFoundationStimulusIsLow() {
        val recommendation = BadmintonTransferRecommendationBuilder().build(
            snapshot = transferSnapshot(
                mapOf(
                    BadmintonTransferAxis.DECELERATION_LANDING to 100.0,
                    BadmintonTransferAxis.UNILATERAL_STABILITY to 100.0,
                    BadmintonTransferAxis.LATERAL_MOVEMENT to 100.0,
                    BadmintonTransferAxis.ROTATION_CONTROL to 100.0,
                    BadmintonTransferAxis.LOWER_BODY_STRENGTH to
                        BadmintonTransferConstants.LOWER_BODY_FOUNDATION_ABSOLUTE_STIMULUS_THRESHOLD - 1.0
                )
            ),
            readinessSummary = readiness(ReadinessStatus.READY),
            exercises = emptyList()
        )

        assertEquals(BadmintonTransferAxis.LOWER_BODY_STRENGTH, recommendation.recommendedAxis)
    }

    private fun lateralExercise(id: Long = 1, name: String = "Lateral fixture"): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "기능성운동",
            stableKey = "lateral_fixture_$id",
            movementPattern = "FOOTWORK",
            movementCategory = "REACTIVE",
            primaryMuscles = "QUADS|GLUTES",
            secondaryMuscles = "CALVES|CORE",
            equipment = "NONE",
            compoundType = "DRILL",
            forceType = "DECELERATE",
            plane = "FRONTAL",
            laterality = "ALTERNATING",
            axialLoadLevel = "LOW",
            trainingRole = "SKILL",
            badmintonTransferRoles = "FOOTWORK|REACTION|DECELERATION",
            fatigueCategories = "NEURAL_SPEED|DECELERATION|ELASTIC_SSC",
            adaptiveBaselineGroups = "BADMINTON_COURT|DECELERATION",
            recoveryDecayProfile = "MEDIUM",
            systemicLoadWeight = 0.2,
            neuralSpeedWeight = 0.8,
            localLoadWeight = 0.5,
            decelerationWeight = 0.8,
            elasticSscWeight = 0.5,
            progressMetricType = "QUALITY_BASED",
            strengthProgressionGroup = "BADMINTON_TEST",
            hypertrophyVolumeGroup = "NONE",
            mainLiftGroup = "NONE",
            accessoryContributionGroup = "BADMINTON_SUPPORT",
            badmintonTransferStrength = "DIRECT",
            courtMovementTypes = "LATERAL_MOVE|DECELERATION",
            badmintonSkillTargets = "FOOTWORK_SPEED|DECELERATION_CONTROL",
            stabilityDemandLevel = "HIGH",
            mobilityDemandLevel = "MODERATE",
            balanceContributionTags = "UNILATERAL_LOWER|KNEE_CONTROL",
            analysisEligibility = "FATIGUE|BADMINTON_TRANSFER|BALANCE",
            metadataConfidence = "HIGH"
        )

    private fun transferSnapshot(
        axisStimulus: Map<BadmintonTransferAxis, Double>
    ): BadmintonTransferScoreSnapshot {
        val completeStimulus = BadmintonTransferAxis.entries.associateWith { axis -> axisStimulus[axis] ?: 0.0 }
        val total = completeStimulus.values.sum()
        return BadmintonTransferScoreSnapshot(
            totalTransferStimulus7d = total,
            totalTransferStimulus28d = total,
            transferRatio7dTo28dAverage = null,
            axisStimulus7d = completeStimulus,
            axisStimulus28d = completeStimulus,
            transferTypeStimulus7d = emptyMap(),
            topTransferExercises7d = emptyList(),
            sampleEntryCount7d = 5,
            sampleEntryCount28d = 5
        )
    }

    private fun transferExercise(
        name: String,
        stableKey: String,
        movementPattern: String,
        forceType: String,
        id: Long = stableKey.hashCode().toLong().let { value -> if (value == 0L) 1L else kotlin.math.abs(value) }
    ): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "strength",
            stableKey = stableKey,
            movementPattern = movementPattern,
            forceType = forceType,
            badmintonTransferStrength = "GENERAL",
            analysisEligibility = "BADMINTON_TRANSFER",
            metadataConfidence = "HIGH"
        )

    private fun readiness(
        status: ReadinessStatus,
        level: FatigueLevel = FatigueLevel.NORMAL
    ): TodayReadinessSummary =
        TodayReadinessSummary(
            status = status,
            headline = "fixture",
            shortReason = "fixture",
            primaryReasons = emptyList(),
            recommendedModes = emptyList(),
            restrictedModes = emptyList(),
            confidence = AnalysisConfidence.MEDIUM,
            detailSections = listOf(
                FatigueDetailSection(
                    type = FatigueDetailType.BADMINTON_COURT,
                    title = "fixture",
                    level = level,
                    summary = "fixture",
                    metrics = emptyList(),
                    relatedCategories = emptyList(),
                    restrictedTargets = emptyList()
                )
            ),
            adaptiveBaselineNotes = emptyList(),
            generatedAt = LocalDateTime.parse("2026-06-15T12:00:00")
        )

    private fun record(
        exercise: Exercise,
        date: LocalDate,
        confirmedSets: List<WorkoutSet>,
        plannedSets: List<WorkoutSet> = emptyList()
    ): WorkoutEntryWithSets {
        val entry = WorkoutEntry(
            id = exercise.id * 100 + date.dayOfYear,
            date = date.toString(),
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            category = exercise.category
        )
        val sets = (confirmedSets + plannedSets).mapIndexed { index, set ->
            set.copy(
                id = entry.id * 10 + index,
                entryId = entry.id,
                setIndex = index + 1
            )
        }
        return WorkoutEntryWithSets(entry = entry, sets = sets)
    }

    private fun set(
        reps: Int = 0,
        weightKg: Double = 0.0,
        seconds: Int = 0,
        confirmed: Boolean
    ): WorkoutSet =
        WorkoutSet(
            entryId = 0,
            setIndex = 1,
            reps = reps,
            weightKg = weightKg,
            seconds = seconds,
            confirmed = confirmed
        )

    private fun assertNoExerciseNameParsingInBadmintonPackage() {
        val roots = listOf(
            File("src/main/java/com/training/trackplanner/analysis/badminton"),
            File("app/src/main/java/com/training/trackplanner/analysis/badminton")
        )
        val root = roots.first { candidate -> candidate.exists() }
        val prohibited = Regex("""(exerciseName|entry\.exerciseName|name)\s*\.\s*contains""")
        val matches = root.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file -> file.readLines().mapIndexed { index, line -> "${file.name}:${index + 1}:$line" } }
            .filter { line -> prohibited.containsMatchIn(line) }
            .toList()
        assertEquals(emptyList<String>(), matches)
    }
}
