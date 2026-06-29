package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.readiness.FatigueDetailSection
import com.training.trackplanner.analysis.readiness.FatigueDetailType
import com.training.trackplanner.analysis.readiness.FatigueLevel
import com.training.trackplanner.analysis.readiness.ReadinessStatus
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
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
    private val removedAxisName = "LOWER_BODY" + "_STRENGTH"
    private val removedAxisLabel = "\uD558\uCCB4 \uAE30\uCD08\uADFC\uB825"

    @Test
    fun transferAxisListDoesNotContainLowerBodyFoundation() {
        val axisNames = BadmintonTransferAxis.entries.map { axis -> axis.name }
        val axisLabels = BadmintonTransferAxis.entries.map { axis -> axis.displayName }

        assertFalse(removedAxisName in axisNames)
        assertFalse(axisLabels.any { label -> label.contains(removedAxisLabel) })
        assertTrue(BadmintonTransferType.GENERAL_STRENGTH in BadmintonTransferType.entries)
        assertTrue(BadmintonTransferAxis.DECELERATION_LANDING in BadmintonTransferAxis.entries)
        assertTrue(BadmintonTransferAxis.UNILATERAL_STABILITY in BadmintonTransferAxis.entries)
        assertTrue(BadmintonTransferAxis.LATERAL_MOVEMENT in BadmintonTransferAxis.entries)
        assertTrue(BadmintonTransferAxis.ROTATION_CONTROL in BadmintonTransferAxis.entries)
        assertTrue(BadmintonTransferAxis.RACKET_SUPPORT in BadmintonTransferAxis.entries)
        assertTrue(BadmintonTransferAxis.AEROBIC_FOOTWORK in BadmintonTransferAxis.entries)
        assertTrue(BadmintonTransferAxis.LOW_FATIGUE_CONTROL in BadmintonTransferAxis.entries)
    }

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
        assertEquals(1, sentence.count { char -> char == '.' })
        assertFalse(sentence.any { char -> char.isDigit() })
        assertFalse(sentence.contains("%"))
        assertFalse(sentence.contains(removedAxisLabel))
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
        assertFalse(summary.metrics.recommendationSentence.contains(removedAxisLabel))
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
        assertFalse(summary.metrics.recommendationSentence.contains(removedAxisLabel))
    }

    @Test
    fun emptyRecordsFallbackDoesNotRecommendRemovedLowerAxis() {
        val summary = BadmintonTransferAnalysisEngine().analyze(
            today = today,
            exercises = listOf(lateralExercise()),
            entriesWithSets = emptyList(),
            readinessSummary = readiness(ReadinessStatus.READY)
        )

        assertEquals(0.0, summary.metrics.totalTransferStimulus7d, 0.001)
        assertEquals(BadmintonTransferAxis.LOW_FATIGUE_CONTROL, summary.metrics.recommendedAxis)
        assertFalse(summary.metrics.recommendationSentence.contains(removedAxisLabel))
        assertEquals(AnalysisConfidence.LOW, summary.confidence)
    }

    @Test
    fun generalStrengthTypeRemainsButDoesNotCreateRemovedAxis() {
        val rawLowerToken = "KNEE_DOMINANT|$removedAxisName|SQUAT_PATTERN"
        val exercise = transferExercise(
            name = "raw squat",
            stableKey = "raw_squat",
            movementPattern = rawLowerToken,
            forceType = "LOWER_BODY",
            transferStrength = "GENERAL"
        )
        val features = AnalysisFeatureExtractor.fromExercise(exercise)

        assertEquals(BadmintonTransferType.GENERAL_STRENGTH, BadmintonTransferMetadataMapper.transferType(features))
        assertFalse(BadmintonTransferMetadataMapper.transferAxes(features).any { axis -> axis.name == removedAxisName })
    }

    @Test
    fun rawLowerBodyStrengthRecordsDoNotCreateTransferAxisOrChartLabel() {
        val exercises = listOf(
            transferExercise("raw squat", "raw_squat", "KNEE_DOMINANT|$removedAxisName|SQUAT_PATTERN", "LOWER_BODY", id = 21),
            transferExercise("raw deadlift", "raw_deadlift", "HIP_HINGE|POSTERIOR_CHAIN_STRENGTH", "HINGE", id = 22),
            transferExercise("raw rdl", "raw_rdl", "HIP_HINGE|POSTERIOR_CHAIN_STRENGTH", "HINGE", id = 23)
        )
        val entries = exercises.map { exercise ->
            record(exercise, today.minusDays(1), listOf(set(reps = 5, weightKg = 80.0, confirmed = true)))
        }

        val summary = BadmintonTransferAnalysisEngine().analyze(today, exercises, entries, readiness(ReadinessStatus.READY))

        assertFalse(summary.metrics.axisShare7d.keys.any { axis -> axis.name == removedAxisName })
        assertFalse(summary.chartData.axisShareBars.any { bar -> bar.label.contains(removedAxisLabel) })
        assertFalse(summary.chartData.windowComparisonBars.any { bar -> bar.label.contains(removedAxisLabel) })
    }

    private fun lateralExercise(id: Long = 1, name: String = "Lateral fixture"): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "training",
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

    private fun transferExercise(
        name: String,
        stableKey: String,
        movementPattern: String,
        forceType: String,
        id: Long = stableKey.hashCode().toLong().let { value -> if (value == 0L) 1L else kotlin.math.abs(value) },
        transferStrength: String = "GENERAL",
        analysisEligibility: String = "BADMINTON_TRANSFER"
    ): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "strength",
            stableKey = stableKey,
            movementPattern = movementPattern,
            forceType = forceType,
            badmintonTransferStrength = transferStrength,
            analysisEligibility = analysisEligibility,
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
