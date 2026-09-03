package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PersonalizedPlannerParityTest {
    private val styleAnalyzer = StrengthProgrammingStyleAnalyzer()

    @Test
    fun `v08 parity matrix runs all 29 named personas from raw history through final skeleton`() {
        val names = listOf(
            "01_bodybuilding_badminton", "02_badminton_machine", "03_novice_machine", "04_no_lower_non_badminton",
            "05_no_arms_calves_hypertrophy", "06_arms_calves_only", "07_no_core_non_badminton", "08_upper_only_general_fitness",
            "09_machine_general_modality_challenge", "10_machine_general_strong_barbell_challenge", "11_badminton_onramp_from_zero",
            "12_badminton_explicitly_disabled", "13_lower_isolation_only_general", "14_lower_isolation_only_badminton",
            "15_lower_isolation_only_hypertrophy", "16_lower_isolation_strong_direct", "17_badminton_foundation_building",
            "18_hypertrophy_strength_unresolved", "19_hypertrophy_strength_mixed", "20_hypertrophy_only_explicit",
            "21_stable_five_week_horizon", "22_sparse_two_week_horizon", "23_bodybuilding_top_set_style",
            "24_top_set_backoff_strength", "25_straight_5x5_style", "26_madcow_like_style", "27_dup_like_style",
            "28_explicit_madcow_incompatible_badminton", "29_hlm_like_style"
        )
        assertEquals(29, names.size)
        assertEquals(29, names.distinct().size)
        names.forEachIndexed { index, name ->
            val snapshot = rawSnapshotFor(name, index)
            val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())
            val gaps = AdaptationGapAnalyzer().analyze(snapshot, state)
            val intent = BlockIntentPlanner().decide(state, gaps)
            val duration = PlanningHorizonPlanner().choose(state, gaps)
            val days = WeeklyDosePlanner().chooseDays(state, state.anchors.size + gaps.size)
            val request = ProgramSkeletonRequest(name, state.programGoal, days, 90, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, duration)
            val first = PersonalizedProgramBuilder().build(snapshot, state, gaps, intent, duration, request, PersonalizedPlanningAnswers(), null)
            val second = PersonalizedProgramBuilder().build(snapshot, state, gaps, intent, duration, request, PersonalizedPlanningAnswers(), null)
            assertEquals(name, personalizedProgramFingerprint(first.request, first.items), personalizedProgramFingerprint(second.request, second.items))
            assertEquals(name, duration, first.request.durationWeeks)
            assertEquals(name, days, first.request.weeklyTrainingDays)
            assertEquals(name, state.programGoal, first.request.goal)
            assertTrue(name, first.items.isNotEmpty())
            assertTrue(name, first.items.none { it.exerciseStableKey in setOf("ex_ae9ecdbc", "ex_badminton_lesson") })
            assertTrue(name, "HISTORY_CUTOFF_ENFORCED" in first.personalizedDecision!!.reasonCodes)
            assertScenarioSemantics(name, snapshot, state, gaps, first)
        }
    }

    @Test
    fun `five straight sets of six are not classified as 5x5`() {
        val rows = (1..5).map { set(0, "squat", 100.0, 6, it) }
        assertFalse(styleAnalyzer.classifySession(rows) == StrengthProgrammingStyle.STRAIGHT_5X5)
    }

    @Test
    fun `exact straight 5x5 is classified`() {
        val rows = (1..5).map { set(0, "squat", 100.0, 5, it) }
        assertEquals(StrengthProgrammingStyle.STRAIGHT_5X5, styleAnalyzer.classifySession(rows))
    }

    @Test
    fun `sparse and unresolved horizons match v08 rules`() {
        assertEquals(2, PlanningHorizonPlanner().choose(state(historyDays = 12), emptyList()))
        assertEquals(3, PlanningHorizonPlanner().choose(state(historyDays = 56, strengthIntent = StrengthIntent.UNRESOLVED), emptyList()))
        assertEquals(5, PlanningHorizonPlanner().choose(state(historyDays = 48), emptyList()))
    }

    @Test
    fun `generic badminton session is cost context and never structured objective stimulus`() {
        val exercise = Exercise(stableKey = "ex_ae9ecdbc", name = "배드민턴", category = "스포츠", activityKind = "SPORT_SESSION", planningEligibility = "NOT_PROGRAM_SELECTABLE")
        val snapshot = snapshot(listOf(set(0, exercise.stableKey, seconds = 3600)), listOf(exercise))
        assertTrue(snapshot.isSportSession(exercise.stableKey))
        assertFalse(snapshot.isStructuredBadminton(exercise.stableKey))
        assertFalse(AdaptationGapAnalyzer().analyze(snapshot, state()).any { it.code.startsWith("BADMINTON") })
    }

    @Test
    fun `supportive badminton relation remains resistance and nine objective state stays complete`() {
        val snapshot = rawSnapshotFor("17_badminton_foundation_building", 17)
        val state = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())

        assertEquals(PlannedActivityKind.RESISTANCE, snapshot.activityKind("squat"))
        assertEquals(PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, snapshot.activityKind("badminton_drill"))
        assertEquals(BadmintonObjective.entries.map(BadmintonObjective::name).toSet(), state.objectiveExposure.keys)
        assertEquals(
            BadmintonObjective.entries.map(BadmintonObjective::name).toSet(),
            state.objectiveExposure.filterValues { it > 0.0 }.keys + state.objectiveDevelopmentalGaps + state.objectiveDropGaps
        )
    }

    @Test
    fun `elevated recovery and tissue signals shorten horizon and reduce anchor load`() {
        val ordinary = rawSnapshotFor("21_stable_five_week_horizon", 21)
        val constrained = ordinary.copy(recoverySignals = PlanningRecoverySignals(
            readinessStatus = "LIMITED",
            readinessConfidence = "HIGH",
            overallFatigueIndex = 82,
            tissueStatus = "VERY_HIGH",
            tissueRestrictedStableKeys = setOf("squat"),
            sourceCodes = setOf("CANONICAL_OFI", "TODAY_READINESS", "TISSUE_RCV")
        ))
        fun plan(source: PlanningHistorySnapshot): com.training.trackplanner.data.GeneratedProgramSkeleton {
            val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
            val gaps = AdaptationGapAnalyzer().analyze(source, state)
            val horizon = PlanningHorizonPlanner().choose(state, gaps)
            val intent = BlockIntentPlanner().decide(state, gaps)
            val request = ProgramSkeletonRequest("recovery", state.programGoal, 3, 90, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, horizon)
            return PersonalizedProgramBuilder().build(source, state, gaps, intent, horizon, request, PersonalizedPlanningAnswers(), null)
        }

        val ordinaryPlan = plan(ordinary)
        val constrainedPlan = plan(constrained)
        assertTrue(constrainedPlan.request.durationWeeks < ordinaryPlan.request.durationWeeks)
        assertTrue(constrainedPlan.items.filter { it.exerciseStableKey == "squat" }.all { it.weightSource.endsWith("REDUCE") })
        assertTrue(constrainedPlan.personalizedDecision!!.recoverySignalCodes.containsAll(constrained.recoverySignals.sourceCodes))
    }

    @Test
    fun `novel exercise prescription never invents a starting load`() {
        val incumbent = Exercise(stableKey = "incumbent", name = "기존 운동", category = "근력운동")
        val novel = Exercise(stableKey = "novel", name = "새 운동", category = "근력운동")
        val history = listOf(set(0, incumbent.stableKey, weight = 50.0, reps = 8))
        val source = snapshot(history, listOf(incumbent, novel))

        val prescription = PersonalizedPrescriptionPlanner().prescribe(
            source,
            PlannedExercise(novel.stableKey, "COVERAGE_LOWER_KNEE", "fixture", 80),
            StrengthProgrammingStyle.NONE,
            week = 1
        )

        assertEquals("PROVISIONAL_RPE_NO_INVENTED_LOAD", prescription.weightSource)
        assertTrue(prescription.sets.all { it.weightKg == 0.0 })
    }

    @Test
    fun `future and unconfirmed leakage is excluded by snapshot contract`() {
        val cutoff = LocalDate.of(2026, 1, 10)
        val exercise = Exercise(stableKey = "x", name = "스쿼트", category = "근력운동")
        fun record(id: Long, date: String, confirmed: Boolean) = WorkoutEntryWithSets(
            WorkoutEntry(id = id, date = date, exerciseStableKey = "x", exerciseName = "스쿼트", category = "근력운동"),
            listOf(WorkoutSet(entryId = id, setIndex = 1, reps = 5, weightKg = 100.0, confirmed = confirmed))
        )
        val snapshot = PlanningHistorySnapshotBuilder().build(
            cutoff = cutoff,
            history = listOf(record(1, "2026-01-09", true), record(2, "2026-01-09", false), record(3, "2026-01-11", true)),
            exercises = listOf(exercise),
            metadata = mapOf("x" to RuntimeExerciseMetadataDefaults.forExercise(exercise)),
            badmintonCatalog = CanonicalBadmintonObjectiveCatalog.EMPTY,
            profile = null,
            preferences = PersonalizedPlanningPreferences()
        )

        assertEquals(1, snapshot.allConfirmedSets.size)
        assertEquals(LocalDate.of(2026, 1, 9), snapshot.allConfirmedSets.single().date)
    }

    private fun rawSnapshotFor(name: String, index: Int): PlanningHistorySnapshot {
        val cutoff = LocalDate.of(2026, 1, 10)
        val definitions = listOf(
            Triple("squat", "스쿼트", "MAIN_LOWER_STRENGTH"),
            Triple("hinge", "데드리프트", "MAIN_HINGE_STRENGTH"),
            Triple("press", "벤치프레스", "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY"),
            Triple("row", "로우", "HORIZONTAL_PULL_STRENGTH"),
            Triple("vertical_press", "숄더 프레스", "OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY"),
            Triple("core", "데드버그", "CORE_STABILITY_ACCESSORY"),
            Triple("biceps", "바벨 컬", "BICEPS_ACCESSORY"),
            Triple("triceps", "케이블 푸시다운", "TRICEPS_ACCESSORY"),
            Triple("calf", "카프 레이즈", "ANKLE_CALF_SUPPORT"),
            Triple("leg_extension", "레그 익스텐션", "QUAD_ISOLATION_ACCESSORY"),
            Triple("badminton_drill", "배드민턴 풋워크", "BADMINTON_FOOTWORK")
        )
        val exercises = definitions.map { (key, label, _) ->
            val equipment = when {
                name == "10_machine_general_strong_barbell_challenge" && key == "squat" -> "BARBELL"
                "machine" in name -> "MACHINE"
                else -> "BARBELL"
            }
            Exercise(stableKey = key, name = label, category = if (key == "badminton_drill") "배드민턴" else "근력운동", equipmentTags = equipment, activityKind = "EXERCISE", planningEligibility = "PROGRAM_SELECTABLE")
        }
        val metadata = definitions.associate { (key, label, slot) ->
            key to RuntimeExerciseMetadataDefaults.forIdentity(key, label).copy(
                activityKind = "EXERCISE",
                planningEligibility = "PROGRAM_SELECTABLE",
                programSlot = slot,
                progressMetricType = "LOAD_REPS",
                analysisEligibility = MetadataTokenField.parse(if (key == "badminton_drill") "FATIGUE|BADMINTON_TRANSFER" else "FATIGUE|STRENGTH_PROGRESS|HYPERTROPHY_VOLUME"),
                badmintonTransferLevel = if (key == "badminton_drill") "DIRECT" else if (key == "squat") "SUPPORTIVE" else "NONE",
                sourceConfidenceLevel = "HIGH",
                finalSourceStatus = "SOURCE_ACCEPTED"
            )
        }
        val historyKeys = when (name) {
            "01_bodybuilding_badminton" -> setOf("row", "vertical_press", "core", "biceps", "triceps", "calf", "badminton_drill")
            "04_no_lower_non_badminton" -> setOf("press", "row", "vertical_press", "core", "biceps", "triceps")
            "05_no_arms_calves_hypertrophy" -> setOf("squat", "hinge", "press", "row", "vertical_press", "core")
            "06_arms_calves_only" -> setOf("biceps", "triceps", "calf")
            "07_no_core_non_badminton" -> setOf("squat", "hinge", "press", "row", "vertical_press", "biceps")
            "08_upper_only_general_fitness" -> setOf("press", "row", "vertical_press", "biceps", "triceps")
            "13_lower_isolation_only_general", "14_lower_isolation_only_badminton", "15_lower_isolation_only_hypertrophy" -> setOf("leg_extension", "press", "row", "core")
            "16_lower_isolation_strong_direct" -> setOf("leg_extension", "squat", "press", "row", "core")
            "17_badminton_foundation_building" -> setOf("squat", "hinge", "press", "row", "core", "badminton_drill")
            else -> setOf("squat", "hinge", "press", "row", "vertical_press", "core") + if (name in setOf("02_badminton_machine", "28_explicit_madcow_incompatible_badminton")) setOf("badminton_drill") else emptySet()
        }
        val weekCount = when (name) {
            "21_stable_five_week_horizon" -> 7
            "22_sparse_two_week_horizon" -> 2
            else -> 8
        }
        var entryId = 1L
        val history = buildList {
            repeat(weekCount) { week ->
                definitions.filter { it.first in historyKeys }.forEachIndexed { exerciseIndex, (key, label, _) ->
                    if (key == "squat" && name in setOf("26_madcow_like_style", "27_dup_like_style", "29_hlm_like_style")) {
                        val weekStart = cutoff.minusWeeks((weekCount - week - 1).toLong()).with(java.time.DayOfWeek.MONDAY)
                        val sessions = when (name) {
                            "26_madcow_like_style" -> listOf(
                                listOf(55.0 to 5, 70.0 to 5, 85.0 to 5),
                                listOf(50.0 to 5, 65.0 to 5, 75.0 to 5),
                                listOf(60.0 to 5, 75.0 to 5, 90.0 to 5, 100.0 to 3, 85.0 to 8)
                            )
                            "27_dup_like_style" -> listOf(List(3) { 85.0 to 5 }, List(3) { 85.0 to 8 }, List(3) { 85.0 to 6 })
                            else -> listOf(List(3) { 100.0 to 5 }, List(3) { 80.0 to 5 }, List(3) { 90.0 to 5 })
                        }
                        sessions.forEachIndexed { sessionIndex, prescription ->
                            val id = entryId++
                            add(WorkoutEntryWithSets(
                                WorkoutEntry(id = id, date = weekStart.plusDays((sessionIndex * 2).toLong()).toString(), exerciseStableKey = key, exerciseName = label, category = "근력운동", rpe = 8.0),
                                prescription.mapIndexed { setIndex, (weight, reps) -> WorkoutSet(entryId = id, setIndex = setIndex + 1, reps = reps, weightKg = weight, rpe = 8.0, confirmed = true) }
                            ))
                        }
                        return@forEachIndexed
                    }
                    val date = cutoff.minusDays(((weekCount - week) * 7L) - exerciseIndex.coerceAtMost(3))
                    val baseLoad = 45.0 + index + exerciseIndex * 7.5
                    val prescriptions = when {
                        name == "25_straight_5x5_style" -> List(5) { baseLoad to 5 }
                        name == "24_top_set_backoff_strength" -> listOf(baseLoad to 4, baseLoad * .9 to 7, baseLoad * .9 to 7)
                        name == "23_bodybuilding_top_set_style" -> listOf(baseLoad to 10)
                        name == "27_dup_like_style" -> List(3) { baseLoad to if (week % 2 == 0) 5 else 8 }
                        else -> List(3) { baseLoad to 8 }
                    }
                    val id = entryId++
                    add(
                        WorkoutEntryWithSets(
                            WorkoutEntry(id = id, date = date.toString(), exerciseStableKey = key, exerciseName = label, category = "근력운동", rpe = 8.0),
                            prescriptions.mapIndexed { setIndex, (weight, reps) ->
                                WorkoutSet(entryId = id, setIndex = setIndex + 1, reps = reps, weightKg = weight, rpe = 8.0, confirmed = true)
                            }
                        )
                    )
                }
            }
        }
        val goal = when {
            "badminton" in name -> "BADMINTON_PERFORMANCE"
            "hypertrophy" in name || "bodybuilding" in name -> "HYPERTROPHY_PHYSIQUE"
            "general" in name || "arms" in name || "lower" in name || "core" in name -> "MIXED"
            else -> "STRENGTH_GAIN"
        }
        val preferences = PersonalizedPlanningPreferences(
            strengthIntent = when {
                name == "18_hypertrophy_strength_unresolved" -> StrengthIntent.UNRESOLVED
                name == "19_hypertrophy_strength_mixed" -> StrengthIntent.MIXED
                "hypertrophy" in name || "bodybuilding" in name -> StrengthIntent.HYPERTROPHY_PRIORITY
                name.substringBefore('_').toInt() <= 17 -> StrengthIntent.UNRESOLVED
                else -> StrengthIntent.STRENGTH_PRIORITY
            },
            badmintonIntent = if ("badminton" in name && name != "12_badminton_explicitly_disabled") BadmintonPlanningIntent.ENABLED else BadmintonPlanningIntent.DISABLED,
            freeWeightWillingness = if ("machine" in name) FreeWeightWillingness.AVOID else FreeWeightWillingness.WILLING
        )
        return PlanningHistorySnapshotBuilder().build(
            cutoff, history, exercises, metadata, badmintonCatalog(),
            InitialUserProfile(primaryGoal = goal, strengthTrainingYears = 3.0, badmintonTrainingYears = if ("badminton" in name) 2.0 else 0.0),
            preferences,
            canonicalStrengthSignals = definitions.associate { (key, _, _) -> key to CanonicalStrengthSignal(100.0 + index, 2.0, weekCount, "TEST_CANONICAL_POSTERIOR") }
        )
    }

    private fun assertScenarioSemantics(name: String, snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, skeleton: com.training.trackplanner.data.GeneratedProgramSkeleton) {
        when (name) {
            "01_bodybuilding_badminton" -> assertTrue(name, state.anchors.none { it.stableKey in setOf("squat", "hinge", "press") })
            "02_badminton_machine", "03_novice_machine", "09_machine_general_modality_challenge" -> assertTrue(name, state.machineSetRatio > state.freeWeightSetRatio)
            "04_no_lower_non_badminton" -> assertTrue(name, gaps.any { it.code in setOf("LOWER_KNEE", "POSTERIOR_CHAIN") })
            "05_no_arms_calves_hypertrophy" -> assertTrue(name, gaps.any { it.code in setOf("ARMS_BICEPS", "ARMS_TRICEPS", "CALVES") })
            "06_arms_calves_only" -> assertTrue(name, state.anchors.all { it.movementGroup in setOf("ARMS_BICEPS", "ARMS_TRICEPS", "CALVES") })
            "07_no_core_non_badminton" -> assertTrue(name, gaps.any { it.code == "CORE_DIRECT" })
            "08_upper_only_general_fitness" -> assertTrue(name, gaps.any { it.code == "LOWER_KNEE" })
            "10_machine_general_strong_barbell_challenge" -> assertTrue(name, snapshot.allConfirmedSets.any { !snapshot.isMachineForTest(it.stableKey) })
            "11_badminton_onramp_from_zero" -> assertTrue(name, gaps.any { it.code == "BADMINTON_FOUNDATIONAL_ONRAMP" })
            "12_badminton_explicitly_disabled" -> assertTrue(name, gaps.none { it.code.startsWith("BADMINTON") })
            "13_lower_isolation_only_general", "14_lower_isolation_only_badminton", "15_lower_isolation_only_hypertrophy" -> assertTrue(name, state.anchors.any { it.stableKey == "leg_extension" } && state.anchors.none { it.stableKey == "squat" })
            "16_lower_isolation_strong_direct" -> assertTrue(name, state.anchors.any { it.stableKey == "squat" })
            "17_badminton_foundation_building" -> assertTrue(name, state.structuredBadmintonSessions > 0)
            "18_hypertrophy_strength_unresolved" -> assertEquals(name, StrengthIntent.UNRESOLVED, state.strengthIntent)
            "19_hypertrophy_strength_mixed" -> assertEquals(name, StrengthIntent.MIXED, state.strengthIntent)
            "20_hypertrophy_only_explicit" -> assertEquals(name, StrengthIntent.HYPERTROPHY_PRIORITY, state.strengthIntent)
            "21_stable_five_week_horizon" -> assertEquals(name, 5, skeleton.request.durationWeeks)
            "22_sparse_two_week_horizon" -> assertEquals(name, 2, skeleton.request.durationWeeks)
            "23_bodybuilding_top_set_style" -> assertEquals(name, StrengthProgrammingStyle.TOP_SET_HYPERTROPHY, state.observedStrengthStyle)
            "24_top_set_backoff_strength" -> assertEquals(name, StrengthProgrammingStyle.TOP_SET_BACKOFF, state.observedStrengthStyle)
            "25_straight_5x5_style" -> assertEquals(name, StrengthProgrammingStyle.STRAIGHT_5X5, state.observedStrengthStyle)
            "26_madcow_like_style" -> assertEquals(name, StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING, state.observedStrengthStyle)
            "27_dup_like_style" -> assertEquals(name, StrengthProgrammingStyle.DUP_LIKE_UNDULATING, state.observedStrengthStyle)
            "28_explicit_madcow_incompatible_badminton" -> assertFalse(name, state.observedStrengthStyle == StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING)
            "29_hlm_like_style" -> assertEquals(name, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM, state.observedStrengthStyle)
        }
    }

    private fun PlanningHistorySnapshot.isMachineForTest(key: String): Boolean = exercises.getValue(key).equipmentTags.contains("MACHINE")

    private fun badmintonCatalog() = CanonicalBadmintonObjectiveCatalog.of(listOf(
        CanonicalBadmintonObjectiveRelation("fixture_drill_footwork", "badminton_drill", BadmintonObjective.FOOTWORK, BadmintonObjectiveTransferLevel.DIRECT, "TEST", setOf("fixture"), "Synthetic fixture"),
        CanonicalBadmintonObjectiveRelation("fixture_drill_acceleration", "badminton_drill", BadmintonObjective.ACCELERATION, BadmintonObjectiveTransferLevel.DIRECT, "TEST", setOf("fixture"), "Synthetic fixture"),
        CanonicalBadmintonObjectiveRelation("fixture_squat_supportive", "squat", BadmintonObjective.JUMP_LANDING, BadmintonObjectiveTransferLevel.SUPPORTIVE, "TEST", setOf("fixture"), "Synthetic fixture")
    ))

    private fun state(
        historyDays: Int = 48,
        strengthIntent: StrengthIntent = StrengthIntent.STRENGTH_PRIORITY,
        primary: String = "STRENGTH_SUPPORT",
        badminton: BadmintonPlanningIntent = BadmintonPlanningIntent.DISABLED,
        style: StrengthProgrammingStyle = StrengthProgrammingStyle.UNRESOLVED
    ) = AthletePlanningState(
        ObservedTrainingBehavior.GENERAL_MIXED, StrengthExposure.PRESENT, strengthIntent, badminton, FreeWeightWillingness.AVOID,
        primary, historyDays, 3.0, 0.2, 0.0, 0.5, emptyList(), style,
        if (style == StrengthProgrammingStyle.UNRESOLVED) PlanningConfidence.LOW else PlanningConfidence.HIGH,
        if (badminton == BadmintonPlanningIntent.ENABLED) 6 else 0, "NONE", PlanningConfidence.HIGH
    )

    private fun snapshot(rows: List<PlanningSetRecord>, exercises: List<Exercise>): PlanningHistorySnapshot {
        val map = exercises.associateBy(Exercise::stableKey)
        return PlanningHistorySnapshot(LocalDate.of(2026, 1, 10), rows, map, map.mapValues { RuntimeExerciseMetadataDefaults.forExercise(it.value) }, emptyMap(), "GENERAL_FITNESS", 3.0, 0.0, PersonalizedPlanningPreferences())
    }

    private fun set(dayOffset: Long, key: String, weight: Double = 0.0, reps: Int = 0, index: Int = 1, seconds: Int = 0) =
        PlanningSetRecord(LocalDate.of(2026, 1, 10).plusDays(dayOffset), key, key, "근력운동", index, reps, weight, seconds, 7.5)
}
