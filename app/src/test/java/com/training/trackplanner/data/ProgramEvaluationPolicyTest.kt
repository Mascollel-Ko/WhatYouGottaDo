package com.training.trackplanner.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgramEvaluationPolicyTest {
    private val exercises = loadSeedExercises()
    private val catalog = RuntimeExerciseMetadataCatalog.of(
        ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
    )
    private val policy = ProgramEvaluationPolicy()

    @Test
    fun evaluationScoresGeneratedPlanAboveCollapsedVariant() {
        val generated = reproductionPlan()
        val collapsed = collapsedTransferOnly(generated)

        val good = policy.evaluate(generated)
        val bad = policy.evaluate(collapsed)

        assertTrue("fixed generated plan should score above collapsed variant", good.overallScore > bad.overallScore)
        assertTrue("collapsed plan should report low density",
            bad.issues.any { it.type == ProgramEvaluationIssueType.LOW_SESSION_DENSITY })
        assertTrue("collapsed plan should report low strength anchors",
            bad.issues.any { it.type == ProgramEvaluationIssueType.LOW_STRENGTH_ANCHOR })
        assertTrue("collapsed plan should report repeated transfer target",
            bad.issues.any { it.type == ProgramEvaluationIssueType.TRANSFER_GOAL_OVERUSE })
    }

    @Test
    fun weeklyImbalanceCanRecoverAtProgramLevel() {
        val generated = reproductionPlan()
        val firstWeekCollapsed = generated.copy(
            items = generated.items.map { item ->
                if (item.weekNumber == 1) collapseItem(item) else item
            }
        )

        val evaluation = policy.evaluate(firstWeekCollapsed)

        assertTrue("program-level recovery should not be scored as hard failure", evaluation.overallScore >= 65)
        assertTrue("recovered weekly imbalance should be noted",
            evaluation.issues.any { it.type == ProgramEvaluationIssueType.WEEKLY_BALANCE_RECOVERS_LATER })
    }

    @Test
    fun repeatedCoreAndIdenticalWeeksCannotScorePerfect() {
        val generated = reproductionPlan()
        val weekOne = generated.items.filter { it.weekNumber == 1 }
        val repeatedCoreWeeks = generated.copy(
            items = generated.weekPlans.flatMap { week ->
                weekOne.map { item ->
                    item.copy(
                        weekNumber = week.weekIndex,
                        exerciseName = "Captain chair leg raise",
                        selectionRole = ProgramExerciseRole.CORE.name,
                        stableKey = "captain_chair_leg_raise",
                        redundancyGroup = "CORE_FLEXION",
                        movementFamily = "CORE_FLEXION_ANTERIOR_CORE",
                        movementSubtype = "CAPTAINS_CHAIR_LEG_RAISE",
                        requestedTemplateSlot = ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name,
                        primarySlotCapabilities = listOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name),
                        secondarySlotCapabilities = emptyList()
                    )
                }
            }
        )

        val evaluation = policy.evaluate(repeatedCoreWeeks)

        assertTrue("repeated core filler should cap overall score", evaluation.overallScore <= 90)
        assertTrue("repeated core filler should be detected",
            evaluation.issues.any { it.type == ProgramEvaluationIssueType.TOO_MUCH_CORE_REPETITION })
        assertTrue("identical week profiles should be detected",
            evaluation.issues.any { it.type == ProgramEvaluationIssueType.NO_WEEK_VARIATION })
    }

    @Test
    fun fatigueClusterProducesIssue() {
        val generated = reproductionPlan()
        val clustered = generated.copy(
            items = generated.items.map { item ->
                if (item.weekNumber == 1 && item.dayOfWeek in setOf(1, 2)) {
                    item.copy(
                        dayIntensity = ProgramDayIntensity.HARD.name,
                        trainingSlot = ProgramTrainingSlot.LOWER_STRENGTH_HEAVY.name,
                        requestedTemplateSlot = ProgramSlotId.LOWER_SQUAT_PATTERN.name,
                        stressMagnitudeHint = "VERY_HIGH"
                    )
                } else {
                    item
                }
            }
        )

        val evaluation = policy.evaluate(clustered)

        assertTrue("high lower-body cluster should be detected",
            evaluation.issues.any { it.type == ProgramEvaluationIssueType.HIGH_LOWER_BODY_FATIGUE_CLUSTER })
    }

    private fun collapsedTransferOnly(source: GeneratedProgramSkeleton): GeneratedProgramSkeleton =
        source.copy(
            items = source.items.groupBy { it.weekNumber to it.dayOfWeek }.values.flatMap { rows ->
                rows.take(3).map(::collapseItem)
            }
        )

    private fun collapseItem(item: ProgramSkeletonItem): ProgramSkeletonItem =
        item.copy(
            exerciseName = "6코너 섀도우 풋워크",
            selectionRole = ProgramExerciseRole.TRANSFER.name,
            stableKey = "same_transfer",
            redundancyGroup = "SAME_TRANSFER",
            movementFamily = "SAME_TRANSFER",
            requestedTemplateSlot = ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name,
            primarySlotCapabilities = listOf(ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name),
            secondarySlotCapabilities = emptyList(),
            badmintonTransferLevel = "DIRECT"
        )

    private fun reproductionPlan(): GeneratedProgramSkeleton = ProgramBuilder().build(
        request = ProgramSkeletonRequest(
            name = "badminton support quality scenario",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 5,
            sessionMinutes = 45,
            availableEquipment = allEquipmentTokens(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.60,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = 4
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
}
