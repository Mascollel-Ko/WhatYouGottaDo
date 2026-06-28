package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class ExerciseSeedMetadataPolicyTest {
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
    fun allSeedExercisesExportWithExactSeedCsvMetadataFields() {
        val rawRows = seedRowsByStableKey()
        val seeds = exactSeedMap()

        assertEquals(239, rawRows.size)
        assertEquals(rawRows.size, seeds.size)

        val parsed = RecordCsvBackupRestore.parse(
            RecordCsvBackupRestore.buildRestoreCsv(
                entriesWithSets = emptyList(),
                metrics = emptyList(),
                exercises = seeds.values.toList()
            )
        ) as RecordCsvImportData.Restore
        val rawPipeCount = rawRows.values.count { row -> row.value("movement_pattern").contains("|") }
        val exportPipeCount = parsed.exerciseRows.count { row -> row.movementPattern.contains("|") }

        assertEquals(rawRows.size, parsed.exerciseRows.size)
        assertEquals(rawPipeCount, exportPipeCount)
        assertTrue(exportPipeCount > 0)
        parsed.exerciseRows.forEach { row ->
            assertRawSeedMetadata(rawRows.getValue(row.stableKey.seedLookupKey()), row)
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
    fun unknownExerciseFallbackDoesNotBecomeSquat() {
        val mapped = ExerciseMetadataMapper.applyLegacyMetadata(
            Exercise(name = "Unknown upper accessory", category = "Custom", stableKey = "custom_unknown")
        )

        assertEquals(MovementPattern.ISOLATION.name, mapped.movementPattern)
        assertEquals(FatigueForceType.BRACE.name, mapped.forceType)
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
        assertEquals(seed.trainingRole, actual.trainingRole)
        assertEquals(seed.sportTransferDirect, actual.sportTransferDirect)
        assertEquals(seed.sportTransferSupportive, actual.sportTransferSupportive)
        assertEquals(seed.loadProfile, actual.loadProfile)
        assertEquals(seed.metadataConfidence, actual.metadataConfidence)
    }

    private fun assertRawSeedMetadata(seedRow: Map<String, String>, actual: RestoreExerciseRow) {
        assertEquals(seedRow.value("primary_muscles"), actual.primaryMuscles)
        assertEquals(seedRow.value("secondary_muscles"), actual.secondaryMuscles)
        assertEquals(seedRow.value("equipment_tags"), actual.equipment)
        assertEquals(seedRow.value("movement_pattern"), actual.movementPattern)
        assertEquals(seedRow.value("movement_category"), actual.movementCategory)
        assertEquals(seedRow.value("force_type"), actual.forceType)
        assertEquals(seedRow.value("body_region"), actual.bodyRegion)
        assertEquals(seedRow.value("laterality"), actual.laterality)
        assertEquals(seedRow.value("plane"), actual.plane)
    }

    private fun exactSeedMap(): Map<String, Exercise> =
        SeedData.exactExerciseMetadataFromParsedRows(seedRows())

    private fun seedRowsByStableKey(): Map<String, Map<String, String>> =
        seedRows()
            .filter { row -> row["row_type"] == "exercise" }
            .associateBy { row -> row.value("stable_key").seedLookupKey() }

    private fun seedRows(): List<Map<String, String>> {
        val file = listOf(
            File("src/main/assets/training_settings_seed.csv"),
            File("app/src/main/assets/training_settings_seed.csv")
        ).first { candidate -> candidate.exists() }
        val parsedRows = file.readLines(Charsets.UTF_8)
            .filter { line -> line.isNotBlank() }
            .map(::parseCsvLine)
        val header = parsedRows.first()
        return parsedRows.drop(1).map { values ->
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
}
