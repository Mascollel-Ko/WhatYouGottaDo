package com.training.trackplanner.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgramBuilderCandidateCollapseTest {
    private val exercises = loadSeedExercises()
    private val catalog = RuntimeExerciseMetadataCatalog.of(
        ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
    )

    @Test
    fun seedProgramsDoNotCollapseToTinyExerciseLists() {
        listOf(
            Scenario("standard 3d 4w", 3, 4, 60, 0.70, 6),
            Scenario("badminton 4d 4w", 4, 4, 60, 0.90, 6),
            Scenario("badminton 5d 8w", 5, 8, 60, 0.80, 8)
        ).forEach { scenario ->
            val result = build(scenario)
            val sessions = result.items.groupBy { it.weekNumber to it.dayOfWeek }
            val distinctExercises = result.items.map(ProgramSkeletonItem::stableKey).toSet()

            assertTrue("${scenario.label} should generate sessions", sessions.isNotEmpty())
            assertTrue("${scenario.label} collapsed session size", sessions.values.all { it.size >= 3 })
            assertTrue("${scenario.label} collapsed distinct exercises: ${distinctExercises.size}", distinctExercises.size >= scenario.minDistinct)
            assertTrue("${scenario.label} missing candidate trace", result.candidateTraces.isNotEmpty())
        }
    }

    @Test
    fun longBadmintonProgramKeepsSupportAccessoryAndPrehabWork() {
        val result = build(Scenario("support 5d 8w", 5, 8, 75, 0.70, 8))
        val roles = result.items.map(ProgramSkeletonItem::selectionRole).toSet()

        assertTrue(ProgramExerciseRole.SUPPORT.name in roles)
        assertTrue(ProgramExerciseRole.ACCESSORY.name in roles || ProgramExerciseRole.PREHAB.name in roles)
    }

    @Test
    fun candidateTraceShowsWhereSlotCandidatesShrink() {
        val result = build(Scenario("trace 3d 4w", 3, 4, 60, 0.70, 6))
        val requiredTrace = result.candidateTraces.first { it.requestedTemplateSlot.isNotBlank() }

        assertTrue(requiredTrace.allActive >= requiredTrace.programSelectable)
        assertTrue(requiredTrace.programSelectable >= requiredTrace.equipmentMatched)
        assertTrue(requiredTrace.equipmentMatched >= requiredTrace.notExcludedByUser)
        assertTrue(requiredTrace.scored >= requiredTrace.selectionPool)
    }

    private fun build(scenario: Scenario): GeneratedProgramSkeleton = ProgramBuilder().build(
        request = ProgramSkeletonRequest(
            name = scenario.label,
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = scenario.days,
            sessionMinutes = scenario.minutes,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = scenario.ratio,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = scenario.weeks
        ),
        exercises = exercises,
        history = emptyList(),
        runtimeMetadataCatalog = catalog
    )

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

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val char = line[index]) {
                '"' -> if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else {
                    quoted = !quoted
                }
                ',' -> if (quoted) current.append(char) else {
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

    private fun seedFile(): File = existingFile("src/main/assets/training_settings_seed.csv")
    private fun canonicalFile(): File = existingFile("src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")
    private fun existingFile(relative: String): File = sequenceOf(File(relative), File("app/$relative")).firstOrNull(File::exists)
        ?: error("Missing test asset: $relative")

    private data class Scenario(
        val label: String,
        val days: Int,
        val weeks: Int,
        val minutes: Int,
        val ratio: Double,
        val minDistinct: Int
    )
}
