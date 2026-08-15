package com.training.trackplanner.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExerciseSeedMetadataPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun programSlotCapabilitiesUseOnlyTheApprovedStableKeyWhitelist() {
        val actual = relationRows("exercise_program_slot_capability_relations_v1.csv")
            .associate { row -> row.getValue("exerciseStableKey") to row.getValue("capabilityCode") }
        val expected = APPROVED_LEGACY_TRAINING_ROLES.mapValues { (_, role) ->
            LegacyTrainingRoleImportMapper.resolve(role).programSlotCapabilities.single().name
        }

        assertEquals(expected, actual)
        assertFalse(actual.containsKey("barbell_back_squat"))
        assertFalse(actual.containsKey("barbell_bench_press"))
    }

    @Test
    fun canonicalSportTransferProjectionRemainsExplicit() {
        val seeds = exactSeedMap()

        assertEquals(
            setOf("BADMINTON_FOOTWORK", "CHANGE_OF_DIRECTION", "COURT_CONDITIONING"),
            seeds.getValue("ex_216351a1").sportTransferDirect.split(',').filter(String::isNotBlank).toSet()
        )
    }

    @Test
    fun builtInBackupExportUsesExactSeedMetadataEvenWhenDbRowIsStale() {
        val seeds = exactSeedMap()

        riskyBuiltIns(seeds.values.toList()).forEach { seed ->
            val staleDbExercise = seed.copy(
                primaryMuscles = "BROKEN",
                secondaryMuscles = "BROKEN",
                equipment = "BROKEN",
                movementPattern = "SQUAT",
                movementCategory = "STRENGTH",
                forceType = "SQUAT",
                bodyRegion = "UPPER",
                laterality = "BROKEN",
                plane = "BROKEN",
                metadataConfidence = "LOW"
            )

            val exportExercise = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(staleDbExercise, seeds)

            assertSeedMetadata(seed, exportExercise)
            assertTrue(exportExercise.movementPattern.contains("|"))
        }
    }

    @Test
    fun builtInBackupImportIgnoresCorruptedCsvMetadata() {
        val seeds = exactSeedMap()
        val seed = seeds.values.first { exercise -> exercise.movementPattern.contains("|") }
        val corruptedCsvExercise = seed.copy(
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            forceType = "SQUAT",
            bodyRegion = "UPPER",
            metadataConfidence = "LOW"
        )

        val imported = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(corruptedCsvExercise, seeds)

        assertSeedMetadata(seed, imported)
    }

    @Test
    fun builtInRoundTripReexportsSeedPipeMetadata() {
        val seeds = exactSeedMap()
        val repaired = riskyBuiltIns(seeds.values.toList()).map { seed ->
            ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(
                seed.copy(movementPattern = "SQUAT", forceType = "SQUAT", bodyRegion = "UPPER"),
                seeds
            )
        }
        val parsed = RecordCsvBackupRestore.parse(
            RecordCsvBackupRestore.buildRestoreCsv(
                entriesWithSets = emptyList(),
                metrics = emptyList(),
                exercises = repaired
            )
        ) as RecordCsvImportData.Restore

        parsed.exerciseRows.forEach { row ->
            val seed = seeds.getValue(row.stableKey.seedLookupKey())
            assertFalse(row.movementPattern == "SQUAT" && row.forceType == "SQUAT" && row.bodyRegion == "UPPER")
            assertEquals(seed.movementPattern, row.movementPattern)
            assertEquals(seed.forceType, row.forceType)
            assertEquals(seed.bodyRegion, row.bodyRegion)
            assertTrue(row.movementPattern.contains("|"))
        }
    }

    @Test
    fun customExerciseMetadataIsNotForcedToSeedMetadata() {
        val custom = Exercise(
            name = "Custom cable thing",
            category = "Custom",
            stableKey = "user_ex_custom",
            movementPattern = "SQUAT",
            forceType = "SQUAT",
            bodyRegion = "UPPER",
            isCustom = true
        )

        assertEquals(custom, ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(custom, exactSeedMap()))
    }

    @Test
    fun seedMetadataRepairIsIdempotent() {
        val seeds = exactSeedMap()
        val stale = seeds.values.first { exercise -> exercise.movementPattern.contains("|") }
            .copy(movementPattern = "SQUAT", forceType = "SQUAT")

        val once = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(stale, seeds)
        val twice = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(once, seeds)

        assertEquals(once, twice)
    }

    @Test
    fun unknownExerciseDefaultsStayNeutral() {
        val mapped = RuntimeExerciseMetadataDefaults.forExercise(
            Exercise(name = "Unknown upper accessory", category = "Custom", stableKey = "custom_unknown")
        )

        assertEquals("NOT_APPLICABLE", mapped.movementFamily)
        assertEquals("HIDDEN", mapped.planningEligibility)
        assertTrue(mapped.analysisEligibility.values.isEmpty())
    }

    private fun riskyBuiltIns(seeds: List<Exercise>): List<Exercise> =
        seeds.filter { exercise -> exercise.movementPattern.contains("|") }.take(9)

    private fun assertSeedMetadata(seed: Exercise, actual: Exercise) {
        assertEquals(seed.primaryMuscles, actual.primaryMuscles)
        assertEquals(seed.secondaryMuscles, actual.secondaryMuscles)
        assertEquals(seed.equipment, actual.equipment)
        assertEquals(seed.movementPattern, actual.movementPattern)
        assertEquals(seed.movementCategory, actual.movementCategory)
        assertEquals(seed.forceType, actual.forceType)
        assertEquals(seed.bodyRegion, actual.bodyRegion)
        assertEquals(seed.laterality, actual.laterality)
        assertEquals(seed.plane, actual.plane)
        assertEquals(seed.sportTransferDirect, actual.sportTransferDirect)
        assertEquals(seed.sportTransferSupportive, actual.sportTransferSupportive)
        assertEquals(seed.loadProfile, actual.loadProfile)
        assertEquals(seed.metadataConfidence, actual.metadataConfidence)
    }

    private fun exactSeedMap(): Map<String, Exercise> =
        SeedData.exactExerciseMetadataByStableKey(context)

    private fun relationRows(fileName: String): List<Map<String, String>> {
        val file = listOf(
            File("src/main/assets/metadata/relations/$fileName"),
            File("app/src/main/assets/metadata/relations/$fileName")
        ).first(File::exists)
        val rows = file.readLines(Charsets.UTF_8).filter(String::isNotBlank).map(::parseCsvLine)
        val header = rows.first()
        return rows.drop(1).map { values ->
            header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        values += current.toString()
        return values
    }

    private fun Map<String, String>.value(key: String): String =
        this[key]?.trim().orEmpty()

    private fun String.seedLookupKey(): String =
        trim().lowercase(Locale.ROOT)

    private companion object {
        val APPROVED_LEGACY_TRAINING_ROLES = mapOf(
            "single_leg_rdl" to "MAIN_STRENGTH",
            "dumbbell_romanian_deadlift" to "MAIN_STRENGTH",
            "barbell_romanian_deadlift" to "MAIN_STRENGTH",
            "ex_e2efd0fe" to "MAIN_STRENGTH",
            "ex_bd072cd" to "ACCESSORY",
            "single_leg_hip_bridge" to "STABILITY",
            "ex_d60745b4" to "ACCESSORY",
            "ex_8824026f" to "PLYOMETRIC",
            "ex_5ca7133f" to "ACCESSORY",
            "ex_5322f2d1" to "ACCESSORY",
            "ex_462c760e" to "SECONDARY_STRENGTH",
            "ex_eb636bac" to "ACCESSORY",
            "ex_33841b88" to "SPEED_REACTIVE",
            "ex_a12de111" to "SPEED_REACTIVE",
            "ex_34e7d21" to "PLYOMETRIC",
            "lateral_bound_continuous" to "PLYOMETRIC",
            "med_ball_overhead_slam" to "POWER",
            "vipr_chop" to "POWER",
            "ex_85f12271" to "STABILITY",
            "ex_314df428" to "PLYOMETRIC",
            "half_kneeling_single_arm_dumbbell_press" to "STABILITY",
            "half_kneeling_single_arm_kettlebell_press" to "STABILITY",
            "medicine_ball_rotational_throw" to "POWER",
            "med_ball_rotational_slam" to "POWER",
            "landmine_rotation" to "POWER",
            "vipr_rotational_lift" to "POWER"
        )
    }
}
