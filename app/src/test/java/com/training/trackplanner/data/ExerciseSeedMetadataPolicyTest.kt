package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseSeedMetadataPolicyTest {
    @Test
    fun builtInBackupExportUsesSeedMetadataEvenWhenDbRowIsStale() {
        val seeds = seedExercises()
        val seedByStableKey = ExerciseSeedMetadataPolicy.seedMap(seeds)

        riskyBuiltIns(seeds).forEach { seed ->
            val staleDbExercise = seed.copy(
                movementPattern = "SQUAT",
                movementCategory = "STRENGTH",
                forceType = "SQUAT",
                bodyRegion = "UPPER",
                metadataConfidence = "LOW"
            )

            val exportExercise = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(staleDbExercise, seedByStableKey)

            assertSeedMetadata(seed, exportExercise)
            assertFalse(exportExercise.movementPattern == "SQUAT" && exportExercise.forceType == "SQUAT")
        }
    }

    @Test
    fun builtInBackupImportIgnoresCorruptedCsvMetadata() {
        val seeds = seedExercises()
        val seedByStableKey = ExerciseSeedMetadataPolicy.seedMap(seeds)
        val seed = seeds.first { it.name == "인클라인 덤벨 플라이" }
        val corruptedCsvExercise = seed.copy(
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            forceType = "SQUAT",
            bodyRegion = "UPPER",
            metadataConfidence = "LOW"
        )

        val imported = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(corruptedCsvExercise, seedByStableKey)

        assertSeedMetadata(seed, imported)
    }

    @Test
    fun builtInRoundTripDoesNotReexportRiskyRowsAsSquatSquatUpper() {
        val seeds = seedExercises()
        val seedByStableKey = ExerciseSeedMetadataPolicy.seedMap(seeds)
        val repaired = riskyBuiltIns(seeds).map { seed ->
            ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(
                seed.copy(movementPattern = "SQUAT", forceType = "SQUAT", bodyRegion = "UPPER"),
                seedByStableKey
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
            val seed = seeds.single { it.stableKey == row.stableKey }
            assertFalse(row.movementPattern == "SQUAT" && row.forceType == "SQUAT" && row.bodyRegion == "UPPER")
            assertEquals(seed.movementPattern, row.movementPattern)
            assertEquals(seed.forceType, row.forceType)
            assertEquals(seed.bodyRegion, row.bodyRegion)
        }
    }

    @Test
    fun allSeedExercisesExportWithSeedMetadataFields() {
        val seeds = seedExercises()
        val seedByStableKey = ExerciseSeedMetadataPolicy.seedMap(seeds)

        seeds.forEach { seed ->
            val stale = seed.copy(
                primaryMuscles = "BROKEN",
                secondaryMuscles = "BROKEN",
                equipment = "BROKEN",
                movementPattern = "SQUAT",
                movementCategory = "STRENGTH",
                forceType = "SQUAT",
                bodyRegion = "UPPER",
                laterality = "BROKEN",
                plane = "BROKEN",
                trainingRole = "BROKEN",
                sportTransferDirect = "BROKEN",
                sportTransferSupportive = "BROKEN",
                loadProfile = "BROKEN",
                metadataConfidence = "LOW"
            )

            assertSeedMetadata(seed, ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(stale, seedByStableKey))
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

        assertEquals(custom, ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(custom, ExerciseSeedMetadataPolicy.seedMap(seedExercises())))
    }

    @Test
    fun seedMetadataRepairIsIdempotent() {
        val seeds = seedExercises()
        val seedByStableKey = ExerciseSeedMetadataPolicy.seedMap(seeds)
        val stale = seeds.first { it.name == "딥스" }.copy(movementPattern = "SQUAT", forceType = "SQUAT")

        val once = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(stale, seedByStableKey)
        val twice = ExerciseSeedMetadataPolicy.applyBuiltInSeedMetadata(once, seedByStableKey)

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

    private fun riskyBuiltIns(seeds: List<Exercise>): List<Exercise> {
        val names = listOf(
            "덤벨 풀오버",
            "딥스",
            "벤치 딥스",
            "스트레이트암 풀다운",
            "원암 스트레이트암 풀다운",
            "원암 케이블 플라이",
            "인클라인 덤벨 플라이",
            "케이블 플라이",
            "플랫 덤벨 플라이"
        )
        return names.map { name -> seeds.single { seed -> seed.name == name } }
    }

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

    private fun seedExercises(): List<Exercise> =
        SeedData.exercisesFromParsedRows(seedRows())

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
}
