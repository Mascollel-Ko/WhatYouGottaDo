package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgramBuilderCanonicalExamplesTest {
    @Test
    fun canonicalCoverageDiagnosticScansAllRowsWithoutLegacyFallback() {
        val exercises = loadSeedExercises()
        val metadata = ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
        val catalog = RuntimeExerciseMetadataCatalog.of(metadata)

        val diagnostics = ProgramSlotCoverageDiagnostics().analyze(exercises, catalog)

        assertEquals(215, exercises.size)
        assertEquals(ProgramSlotId.entries.size, diagnostics.size)
        assertTrue(diagnostics.all { it.legacyFallbackMatchCount == 0 })
        setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
            ProgramSlotId.UPPER_PULL_ANCHOR,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY
        ).forEach { slot ->
            assertTrue("Expected canonical candidates for $slot", diagnostics.first { it.slot == slot }.candidateCount > 0)
        }
    }

    @Test
    fun requiredExamplesGenerateFromCanonicalAssets() {
        val exercises = loadSeedExercises()
        val metadata = ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
        val catalog = RuntimeExerciseMetadataCatalog.of(metadata)
        assertEquals(215, exercises.size)
        assertEquals(215, catalog.size)

        listOf(
            Example("3 days / 4 weeks / 70:30", 3, 4, 45, 0.70),
            Example("5 days / 8 weeks / 70:30", 5, 8, 60, 0.70),
            Example("7 days / 8 weeks / 80:20", 7, 8, 45, 0.80)
        ).forEach { example ->
            val result = ProgramBuilder().build(
                request = ProgramSkeletonRequest(
                    name = example.label,
                    goal = ProgramGoal.BADMINTON_SUPPORT,
                    weeklyTrainingDays = example.days,
                    sessionMinutes = example.minutes,
                    availableEquipment = emptySet(),
                    excludedExerciseText = "",
                    badmintonTransferRatio = example.ratio,
                    sportStrengthRatio = "AUTO",
                    periodizationType = ProgramPeriodizationType.AUTO,
                    durationWeeks = example.weeks
                ),
                exercises = exercises,
                history = emptyList(),
                runtimeMetadataCatalog = catalog
            )
            assertEquals(example.weeks, result.weekPlans.size)
            assertTrue(result.items.isNotEmpty())
            assertTrue(result.items.groupBy { it.weekNumber to it.dayOfWeek }.values.all { it.size <= 7 })
            assertTrue(
                "${example.label} must not contain hard validation failures: ${result.validationIssues}",
                result.validationDetails.none { it.severity == ProgramValidationSeverity.HARD }
            )
            if (example.days == 7) {
                assertTrue(result.items.groupBy { it.weekNumber }.values.all { weekItems ->
                    weekItems.groupBy { it.dayOfWeek }.count { (_, rows) -> rows.first().dayIntensity == "HARD" } <= 2
                })
            }
            val weeklySignatures = result.items.groupBy { it.weekNumber }.values.map { week ->
                week.sortedWith(compareBy(ProgramSkeletonItem::dayOfWeek, ProgramSkeletonItem::orderIndex))
                    .joinToString("|") { it.exerciseName }
            }
            assertTrue("${example.label} must vary across weeks", weeklySignatures.distinct().size > 1)
            println(example.render(result))
        }
    }

    private fun Example.render(result: GeneratedProgramSkeleton): String = buildString {
        appendLine("PROGRAM EXAMPLE: $label")
        result.weekPlans.forEach { week ->
            val weekItems = result.items.filter { it.weekNumber == week.weekIndex }
            val slots = weekItems.groupBy { it.dayOfWeek }.toSortedMap().entries.joinToString("; ") { (_, rows) ->
                "${rows.first().trainingSlot}=[${rows.take(3).joinToString { it.exerciseName }}]"
            }
            appendLine("W${week.weekIndex} ${week.weekType}: $slots")
        }
        appendLine("validationIssues=${result.validationIssues}")
    }

    private fun loadSeedExercises(): List<Exercise> {
        val lines = seedFile().readLines(Charsets.UTF_8).filter(String::isNotBlank)
        val header = parseCsvLine(lines.first()).map { it.removePrefix("\uFEFF") }
        return lines.drop(1).map(::parseCsvLine).map { values ->
            header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
        }.filter { it["row_type"] == "exercise" }.mapIndexed { index, row ->
            Exercise(
                id = (index + 1).toLong(),
                name = row["exercise_name"].orEmpty(),
                category = row["category"].orEmpty(),
                defaultRestSeconds = row["default_rest_seconds"]?.toIntOrNull() ?: 60,
                stableKey = row["stable_key"].orEmpty(),
                equipment = row["equipment_tags"].orEmpty(),
                isActive = true
            )
        }
    }

    private fun seedFile(): File = existingFile("src/main/assets/training_settings_seed.csv")
    private fun canonicalFile(): File = existingFile(
        "src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"
    )

    private fun existingFile(relative: String): File = sequenceOf(
        File(relative),
        File("app/$relative")
    ).firstOrNull(File::exists) ?: error("Missing test asset: $relative")

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private data class Example(
        val label: String,
        val days: Int,
        val weeks: Int,
        val minutes: Int,
        val ratio: Double
    )
}
