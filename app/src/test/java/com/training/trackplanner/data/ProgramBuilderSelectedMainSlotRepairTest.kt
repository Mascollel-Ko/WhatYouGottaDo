package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgramBuilderSelectedMainSlotRepairTest {
    private val exercises = loadSeedExercises()
    private val catalog = RuntimeExerciseMetadataCatalog.of(
        ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
    )

    @Test
    fun selectedMainAndCaptainChairTargetsSurviveHardGatesInTheReservoir() {
        val inventory = ProgramCandidateInventory().collect(
            exercises = exercises,
            runtimeMetadataCatalog = catalog,
            availableEquipment = allEquipmentTokens()
        )
        val reservoirKeys = inventory.reservoir.candidates.map { it.exercise.stableKey }.toSet()
        val expectedKeys = SELECTED_MAIN_KEYS + setOf("ex_a345e30b")
        val missingKeys = expectedKeys - reservoirKeys

        assertTrue("missing from reservoir after hard gates: $missingKeys", missingKeys.isEmpty())
    }

    @Test
    fun scoreTraceShowsSelectedMainBoostAndCaptainChairPenaltyBeforeSelection() {
        val result = reproductionPlan()
        val traces = result.candidateTraces.flatMap(ProgramCandidateTrace::scoreAdjustments)

        assertTrue(
            "selected main candidates should reach scoring trace",
            traces.any { it.stableKey in SELECTED_MAIN_KEYS && it.selectedMainBoostApplied }
        )
        assertTrue(
            "captain chair should be visible in scoring trace with its penalty",
            traces.any { it.stableKey in CAPTAIN_CHAIR_KEYS && it.captainChairPenaltyApplied }
        )
    }

    @Test
    fun generatedBadmintonSupportPlanSelectsExactMainExercises() {
        val result = reproductionPlan()
        val selectedMainCount = result.items.count { it.stableKey in SELECTED_MAIN_KEYS }
        val selectedMainDistinct = result.items.map(ProgramSkeletonItem::stableKey).filter(SELECTED_MAIN_KEYS::contains).toSet()
        val weeksBelowTarget = result.items
            .groupBy(ProgramSkeletonItem::weekNumber)
            .filterValues { weekItems -> weekItems.count { it.stableKey in SELECTED_MAIN_KEYS } < 2 }
            .keys

        assertTrue(
            "45 minute badminton support plan should include exact selected-main exercises: ${result.items.map { it.stableKey }}",
            selectedMainCount >= 3
        )
        assertTrue("program should use at least 3 exact selected-main exercise types: $selectedMainDistinct",
            selectedMainDistinct.size >= 3)
        assertTrue("each week should keep at least 2 exact selected-main exercises; weak weeks=$weeksBelowTarget",
            weeksBelowTarget.isEmpty())
    }

    @Test
    fun captainChairDoesNotRepeatBeforeSelectedMainNeedsAreMet() {
        val result = reproductionPlan()
        val captainChairCount = result.items.count { it.stableKey in CAPTAIN_CHAIR_KEYS }
        val selectedMainCount = result.items.count { it.stableKey in SELECTED_MAIN_KEYS }

        assertTrue("selected-main exercises must be present before captain chair can repeat", selectedMainCount >= 3)
        assertTrue("captain chair should not repeat as a filler across the program", captainChairCount <= 1)
    }

    @Test
    fun excludedSelectedMainTargetsAreNotInsertedByReservationOrRepair() {
        val excluded = reproductionPlan(
            excludedExerciseStableKeys = SELECTED_MAIN_KEYS
        )

        assertEquals(0, excluded.items.count { it.stableKey in SELECTED_MAIN_KEYS })
    }

    private fun reproductionPlan(
        excludedExerciseStableKeys: Set<String> = emptySet()
    ): GeneratedProgramSkeleton = ProgramBuilder().build(
        request = ProgramSkeletonRequest(
            name = "selected main slot repair scenario",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 5,
            sessionMinutes = 45,
            availableEquipment = allEquipmentTokens(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.60,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = 4,
            excludedExerciseStableKeys = excludedExerciseStableKeys
        ),
        exercises = exercises,
        history = emptyList(),
        runtimeMetadataCatalog = catalog
    )

    private fun allEquipmentTokens(): Set<String> = exercises.flatMap { splitExerciseTokens(it.equipment) }.toSet()

    private fun splitExerciseTokens(value: String): Set<String> = value
        .split('|', ',', '/', ';', ' ')
        .map { it.trim().uppercase() }
        .filter(String::isNotBlank)
        .toSet()

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

    private companion object {
        val SELECTED_MAIN_KEYS = setOf(
            "barbell_back_squat",
            "barbell_deadlift",
            "pull_up",
            "ex_32219f7a",
            "ex_8e1b313e"
        )
        val CAPTAIN_CHAIR_KEYS = setOf("ex_a345e30b", "captain_chair_leg_raise")
    }
}
