package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class ResistanceVolumePlanningTest {
    private val cutoff = LocalDate.of(2026, 9, 17)

    @Test
    fun `resistance baseline ignores structured performance volume`() {
        val source = source(List(6) { 36 }, List(6) { 100.0 }, includeDrill = true)
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val budget = ResistanceVolumePlanner.plan(source, state, request(3), 100, 0.0)

        assertEquals("NORMAL_COMPLETE_WEEK_MEDIAN", budget.resistanceBaselineSource)
        assertEquals(36.0, budget.resistanceBaselineSets, .001)
        assertEquals(36.0, budget.resistanceWeeklyMedian, .001)
        assertEquals(36, budget.resistanceTargetSets)
    }

    @Test
    fun `stable high badminton with normal lower response has no interference`() {
        val source = source(List(6) { 36 }, List(6) { 300.0 })
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
            .copy(genericCourtLoad = 3000.0, courtDeviation = 0.0, lowerNegativeEvidence = 0.0)
        val anchor = state.anchors.single()
        val transition = AdaptationTransitionPlanner().decide(anchor, state, emptyList())

        assertEquals(0.0, transition.adaptation.sportInterferencePressure, .001)
        assertEquals(0.0, transition.localDoseFactor - AdaptationTransitionPlanner().decide(
            anchor, state.copy(genericCourtLoad = 0.0), emptyList()
        ).localDoseFactor, .001)
    }

    @Test
    fun `court deviation only acts with lower negative evidence`() {
        val source = source(List(6) { 36 }, List(5) { 100.0 } + 300.0)
        val normal = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val deviationOnly = normal.copy(courtDeviation = 1.0, lowerNegativeEvidence = 0.0)
        val negative = normal.copy(courtDeviation = 1.0, lowerNegativeEvidence = 1.0)
        val anchor = normal.anchors.single()
        val planner = AdaptationTransitionPlanner()

        assertEquals(0.0, planner.decide(anchor, deviationOnly, emptyList()).adaptation.sportInterferencePressure, .001)
        assertTrue(planner.decide(anchor, negative, emptyList()).adaptation.sportInterferencePressure > 0.0)
    }

    @Test
    fun `explicit extra days release additional useful resistance demand`() {
        val source = source(List(6) { 30 }, List(6) { 0.0 })
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val three = ResistanceVolumePlanner.plan(source, state, request(3), 200, 0.0)
        val five = ResistanceVolumePlanner.plan(source, state, request(5), 200, 0.0)

        assertTrue(five.resistanceDayRelease > three.resistanceDayRelease)
        assertTrue(five.resistanceTargetSets > three.resistanceTargetSets)
    }

    @Test
    fun `global recovery dose still lowers resistance target`() {
        val source = source(List(6) { 40 }, List(6) { 0.0 })
        val state = AthletePlanningStateBuilder().build(source, PersonalizedPlanningAnswers())
        val normal = ResistanceVolumePlanner.plan(source, state, request(3), 200, 0.0)
        val constrainedState = state.copy(trainingStateAssessment = requireNotNull(state.trainingStateAssessment).copy(globalDoseFactor = .80))
        val constrained = ResistanceVolumePlanner.plan(source, constrainedState, request(3), 200, 0.0)

        assertTrue(constrained.resistanceTargetSets < normal.resistanceTargetSets)
    }

    @Test
    fun `additional performance bouts do not lower independent resistance target`() {
        val resistanceOnly = source(List(6) { 32 }, List(6) { 0.0 })
        val withPerformance = source(List(6) { 32 }, List(6) { 0.0 }, includeDrill = true)
        val resistanceBudget = ResistanceVolumePlanner.plan(
            resistanceOnly,
            AthletePlanningStateBuilder().build(resistanceOnly, PersonalizedPlanningAnswers()),
            request(3),
            200,
            0.0
        )
        val performanceBudget = ResistanceVolumePlanner.plan(
            withPerformance,
            AthletePlanningStateBuilder().build(withPerformance, PersonalizedPlanningAnswers()),
            request(3),
            200,
            0.0
        )
        assertEquals(resistanceBudget.resistanceTargetSets, performanceBudget.resistanceTargetSets)
        assertEquals(resistanceBudget.resistanceWeeklyMedian, performanceBudget.resistanceWeeklyMedian, .001)
    }

    private fun request(days: Int) = ProgramSkeletonRequest(
        "resistance-volume", ProgramGoal.BODYBUILDING, days, 60, emptySet(), "", .5, "AUTO",
        ProgramPeriodizationType.AUTO, 4
    )

    private fun source(counts: List<Int>, court: List<Double>, includeDrill: Boolean = false): PlanningHistorySnapshot {
        val resistance = Exercise("squat", "Squat", "근력운동", planningEligibility = "PROGRAM_SELECTABLE")
        val drill = Exercise("drill", "Footwork", "배드민턴", activityKind = "EXERCISE", planningEligibility = "PROGRAM_SELECTABLE")
        val resistanceMetadata = RuntimeExerciseMetadataDefaults.forExercise(resistance).copy(
            activityKind = "EXERCISE", programSlot = "MAIN_LOWER_STRENGTH", analysisEligibility = MetadataTokenField.parse("STRENGTH_PROGRESS|HYPERTROPHY_VOLUME"),
            planningEligibility = "PROGRAM_SELECTABLE", sourceConfidenceLevel = "HIGH"
        )
        val drillMetadata = RuntimeExerciseMetadataDefaults.forExercise(drill).copy(
            programSlot = "BADMINTON_FOOTWORK", analysisEligibility = MetadataTokenField.parse("BADMINTON_TRANSFER"),
            badmintonTransferLevel = "DIRECT", planningEligibility = "PROGRAM_SELECTABLE"
        )
        val monday = cutoff.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val rows = buildList {
            counts.forEachIndexed { index, count ->
                val date = monday.minusWeeks((counts.lastIndex - index).toLong()).plusDays(1)
                repeat(count) { setIndex -> add(PlanningSetRecord(date, "squat", "Squat", "근력운동", setIndex + 1, 5, 100.0, 0, 7.0)) }
                if (includeDrill) add(PlanningSetRecord(date, "drill", "Footwork", "배드민턴", 1, 0, 0.0, 60, 7.0))
            }
        }
        val courtWeeks = counts.indices.associate { index ->
            monday.minusWeeks((counts.lastIndex - index).toLong()).plusDays(6) to court[index]
        }
        return PlanningHistorySnapshot(
            cutoff = cutoff, allConfirmedSets = rows,
            exercises = mapOf("squat" to resistance, "drill" to drill),
            metadata = mapOf("squat" to resistanceMetadata, "drill" to drillMetadata),
            badmintonObjectives = emptyMap(), profilePrimaryGoal = "STRENGTH", strengthTrainingYears = 3.0,
            badmintonTrainingYears = 1.0, preferences = PersonalizedPlanningPreferences(
                strengthIntent = StrengthIntent.STRENGTH_PRIORITY, badmintonIntent = BadmintonPlanningIntent.ENABLED,
                freeWeightWillingness = FreeWeightWillingness.WILLING
            ), weeklyCourtLoad = courtWeeks
        )
    }
}
