package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun selectedMainReservationTargetsExactStableKeysOnly() {
        val slots = ProgramFoundationAnchorPolicy().reserveSlots(
            slots = listOf(
                TemplateExerciseSlot(null, ProgramExerciseRole.CORE),
                TemplateExerciseSlot(null, ProgramExerciseRole.PREHAB)
            ),
            request = request(),
            week = ProgramPeriodizationWeekPlan(
                weekIndex = 1,
                role = ProgramWeekRole.FOUNDATION_LOAD,
                dayProfiles = mapOf(1 to ProgramDayProfile.HARD_FOUNDATION)
            ),
            plannedSlot = PlannedSlot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD),
            availableSelectedMainStableKeys = setOf("barbell_back_squat", "goblet_squat"),
            generatedItems = emptyList()
        )

        assertEquals("barbell_back_squat", slots.first().selectedMainStableKey)
        assertEquals(ProgramSlotId.LOWER_SQUAT_PATTERN, slots.first().targetSlot)
        assertTrue(slots.first().required)
    }

    @Test
    fun selectedMainReservationDoesNotAllowBroadFamilySubstitutes() {
        val result = ProgramSlotCandidateQuery().query(
            inventory = ProgramCandidateInventoryResult(
                allActive = 2,
                programSelectable = 2,
                equipmentMatched = 2,
                notExcludedByUser = 2,
                candidates = listOf(
                    candidate(
                        id = 1,
                        stableKey = "barbell_back_squat",
                        name = "Back squat",
                        slot = ProgramSlotId.LOWER_SQUAT_PATTERN
                    ),
                    candidate(
                        id = 2,
                        stableKey = "goblet_squat",
                        name = "Goblet squat",
                        slot = ProgramSlotId.LOWER_SQUAT_PATTERN
                    )
                )
            ),
            selected = emptyList(),
            plannedSlot = PlannedSlot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD),
            templateSlot = TemplateExerciseSlot(
                targetSlot = ProgramSlotId.LOWER_SQUAT_PATTERN,
                role = ProgramExerciseRole.ANCHOR,
                required = true,
                selectedMainStableKey = "barbell_back_squat"
            ),
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

        assertEquals(listOf("barbell_back_squat"), result.scored.map { it.first.exercise.stableKey })
        assertFalse(result.scored.any { it.first.exercise.stableKey == "goblet_squat" })
        assertEquals("barbell_back_squat", result.trace.selectedMainReservationStableKey)
    }

    @Test
    fun captainChairCanBeBlockedBeforeSelectedMainRequirementsAreMet() {
        val result = ProgramSlotCandidateQuery().query(
            inventory = ProgramCandidateInventoryResult(
                allActive = 2,
                programSelectable = 2,
                equipmentMatched = 2,
                notExcludedByUser = 2,
                candidates = listOf(
                    candidate(
                        id = 10,
                        stableKey = "ex_a345e30b",
                        name = "Captain chair leg raise",
                        slot = ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY
                    ),
                    candidate(
                        id = 11,
                        stableKey = "dead_bug",
                        name = "Dead bug",
                        slot = ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY
                    )
                )
            ),
            selected = emptyList(),
            plannedSlot = PlannedSlot(7, ProgramTrainingSlot.RECOVERY_WEAKPOINT, ProgramDayIntensity.LIGHT),
            templateSlot = TemplateExerciseSlot(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE),
            week = week(),
            weekNumber = 1,
            absoluteDay = 7,
            repeatAllowed = { true },
            fatigueAllowed = { true },
            sessionAllowed = { true },
            captainChairAllowed = { candidate -> candidate.exercise.stableKey != "ex_a345e30b" },
            score = { 1.0 },
            selectionPoolSize = 8,
            selectedCount = 0
        )

        assertEquals(listOf("dead_bug"), result.scored.map { it.first.exercise.stableKey })
        assertEquals(1, result.trace.captainChairBlockedCount)
        assertEquals("CAPTAIN_CHAIR_BLOCKED_UNTIL_SELECTED_MAIN_READY", result.trace.captainChairBlockReason)
    }

    @Test
    fun repairReopensCaptainChairSlotForExactSelectedMainExercise() {
        val repair = ProgramIssueDrivenRerankPolicy().repair(
            skeleton = captainChairOnlySkeleton(),
            evaluation = evaluationWith(ProgramEvaluationIssueType.SELECTED_MAIN_MISSING),
            reservoir = ProgramCandidateReservoir(
                listOf(
                    candidate(
                        id = 20,
                        stableKey = "barbell_back_squat",
                        name = "Back squat",
                        slot = ProgramSlotId.LOWER_SQUAT_PATTERN
                    )
                )
            )
        )

        assertTrue("selected-main issue should reopen a weak filler slot",
            "REOPEN_FILLER_SLOT_FOR_SELECTED_MAIN" in repair.actions)
        assertTrue("exact selected-main candidate should be inserted",
            repair.skeleton.items.any { it.stableKey == "barbell_back_squat" })
        assertFalse("captain chair should be removed from the repaired slot",
            repair.skeleton.items.all { it.stableKey in CAPTAIN_CHAIR_KEYS })
    }

    @Test
    fun selectedMainAvailableButMissingCapsEvaluationScore() {
        val skeleton = captainChairOnlySkeleton().copy(
            candidateTraces = listOf(
                ProgramCandidateTrace(
                    weekNumber = 1,
                    dayOfWeek = 1,
                    requestedTemplateSlot = ProgramSlotId.LOWER_SQUAT_PATTERN.name,
                    role = ProgramExerciseRole.ANCHOR.name,
                    allActive = 2,
                    programSelectable = 2,
                    equipmentMatched = 2,
                    notExcludedByUser = 2,
                    capabilityMatched = 1,
                    repeatAllowed = 1,
                    fatigueAllowed = 1,
                    templateAllowed = 1,
                    sessionAllowed = 1,
                    scored = 1,
                    selectionPool = 1,
                    selected = 0,
                    scoreAdjustments = listOf(
                        ProgramCandidateScoreTrace(
                            exerciseName = "Back squat",
                            stableKey = "barbell_back_squat",
                            baseScore = 100.0,
                            contextRerankScore = 0.0,
                            selectedMainBoostApplied = true,
                            captainChairPenaltyApplied = false,
                            finalScore = 140.0
                        )
                    )
                )
            )
        )

        val evaluation = ProgramEvaluationPolicy().evaluate(skeleton)

        assertTrue(evaluation.issues.any { it.type == ProgramEvaluationIssueType.SELECTED_MAIN_MISSING })
        assertTrue("selected-main missing must cap score strongly, score=${evaluation.overallScore}",
            evaluation.overallScore <= 60)
    }

    private fun reproductionPlan(
        excludedExerciseStableKeys: Set<String> = emptySet()
    ): GeneratedProgramSkeleton = ProgramBuilder().build(
        request = request(
            excludedExerciseStableKeys = excludedExerciseStableKeys
        ),
        exercises = exercises,
        history = emptyList(),
        runtimeMetadataCatalog = catalog
    )

    private fun request(
        excludedExerciseStableKeys: Set<String> = emptySet()
    ): ProgramSkeletonRequest = ProgramSkeletonRequest(
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

    private fun week(): ProgramWeekPlan = ProgramWeekPlan(
        weekIndex = 1,
        weekType = ProgramWeekType.BUILD.name,
        volumeMultiplier = 1.0,
        intensityMultiplier = 1.0,
        heavyExposureLimit = 2,
        lowerBodyFatigueLimit = 8.0,
        axialLoadLimit = 2,
        plyometricLimit = 1,
        deloadFlag = false
    )

    private fun candidate(
        id: Long,
        stableKey: String,
        name: String,
        slot: ProgramSlotId
    ): ProgramCandidate = ProgramCandidate(
        exercise = Exercise(
            id = id,
            name = name,
            category = "strength",
            stableKey = stableKey,
            equipment = "BARBELL",
            planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
        ),
        metadata = RuntimeExerciseMetadataDefaults.forExercise(
            Exercise(id = id, name = name, category = "strength", stableKey = stableKey)
        ).copy(
            stableKey = stableKey,
            exerciseName = name,
            planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
        ),
        canonical = true,
        slotCapabilities = SlotCapabilityProfile(
            primary = setOf(slot),
            secondary = emptySet(),
            weakMatches = emptySet(),
            source = SlotCapabilitySource.RUNTIME_METADATA,
            confidence = SlotCapabilityConfidence.HIGH
        )
    )

    private fun captainChairOnlySkeleton(): GeneratedProgramSkeleton {
        val request = request()
        return GeneratedProgramSkeleton(
            suggestedName = request.name,
            durationDays = 28,
            request = request,
            periodizationType = ProgramPeriodizationType.BADMINTON_WAVE,
            weekPlans = listOf(week()),
            items = listOf(
                ProgramSkeletonItem(
                    localId = "1-1-1",
                    weekNumber = 1,
                    dayOfWeek = 1,
                    orderIndex = 1,
                    exerciseId = 100,
                    exerciseName = "Captain chair leg raise",
                    category = "strength",
                    restSeconds = 60,
                    prescription = "2x12",
                    setCount = 2,
                    reps = 12,
                    weightKg = 0.0,
                    seconds = 0,
                    selectionReason = "",
                    weightSource = "",
                    stableKey = "ex_a345e30b",
                    selectionRole = ProgramExerciseRole.CORE.name,
                    primarySlotCapabilities = listOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name),
                    requestedTemplateSlot = ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name
                )
            )
        )
    }

    private fun evaluationWith(issueType: ProgramEvaluationIssueType): ProgramEvaluation = ProgramEvaluation(
        overallScore = 55,
        weeklyScores = emptyList(),
        fatigueScore = 70,
        strengthDistributionScore = 45,
        badmintonTransferScore = 70,
        densityScore = 70,
        intensityDistributionScore = 70,
        equipmentUtilizationScore = 45,
        issues = listOf(ProgramEvaluationIssue(issueType, ProgramEvaluationIssueSeverity.SEVERE, "fixture issue")),
        suggestions = emptyList()
    )

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
