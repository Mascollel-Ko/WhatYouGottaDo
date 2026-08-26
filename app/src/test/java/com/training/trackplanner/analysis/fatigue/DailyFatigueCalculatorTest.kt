package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.CanonicalOfiAxisProfile
import com.training.trackplanner.data.ExerciseMetadataAdapter
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataAssetLoader
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.SeedData
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class DailyFatigueCalculatorTest {
    @Test
    fun factorsUseConfiguredRpeAndAxisLevels() {
        assertEquals(1.00, FatigueRecordFactors.rpeFactor(5.0), 0.0001)
        assertEquals(1.25, FatigueRecordFactors.rpeFactor(8.0), 0.0001)
        assertEquals(1.50, FatigueRecordFactors.rpeFactor(10.0), 0.0001)
        assertEquals(0.25, FatigueRecordFactors.axisLevelMultiplier("LOW"), 0.0001)
        assertEquals(1.00, FatigueRecordFactors.axisLevelMultiplier("VERY_HIGH"), 0.0001)
        assertEquals(0.0, FatigueRecordFactors.axisLevelMultiplier("NONE"), 0.0001)
    }

    @Test
    fun canonicalE1rmUsesPosteriorPointFromThePerformedDate() {
        val firstDate = LocalDate.of(2026, 8, 17)
        val secondDate = firstDate.plusDays(1)
        val stableKey = "barbell_back_squat"
        val exercise = Exercise(name = "Back squat", category = "Strength", stableKey = stableKey)
        val posterior = DailyCanonicalStrengthPosterior(
            canonicalExerciseStableKeys = setOf(stableKey),
            valuesByDate = mapOf(
                firstDate to mapOf(stableKey to 100.0),
                secondDate to mapOf(stableKey to 200.0)
            )
        )

        val result = DailyFatigueCalculator(
            metadataCatalog = RuntimeExerciseMetadataCatalog.of(
                listOf(neutralTestMetadata(stableKey, exercise.name).copy(progressMetricType = "ESTIMATED_1RM"))
            ),
            canonicalStrengthPosterior = posterior
        ).calculateSeries(
            endDate = secondDate,
            days = 2,
            exercises = listOf(exercise),
            entriesWithSets = listOf(
                testRecord(firstDate, stableKey, exercise.name, id = 1),
                testRecord(secondDate, stableKey, exercise.name, id = 2)
            ),
            initialProfile = null
        )

        assertEquals(2.5, result.last().recordContributions.single().trainingLoad, 0.0001)
    }

    @Test
    fun canonicalE1rmDoesNotCarryAnEarlierPosteriorIntoPerformedDate() {
        val firstDate = LocalDate.of(2026, 8, 17)
        val secondDate = firstDate.plusDays(1)
        val stableKey = "barbell_back_squat"
        val exercise = Exercise(name = "Back squat", category = "Strength", stableKey = stableKey)
        val posterior = DailyCanonicalStrengthPosterior(
            canonicalExerciseStableKeys = setOf(stableKey),
            valuesByDate = mapOf(firstDate to mapOf(stableKey to 100.0))
        )

        val result = DailyFatigueCalculator(
            metadataCatalog = RuntimeExerciseMetadataCatalog.of(
                listOf(neutralTestMetadata(stableKey, exercise.name).copy(progressMetricType = "ESTIMATED_1RM"))
            ),
            canonicalStrengthPosterior = posterior
        ).calculateSeries(
            endDate = secondDate,
            days = 2,
            exercises = listOf(exercise),
            entriesWithSets = listOf(
                testRecord(firstDate, stableKey, exercise.name, id = 1),
                testRecord(secondDate, stableKey, exercise.name, id = 2)
            ),
            initialProfile = null
        )

        assertTrue(result.last().recordContributions.isEmpty())
    }

    @Test
    fun calculatesCanonicalFiveAxesAndAllowsLocalHighSystemicLow() {
        val date = LocalDate.of(2026, 6, 19)
        val exercise = Exercise(
            name = "테스트 컬",
            category = "근력운동",
            stableKey = "test_curl"
        )
        val metadata = ExerciseMetadataAdapter.fromFields(
            mapOf(
                "stableKey" to "test_curl",
                "exerciseName" to "테스트 컬",
                "currentActivityKind" to "EXERCISE",
                "currentPlanningEligibility" to "PROGRAM_SELECTABLE",
                "movementFamily" to "ELBOW_FLEXION_BICEPS_CURL_VARIANTS",
                "movementSubtype" to "DUMBBELL_CURL",
                "programSlot" to "BICEPS_ACCESSORY",
                "redundancyGroup" to "ELBOW_FLEXION_CURL",
                "progressMetricType" to "LOAD_REPS",
                "strengthProgressionGroup" to "ELBOW_FLEXION_CURL",
                "analysisEligibility" to "FATIGUE|HYPERTROPHY_VOLUME",
                "primaryStressProfile" to "LOCAL_MUSCLE",
                "secondaryStressTags" to "ISOLATION",
                "tendonStressTags" to "ELBOW_FLEXOR_TENDON",
                "ligamentJointStabilityStressTags" to "NONE",
                "jointImpactStressTags" to "NONE",
                "cognitiveStressTags" to "NONE",
                "sportContextTags" to "NONE",
                "recoveryDecayProfile" to "SHORT",
                "stressMagnitudeHint" to "LOW",
                "badmintonTransferLevel" to "GENERAL",
                "neuromuscularStressLevel" to "LOW",
                "systemicMuscularStressLevel" to "LOW",
                "localMuscularStressLevel" to "VERY_HIGH",
                "jointTendonImpactStressLevel" to "HIGH",
                "movementFocusDemandLevel" to "LOW",
                "recoveryDurationClass" to "SHORT"
            )
        )
        val entry = WorkoutEntry(
            id = 1,
            date = date.toString(),
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category
        )
        val record = WorkoutEntryWithSets(
            entry,
            listOf(
                WorkoutSet(entryId = 1, setIndex = 1, reps = 12, weightKg = 10.0, confirmed = true, rpe = 8.0),
                WorkoutSet(entryId = 1, setIndex = 2, reps = 12, weightKg = 10.0, confirmed = true, rpe = 8.0),
                WorkoutSet(entryId = 1, setIndex = 3, reps = 12, weightKg = 10.0, confirmed = true, rpe = 8.0)
            )
        )

        val result = DailyFatigueCalculator(RuntimeExerciseMetadataCatalog.of(listOf(metadata))).calculate(
            targetDate = date,
            exercises = listOf(exercise),
            entriesWithSets = listOf(record),
            initialProfile = null
        )

        val contribution = result.recordContributions.single()
        assertTrue(contribution.axes.highForceNeural > 0.0)
        assertTrue(contribution.axes.systemicMuscular > 0.0)
        assertTrue(contribution.axes.localMuscular > contribution.axes.systemicMuscular)
        assertEquals(0.0, contribution.axes.highSpeed, 0.0001)
        assertEquals(0.0, contribution.axes.reactive, 0.0001)
        assertTrue(contribution.axes.recoveryPressure > 0.0)
        assertEquals("SHORT", contribution.recoveryDurationClass)
    }

    @Test
    fun recoveryPressureUsesHalfMaxAndHalfMean() {
        val pressure = RecoveryPressureCalculator.calculate(listOf(10.0, 20.0, 30.0, 40.0, 50.0), 1.25)

        assertEquals(50.0, pressure, 0.0001)
    }

    @Test
    fun canonicalBadmintonUsesExplicitIndependentHighSpeedAndReactiveAxisAuthority() {
        val canonical = badmintonContribution(
            secondaryStressTags = "COURT_MOVEMENT_LOAD|DECELERATION_LOAD|OVERHEAD_REPETITION_LOAD",
            cognitiveStressTags = "REACTION_LOAD|DECISION_MAKING_LOAD|VISUAL_TRACKING_LOAD",
            transferTypes = "RALLY_CONDITIONING_DIRECT|REACTION_DECISION_DIRECT|CHANGE_OF_DIRECTION_DIRECT",
            skillTargets = "CHANGE_OF_DIRECTION",
            physicalQualities = "REACTIVE_AGILITY"
        )
        val withoutReactiveCues = badmintonContribution(
            secondaryStressTags = "COURT_MOVEMENT_LOAD|DECELERATION_LOAD|OVERHEAD_REPETITION_LOAD",
            cognitiveStressTags = "NONE",
            transferTypes = "RALLY_CONDITIONING_DIRECT",
            skillTargets = "NONE",
            physicalQualities = "NONE"
        )
        val withoutHighSpeedCues = badmintonContribution(
            secondaryStressTags = "OVERHEAD_REPETITION_LOAD",
            cognitiveStressTags = "REACTION_LOAD|DECISION_MAKING_LOAD|VISUAL_TRACKING_LOAD",
            transferTypes = "REACTION_DECISION_DIRECT|CHANGE_OF_DIRECTION_DIRECT",
            skillTargets = "CHANGE_OF_DIRECTION",
            physicalQualities = "CORE_STABILITY"
        )

        assertTrue(canonical.axes.highSpeed > 0.0)
        assertTrue(canonical.axes.reactive > 0.0)
        assertTrue(canonical.axes.systemicMuscular > 0.0)
        assertEquals(canonical.axes.highSpeed, withoutReactiveCues.axes.highSpeed, 0.0001)
        assertEquals(canonical.axes.reactive, withoutReactiveCues.axes.reactive, 0.0001)
        assertEquals(canonical.axes.reactive, withoutHighSpeedCues.axes.reactive, 0.0001)
        assertEquals(canonical.axes.highSpeed, withoutHighSpeedCues.axes.highSpeed, 0.0001)
        assertFalse(canonical.axes.highSpeed == canonical.axes.reactive)
    }

    @Test
    fun canonicalOfiWarningsDoNotContainLegacyJointOrMovementAxes() {
        val date = LocalDate.of(2026, 6, 19)
        val exercise = Exercise(name = "배드민턴", category = "스포츠", stableKey = "ex_ae9ecdbc")
        val metadata = badmintonMetadata(
            secondaryStressTags = "COURT_MOVEMENT_LOAD|DECELERATION_LOAD",
            cognitiveStressTags = "REACTION_LOAD|DECISION_MAKING_LOAD",
            transferTypes = "REACTION_DECISION_DIRECT|CHANGE_OF_DIRECTION_DIRECT",
            skillTargets = "CHANGE_OF_DIRECTION",
            physicalQualities = "REACTIVE_AGILITY"
        )
        val result = DailyFatigueCalculator(RuntimeExerciseMetadataCatalog.of(listOf(metadata))).calculate(
            targetDate = date,
            exercises = listOf(exercise),
            entriesWithSets = listOf(badmintonRecord(date, exercise)),
            initialProfile = null
        )

        assertFalse(result.state.cautionReasons.any { reason -> reason.contains("JOINT_TENDON") })
        assertFalse(result.state.cautionReasons.any { reason -> reason.contains("POWER_REACTION") })
    }

    @Test
    fun exerciseRenameDoesNotChangeFatigueSemanticsOrGrouping() {
        val date = LocalDate.of(2026, 8, 15)
        val exercise = Exercise(name = "Current name", category = "Strength", stableKey = "stable-lift")
        val metadata = neutralTestMetadata(exercise.stableKey, exercise.name)
        fun result(recordedName: String) = DailyFatigueCalculator(
            RuntimeExerciseMetadataCatalog.of(listOf(metadata))
        ).calculate(
            targetDate = date,
            exercises = listOf(exercise),
            entriesWithSets = listOf(testRecord(date, exercise.stableKey, recordedName)),
            initialProfile = null
        )

        val before = result("Old name")
        val after = result("Renamed display text")

        assertEquals(before.state, after.state)
        assertEquals(
            setOf("stable-lift"),
            after.groupStates.filter { it.groupType == "exerciseStableKey" }.mapTo(mutableSetOf(), GroupFatigueState::groupKey)
        )
    }

    @Test
    fun equalDisplayNamesNeverMergeDifferentStableKeys() {
        val date = LocalDate.of(2026, 8, 15)
        val first = Exercise(name = "Same name", category = "Strength", stableKey = "first-key")
        val second = first.copy(stableKey = "second-key")
        val result = DailyFatigueCalculator(
            RuntimeExerciseMetadataCatalog.of(
                listOf(
                    neutralTestMetadata(first.stableKey, first.name),
                    neutralTestMetadata(second.stableKey, second.name)
                )
            )
        ).calculate(
            targetDate = date,
            exercises = listOf(first, second),
            entriesWithSets = listOf(
                testRecord(date, first.stableKey, first.name, id = 1),
                testRecord(date, second.stableKey, second.name, id = 2)
            ),
            initialProfile = null
        )

        assertEquals(
            setOf(first.stableKey, second.stableKey),
            result.groupStates.filter { it.groupType == "exerciseStableKey" }
                .mapTo(mutableSetOf(), GroupFatigueState::groupKey)
        )
    }

    private fun badmintonContribution(
        secondaryStressTags: String,
        cognitiveStressTags: String,
        transferTypes: String,
        skillTargets: String,
        physicalQualities: String
    ): RecordFatigueContribution {
        val date = LocalDate.of(2026, 6, 19)
        val exercise = Exercise(name = "배드민턴", category = "스포츠", stableKey = "ex_ae9ecdbc")
        val metadata = badmintonMetadata(
            secondaryStressTags,
            cognitiveStressTags,
            transferTypes,
            skillTargets,
            physicalQualities
        )
        return DailyFatigueCalculator(
            RuntimeExerciseMetadataCatalog.of(listOf(metadata)),
            canonicalBadmintonOfiProfile()
        ).calculate(
            targetDate = date,
            exercises = listOf(exercise),
            entriesWithSets = listOf(badmintonRecord(date, exercise)),
            initialProfile = null
        ).recordContributions.single()
    }

    private fun badmintonMetadata(
        secondaryStressTags: String,
        cognitiveStressTags: String,
        transferTypes: String,
        skillTargets: String,
        physicalQualities: String
    ): RuntimeExerciseMetadata =
        canonicalBadmintonMetadata().copy(
            secondaryStressTags = MetadataTokenField.parse(secondaryStressTags),
            cognitiveStressTags = MetadataTokenField.parse(cognitiveStressTags),
            badmintonTransferType = MetadataTokenField.parse(transferTypes),
            badmintonSkillTargets = MetadataTokenField.parse(skillTargets),
            badmintonPhysicalQualities = MetadataTokenField.parse(physicalQualities)
        )

    private fun canonicalBadmintonMetadata(): RuntimeExerciseMetadata {
        val asset = sequenceOf(
            File("src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}"),
            File("app/src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}")
        ).first(File::isFile)
        return RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(asset.readText(Charsets.UTF_8))
            .single { metadata -> metadata.stableKey == "ex_ae9ecdbc" }
    }

    private fun canonicalBadmintonOfiProfile(): Map<String, CanonicalOfiAxisProfile> {
        val asset = sequenceOf(
            File("src/main/assets/metadata/canonical_v1/ofi_relations.csv"),
            File("app/src/main/assets/metadata/canonical_v1/ofi_relations.csv")
        ).first(File::isFile)
        val parsed = asset.readLines(Charsets.UTF_8).filter(String::isNotBlank).map(SeedData::parseCsvLine)
        val header = parsed.first()
        val rows = parsed.drop(1).map { values -> header.zip(values).toMap() }
            .filter { row ->
                row.getValue("exerciseStableKey") == "ex_ae9ecdbc" && row.getValue("relationType") == "OFI_AXIS"
            }
        val values = rows.associate { row -> row.getValue("relationId") to row.getValue("coefficient").toDouble() }
        return mapOf(
            "ex_ae9ecdbc" to CanonicalOfiAxisProfile(
                exerciseStableKey = "ex_ae9ecdbc",
                highForceNeural = values.getValue("HIGH_FORCE_NEURAL"),
                systemicMuscular = values.getValue("SYSTEMIC_MUSCULAR"),
                localMuscular = values.getValue("LOCAL_MUSCULAR"),
                highSpeed = values.getValue("HIGH_SPEED"),
                reactive = values.getValue("REACTIVE")
            )
        )
    }

    private fun neutralTestMetadata(stableKey: String, name: String): RuntimeExerciseMetadata =
        ExerciseMetadataAdapter.fromFields(
            mapOf(
                "stableKey" to stableKey,
                "exerciseName" to name,
                "currentActivityKind" to "EXERCISE",
                "currentPlanningEligibility" to "ANALYSIS_ONLY",
                "movementFamily" to "TEST_FAMILY",
                "movementSubtype" to "TEST_MOVEMENT",
                "programSlot" to "NOT_APPLICABLE",
                "redundancyGroup" to "TEST_GROUP",
                "progressMetricType" to "LOAD_REPS",
                "strengthProgressionGroup" to "TEST_GROUP",
                "analysisEligibility" to "FATIGUE",
                "primaryStressProfile" to "TEST_STRESS",
                "recoveryDecayProfile" to "SHORT",
                "neuromuscularStressLevel" to "LOW",
                "systemicMuscularStressLevel" to "LOW",
                "localMuscularStressLevel" to "HIGH",
                "recoveryDurationClass" to "SHORT"
            )
        )

    private fun testRecord(
        date: LocalDate,
        stableKey: String,
        name: String,
        id: Long = 1
    ): WorkoutEntryWithSets = WorkoutEntryWithSets(
        WorkoutEntry(
            id = id,
            date = date.toString(),
            exerciseStableKey = stableKey,
            exerciseName = name,
            category = "Strength"
        ),
        listOf(WorkoutSet(entryId = id, setIndex = 1, reps = 8, weightKg = 20.0, confirmed = true, rpe = 8.0))
    )

    private fun badmintonRecord(date: LocalDate, exercise: Exercise): WorkoutEntryWithSets =
        WorkoutEntryWithSets(
            WorkoutEntry(
                id = 1,
                date = date.toString(),
                exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name,
                category = exercise.category,
                rpe = 8.0
            ),
            listOf(WorkoutSet(entryId = 1, setIndex = 1, seconds = 45 * 60, confirmed = true, rpe = 8.0))
        )
}
