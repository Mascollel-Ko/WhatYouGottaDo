package com.training.trackplanner.data

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    @Test
    fun candidateTraceIncludesScoreAdjustmentDetails() {
        val result = build(Scenario("score trace 3d 4w", 3, 4, 60, 0.70, 6))
        val traces = result.candidateTraces.flatMap(ProgramCandidateTrace::scoreAdjustments)

        assertTrue(traces.any { it.selectionWindowIncluded })
        assertTrue(traces.any { it.selected })
        assertTrue(traces.all { it.exerciseName.isNotBlank() })
        assertTrue(traces.all { it.stableKey.isNotBlank() })
        assertTrue(traces.all { !it.finalScore.isNaN() && !it.finalScore.isInfinite() })
    }

    @Test
    fun slotQueryUsesAdaptivePoolInsteadOfFixedTopThree() {
        val inventory = ProgramCandidateInventoryResult(
            allActive = 10,
            programSelectable = 10,
            equipmentMatched = 10,
            notExcludedByUser = 10,
            candidates = (1..10).map { id ->
                candidate(
                    id = id,
                    slotCapabilities = SlotCapabilityProfile(
                        primary = setOf(ProgramSlotId.UPPER_PULL_ANCHOR),
                        secondary = emptySet(),
                        weakMatches = emptySet(),
                        source = SlotCapabilitySource.RUNTIME_METADATA,
                        confidence = SlotCapabilityConfidence.HIGH
                    )
                )
            }
        )
        val result = ProgramSlotCandidateQuery().query(
            inventory = inventory,
            selected = emptyList(),
            plannedSlot = plannedSlot(),
            templateSlot = TemplateExerciseSlot(ProgramSlotId.UPPER_PULL_ANCHOR, ProgramExerciseRole.ANCHOR, required = true),
            week = week(),
            weekNumber = 1,
            absoluteDay = 1,
            repeatAllowed = { true },
            fatigueAllowed = { true },
            sessionAllowed = { true },
            score = { candidate -> 100.0 - candidate.exercise.id },
            selectionPoolSize = 8,
            selectedCount = 0
        )

        assertEquals(8, result.scored.size)
        assertTrue(result.trace.selectionPool > 3)
    }

    @Test
    fun requiredSlotCanUseWeakFallbackInsteadOfBecomingUnfilled() {
        val inventory = ProgramCandidateInventoryResult(
            allActive = 1,
            programSelectable = 1,
            equipmentMatched = 1,
            notExcludedByUser = 1,
            candidates = listOf(
                candidate(
                    id = 1,
                    slotCapabilities = SlotCapabilityProfile(
                        primary = emptySet(),
                        secondary = emptySet(),
                        weakMatches = setOf(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN),
                        source = SlotCapabilitySource.RUNTIME_METADATA,
                        confidence = SlotCapabilityConfidence.MODERATE
                    )
                )
            )
        )
        val result = ProgramSlotCandidateQuery().query(
            inventory = inventory,
            selected = emptyList(),
            plannedSlot = plannedSlot(),
            templateSlot = TemplateExerciseSlot(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN, ProgramExerciseRole.SUPPORT, required = true),
            week = week(),
            weekNumber = 1,
            absoluteDay = 1,
            repeatAllowed = { true },
            fatigueAllowed = { true },
            sessionAllowed = { true },
            score = { 1.0 },
            selectionPoolSize = 8,
            selectedCount = 0
        )

        assertEquals(1, result.scored.size)
        assertTrue(result.trace.warnings.any { it.startsWith("TEMPLATE_REQUIRED_SLOT_FALLBACK_USED") })
    }

    @Test
    fun tightSessionBudgetTrimsSlotsWithoutDroppingWholeSessions() {
        val result = build(Scenario("tight budget 5d 4w", 5, 4, 15, 0.70, 6))
        val sessions = result.items.groupBy { it.weekNumber to it.dayOfWeek }

        assertTrue(sessions.isNotEmpty())
        assertTrue(sessions.values.all { it.isNotEmpty() })
        assertTrue(result.candidateTraces.any { "PROGRAM_SLOT_TIME_BUDGET_TRIMMED" in it.warnings })
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

    private fun candidate(id: Int, slotCapabilities: SlotCapabilityProfile): ProgramCandidate = ProgramCandidate(
        exercise = Exercise(
            id = id.toLong(),
            name = "Candidate $id",
            category = "strength",
            stableKey = "candidate_$id"
        ),
        metadata = null,
        canonical = false,
        slotCapabilities = slotCapabilities
    )

    private fun plannedSlot(): PlannedSlot = PlannedSlot(
        dayOfWeek = 1,
        slot = ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
        intensity = ProgramDayIntensity.MODERATE
    )

    private fun week(): ProgramWeekPlan = ProgramWeekPlan(
        weekIndex = 1,
        weekType = ProgramWeekType.ADAPT.name,
        volumeMultiplier = 1.0,
        intensityMultiplier = 1.0,
        heavyExposureLimit = 1,
        lowerBodyFatigueLimit = 1.0,
        axialLoadLimit = 1,
        plyometricLimit = 1,
        deloadFlag = false
    )

    private data class Scenario(
        val label: String,
        val days: Int,
        val weeks: Int,
        val minutes: Int,
        val ratio: Double,
        val minDistinct: Int
    )
}
