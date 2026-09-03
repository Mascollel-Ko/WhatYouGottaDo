package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PersonalizedPlannerParityTest {
    private val styleAnalyzer = StrengthProgrammingStyleAnalyzer()

    @Test
    fun `v08 parity matrix contains all 29 named personas and decisions remain deterministic`() {
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
        names.forEach { name ->
            val state = stateFor(name)
            val gaps = if (name in setOf("04_no_lower_non_badminton", "06_arms_calves_only", "08_upper_only_general_fitness")) listOf(AdaptationGap("LOWER_KNEE", "HIGH", "fixture")) else emptyList()
            val first = BlockIntentPlanner().decide(state, gaps)
            val second = BlockIntentPlanner().decide(state, gaps)
            assertEquals(name, first, second)
            assertEquals(name, state.primaryAdaptation, first.primary)
            assertTrue(name, "HISTORY_CUTOFF_ENFORCED" in first.reasonCodes)
            assertTrue(name, "STABLE_KEY_AUTHORITY" in first.reasonCodes)
            if (state.observedStrengthStyle != StrengthProgrammingStyle.UNRESOLVED) {
                assertEquals(name, state.observedStrengthStyle, first.selectedStyle)
            }
            assertTrue(name, WeeklyDosePlanner().chooseDays(state, state.anchors.size) in 2..5)
            assertTrue(name, PlanningHorizonPlanner().choose(state, gaps) in 2..6)
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

    private fun stateFor(name: String): AthletePlanningState = when {
        name == "22_sparse_two_week_horizon" -> state(historyDays = 11)
        name == "18_hypertrophy_strength_unresolved" -> state(strengthIntent = StrengthIntent.UNRESOLVED, primary = "HYPERTROPHY")
        name == "19_hypertrophy_strength_mixed" -> state(strengthIntent = StrengthIntent.MIXED, primary = "HYPERTROPHY_STRENGTH")
        name == "20_hypertrophy_only_explicit" -> state(strengthIntent = StrengthIntent.HYPERTROPHY_PRIORITY, primary = "HYPERTROPHY")
        name == "23_bodybuilding_top_set_style" -> state(strengthIntent = StrengthIntent.HYPERTROPHY_PRIORITY, primary = "HYPERTROPHY", style = StrengthProgrammingStyle.TOP_SET_HYPERTROPHY)
        name == "24_top_set_backoff_strength" -> state(style = StrengthProgrammingStyle.TOP_SET_BACKOFF)
        name == "25_straight_5x5_style" -> state(style = StrengthProgrammingStyle.STRAIGHT_5X5)
        name == "26_madcow_like_style" -> state(style = StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING)
        name == "27_dup_like_style" -> state(style = StrengthProgrammingStyle.DUP_LIKE_UNDULATING)
        name == "29_hlm_like_style" -> state(style = StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM)
        name.contains("hypertrophy") || name.contains("bodybuilding") -> state(primary = "HYPERTROPHY", strengthIntent = StrengthIntent.HYPERTROPHY_PRIORITY)
        name.contains("badminton") -> state(primary = "BADMINTON_SUPPORT", badminton = BadmintonPlanningIntent.ENABLED)
        else -> state()
    }

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
