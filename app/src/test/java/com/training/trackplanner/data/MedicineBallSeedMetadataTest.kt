package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MedicineBallSeedMetadataTest {
    @Test
    fun requestedMedicineBallExercisesExistInDefaultSeedWithoutDuplicateStableKeys() {
        val rows = seedRows().filter { row -> row["row_type"] == "exercise" }
        val stableKeys = rows.map { row -> row.getValue("stable_key") }

        assertEquals(stableKeys.size, stableKeys.distinct().size)
        REQUIRED_MEDICINE_BALL_KEYS.forEach { key ->
            val row = rows.single { candidate -> candidate["stable_key"] == key }
            assertEquals("exercise", row["row_type"])
            assertTrue(row.getValue("exercise_name").isNotBlank())
        }
    }

    @Test
    fun requestedMedicineBallExercisesExistInJsonSeed() {
        val json = existingFile("src/main/assets/exercises_seed.json").readText(Charsets.UTF_8)

        REQUIRED_MEDICINE_BALL_KEYS.forEach { key ->
            assertTrue(
                "Missing JSON seed stableKey $key",
                Regex("\"stableKey\"\\s*:\\s*\"$key\"").containsMatchIn(json)
            )
        }
    }

    @Test
    fun requestedMedicineBallCanonicalMetadataIsSupportiveAndProgramSelectable() {
        val asset = existingFile("src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}")
        val rows = RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(asset.readText(Charsets.UTF_8))
        val byKey = rows.associateBy(RuntimeExerciseMetadata::stableKey)

        REQUIRED_MEDICINE_BALL_KEYS.forEach { key ->
            val row = byKey.getValue(key)
            assertEquals("EXERCISE", row.activityKind)
            assertEquals("PROGRAM_SELECTABLE", row.planningEligibility)
            assertEquals("SUPPORTIVE", row.badmintonTransferLevel)
            assertTrue(row.analysisEligibility.contains("BADMINTON_TRANSFER"))
            assertTrue(row.sourceConfidenceLevel.isNotBlank())
            assertTrue(row.finalSourceStatus.isNotBlank())
        }
    }

    private fun seedRows(): List<Map<String, String>> {
        val lines = existingFile("src/main/assets/training_settings_seed.csv")
            .readLines(Charsets.UTF_8)
            .filter(String::isNotBlank)
        val header = parseCsvLine(lines.first()).map { value -> value.removePrefix("\uFEFF") }
        return lines.drop(1).map(::parseCsvLine).map { values ->
            header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val char = line[index]) {
                '"' -> if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index += 1
                } else {
                    quoted = !quoted
                }
                ',' -> if (quoted) {
                    current.append(char)
                } else {
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

    private fun existingFile(relative: String): File = sequenceOf(
        File(relative),
        File("app/$relative")
    ).firstOrNull(File::isFile) ?: error("Missing test asset: $relative")

    private companion object {
        val REQUIRED_MEDICINE_BALL_KEYS = setOf(
            "ex_26ac0c19",
            "medicine_ball_rotational_throw",
            "med_ball_chest_pass",
            "medicine_ball_side_slam",
            "medicine_ball_three_step_acceleration_throw",
            "medicine_ball_three_step_deceleration_throw"
        )
    }
}
