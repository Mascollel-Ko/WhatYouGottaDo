package com.training.trackplanner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File

class ProgramBuilderQualityV041104Test {
    private val exercises = loadSeedExercises()
    private val exerciseById = exercises.associateBy(Exercise::id)
    private val catalog = RuntimeExerciseMetadataCatalog.of(
        ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
    )

    @Ignore("Reproduces the v0.4.1.10.3 all-equipment loaded-strength underuse gap; enabled by the loaded-strength fix.")
    @Test
    fun badmintonSupportFiveDayFortyFiveMinutePlanKeepsLoadedStrengthAndUsefulDensity() {
        val result = reproductionPlan()
        val sessions = result.items.groupBy { it.weekNumber to it.dayOfWeek }
        val loadedItems = result.items.filter(::isLoadedStrengthItem)
        val firstWeek = result.items.filter { it.weekNumber == 1 }
        val firstWeekSlots = firstWeek.map(ProgramSkeletonItem::requestedTemplateSlot).toSet()
        val stableKeyRepeat = result.items
            .groupBy(ProgramSkeletonItem::stableKey)
            .filterKeys(String::isNotBlank)
            .mapValues { (_, rows) -> rows.map { it.weekNumber to it.dayOfWeek }.distinct().size }

        assertTrue("expected 4 weeks x 5 sessions", sessions.size >= 20)
        assertTrue("45 minute sessions should usually have at least four exercises", sessions.values.count { it.size >= 4 } >= 16)
        assertTrue("loaded barbell/dumbbell/machine/cable strength work should survive all-equipment selection", loadedItems.size >= 8)
        assertTrue("week 1 needs a lower strength anchor", firstWeekSlots.any { it in LOWER_ANCHOR_SLOTS })
        assertTrue("week 1 needs an upper strength anchor/support", firstWeekSlots.any { it in UPPER_ANCHOR_SLOTS })
        assertTrue("week 1 needs core or trunk stability", ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name in firstWeekSlots)
        assertFalse("non-anchor exercise repeated too often", stableKeyRepeat.any { (key, count) ->
            count > 6 && result.items.none { it.stableKey == key && it.selectionRole == ProgramExerciseRole.ANCHOR.name }
        })
        assertTrue("plan should keep at least one hard day", result.items.any { it.dayIntensity == ProgramDayIntensity.HARD.name })
        assertTrue("plan should keep at least one light day", result.items.any { it.dayIntensity == ProgramDayIntensity.LIGHT.name })
        assertTrue("6-corner shadow footwork remains allowed when selected", result.items.none {
            it.exerciseName.contains("6코너", ignoreCase = true) && it.directSportSession
        })
    }

    @Test
    fun simpleQualityAuditAllowsRecoveredWeeklyImbalanceButFlagsRepeatedProgramWideImbalance() {
        val result = reproductionPlan()
        val recovered = simpleAudit(result)
        val repeatedBad = simpleAudit(
            result.copy(items = result.items.map { item ->
                item.copy(
                    selectionRole = ProgramExerciseRole.TRANSFER.name,
                    stableKey = "same_transfer",
                    redundancyGroup = "SAME_TRANSFER"
                )
            })
        )

        assertTrue("normal generated plan should pass soft quality audit", recovered.score >= 70)
        assertTrue("repeated transfer imbalance should be detected", "PROGRAM_WIDE_TRANSFER_REPEAT" in repeatedBad.issues)
    }

    private fun reproductionPlan(): GeneratedProgramSkeleton = ProgramBuilder().build(
        request = ProgramSkeletonRequest(
            name = "배드민턴 지원 웨이트",
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

    private fun simpleAudit(result: GeneratedProgramSkeleton): SimpleQualityAudit {
        val transferRepeats = result.items
            .filter { it.selectionRole == ProgramExerciseRole.TRANSFER.name }
            .groupBy { it.redundancyGroup.ifBlank { it.stableKey } }
            .filterKeys { it.isNotBlank() && it != "NOT_APPLICABLE" }
            .count { (_, rows) -> rows.map(ProgramSkeletonItem::weekNumber).distinct().size >= result.weekPlans.size }
        val lowDensitySessions = result.items.groupBy { it.weekNumber to it.dayOfWeek }.count { (_, rows) -> rows.size <= 3 }
        val issues = buildList {
            if (transferRepeats > 0) add("PROGRAM_WIDE_TRANSFER_REPEAT")
            if (lowDensitySessions > result.request.durationWeeks) add("LOW_SESSION_DENSITY")
        }
        return SimpleQualityAudit(score = (100 - transferRepeats * 20 - lowDensitySessions * 4).coerceIn(0, 100), issues = issues)
    }

    private fun isLoadedStrengthItem(item: ProgramSkeletonItem): Boolean {
        val exercise = exerciseById[item.exerciseId] ?: return false
        val equipment = splitExerciseTokens(exercise.equipment)
        val loaded = equipment.any { it in LOADED_EQUIPMENT }
        val strengthSlot = item.requestedTemplateSlot in STRENGTH_SLOTS ||
            item.primarySlotCapabilities.any(STRENGTH_SLOTS::contains) ||
            item.secondarySlotCapabilities.any(STRENGTH_SLOTS::contains)
        return loaded && strengthSlot && item.badmintonTransferLevel != "DIRECT"
    }

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

    private data class SimpleQualityAudit(val score: Int, val issues: List<String>)

    private companion object {
        val LOADED_EQUIPMENT = setOf(
            "BARBELL",
            "DUMBBELL",
            "MACHINE",
            "CABLE",
            "SMITH_MACHINE",
            "LEG_PRESS_MACHINE",
            "HACK_SQUAT_MACHINE",
            "LEG_CURL_MACHINE",
            "LEG_EXTENSION_MACHINE"
        )
        val LOWER_ANCHOR_SLOTS = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL.name
        )
        val UPPER_ANCHOR_SLOTS = setOf(
            ProgramSlotId.UPPER_PULL_ANCHOR.name,
            ProgramSlotId.UPPER_PUSH_SUPPORT.name,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT.name
        )
        val STRENGTH_SLOTS = LOWER_ANCHOR_SLOTS + UPPER_ANCHOR_SLOTS + setOf(
            ProgramSlotId.CALF_ANKLE_CAPACITY.name,
            ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT.name,
            ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT.name,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name
        )
    }
}
