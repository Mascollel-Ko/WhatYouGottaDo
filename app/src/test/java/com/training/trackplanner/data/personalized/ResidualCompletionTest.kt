package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ResidualCompletionTest {
    private val f = PostGenerationFixture
    private val snapshot = f.snapshot()
    private val gap = AdaptationGap("LOWER_KNEE", "HIGH", "fixture")
    private fun authorized() = listOf(f.authorized(snapshot, f.source("press", 8), true), f.authorized(snapshot, f.source("squat", 2, gap.code)))

    @Test fun `Q uses exact funded prescriptions and not raw candidate demand`() {
        val funded = FiniteExecutionAllocator.allocate(10, 8, listOf(2, 20), .2, 1)
        assertEquals(listOf(2, 0), funded.material)
        val demand = AuthorizedPlanningDemand(snapshot, authorized(), listOf(gap, AdaptationGap("POSTERIOR_CHAIN", "HIGH", "raw")), emptyList())
        assertEquals(setOf("CONTINUITY:press:", "LOWER_KNEE"), demand.residuals(emptyList()).map { it.id }.toSet())
        assertEquals(2.0, demand.residuals(emptyList()).first { it.id == gap.code }.requested, 0.0)
        assertEquals(10, demand.authorizedUnits)
    }
    @Test fun `resistance Q C R are sets not presence booleans`() {
        val authorized = listOf(f.authorized(snapshot, f.source("squat", 4, gap.code)))
        val demand = AuthorizedPlanningDemand(snapshot, authorized, listOf(gap), listOf(f.row("squat", 1)))
        val residual = demand.residuals(listOf(f.row("squat", 1))).single()
        assertEquals(4.0, residual.requested, 0.0); assertEquals(2.0, residual.coverage, 0.0)
        assertEquals(2.0, residual.residual, 0.0); assertEquals(.5, residual.fraction, 0.0)
    }
    @Test fun `weighted SUPPORTIVE reduces objective but never DIRECT demand`() {
        val gap = AdaptationGap("BADMINTON_DROP_REACTION", "HIGH", "direct drop")
        val auth = listOf(f.authorized(snapshot, f.source("direct", 3, gap.code)))
        val demand = AuthorizedPlanningDemand(snapshot, auth, listOf(gap), emptyList())
        val values = demand.residuals(listOf(f.row("support", 1, 2))).associateBy { it.unit }
        assertEquals(2.2, values.getValue(PlanningDemandUnit.OBJECTIVE_EXPOSURE).residual, 1e-9)
        assertEquals(3.0, values.getValue(PlanningDemandUnit.DIRECT_SETS).residual, 0.0)
        val ordinaryGap = gap.copy(code = "BADMINTON_UNDERREPRESENTED_REACTION")
        val ordinary = AuthorizedPlanningDemand(snapshot, listOf(f.authorized(snapshot, f.source("direct", 3, ordinaryGap.code))), listOf(ordinaryGap), emptyList())
        assertFalse(ordinary.definitions.any { it.unit == PlanningDemandUnit.DIRECT_SETS })
    }
    @Test fun `historical RPE cannot alter planning quantities`() {
        val otherRpe = snapshot.copy(allConfirmedSets = snapshot.allConfirmedSets.map { it.copy(rpe = 10.0) })
        val a = AuthorizedPlanningDemand(snapshot, authorized(), listOf(gap), emptyList()).residuals(emptyList())
        val b = AuthorizedPlanningDemand(otherRpe, authorized(), listOf(gap), emptyList()).residuals(emptyList())
        assertEquals(a, b)
    }
    @Test fun `continuity uses exact stableKey and style variant identity`() {
        val item = f.source("squat", 2).copy(styleVariant = "LIGHT")
        val auth = AuthorizedPrescription("s", item, f.rx(2), true)
        val demand = AuthorizedPlanningDemand(snapshot, listOf(auth), emptyList(), emptyList())
        assertEquals(2.0, demand.residuals(listOf(f.row("squat", 1).copy(progressionVariant = "HEAVY"))).single().residual, 0.0)
        assertEquals(0.0, demand.residuals(listOf(f.row("squat", 1).copy(progressionVariant = "LIGHT"))).single().residual, 0.0)
    }
    @Test fun `one accepted addition recalculates every residual and mirrors all weeks`() {
        val initial = f.plan(listOf(f.row("press", 1, 8)))
        val frozen = initial.copy()
        val result = f.complete(initial, authorized(), listOf(gap))
        assertEquals(frozen, initial)
        assertEquals(1, result.trace.additions.size)
        assertEquals(3, result.skeleton.items.count { it.exerciseStableKey == "squat" })
        assertTrue(result.trace.residuals.all { it.residual == 0.0 })
        assertEquals(result.trace.residuals, result.trace.additions.single().residualsAfter)
        assertNotNull(result.week)
        assertEquals(personalizedProgramFingerprint(initial.request, initial.items), result.trace.initialFingerprint)
    }
    @Test fun `no residual means no filler even with many empty days`() {
        val initial = f.plan(listOf(f.row("press", 1, 8), f.row("squat", 3)))
        val result = f.complete(initial, authorized(), listOf(gap))
        assertEquals(initial, result.skeleton); assertTrue(result.trace.additions.isEmpty())
    }
    @Test fun `sparse trigger is strictly both units AND time below half`() {
        val initial = f.plan(listOf(f.row("press", 1, 8), f.row("row", 3, 2, 400), f.row("other", 5, 5, 1)))
        val auth = authorized() + listOf(f.authorized(snapshot, f.source("row", 2), true), f.authorized(snapshot, f.source("other", 5), true))
        val result = f.complete(initial, auth, listOf(gap))
        assertTrue(result.trace.additions.isEmpty())
        assertEquals(initial, result.skeleton)
    }
    @Test fun `time is exact prescribed seconds or 45 and N minus one rests`() {
        val row = f.row("squat", 1, 3).copy(restSeconds = 90,
            setPrescriptions = listOf(ProgramSetPrescription(1, 8, 0.0, 20), ProgramSetPrescription(2, 8, 0.0, 0), ProgramSetPrescription(3, 8, 0.0, 40)))
        assertEquals(285, plannedSeconds(row))
    }
    @Test fun `fallback medians come from nonempty initial days and historical reference wins at four`() {
        val initial = f.plan(listOf(f.row("press", 1, 2, 100), f.row("row", 3, 6, 100)))
        val result = f.complete(initial, emptyList(), emptyList(), envelope = f.envelope(observations = 3))
        assertEquals(4.0, result.trace.referenceUnits, 0.0); assertEquals(400.0, result.trace.referenceSeconds, 0.0)
        val historical = f.complete(initial, emptyList(), emptyList(), envelope = f.envelope(observations = 4))
        assertEquals(10.0, historical.trace.referenceUnits, 0.0); assertEquals(1200.0, historical.trace.referenceSeconds, 0.0)
    }
    @Test fun `projection uses cutoff plus one only and synthetic RPE is null`() {
        val calculator = DailyFatigueCalculator(RuntimeExerciseMetadataCatalog.of(snapshot.metadata.values))
        val adapter = PlanDayOfiProjection(snapshot.cutoff, calculator, snapshot.exercises.values.toList(), emptyList(), null)
        val row = f.row("squat", 1)
        val synthetic = adapter.syntheticRows(listOf(row))
        assertEquals(snapshot.cutoff.plusDays(1), adapter.projectionDate)
        assertTrue(synthetic.all { it.entry.date == adapter.projectionDate.toString() && it.entry.rpe == null && it.sets.all { set -> set.rpe == null } })
        assertEquals(adapter.evaluate(listOf(row)), adapter.evaluate(listOf(row.copy(dayOfWeek = 7))))
        val direct = calculator.calculate(adapter.projectionDate, snapshot.exercises.values.toList(), synthetic, null).state
        assertEquals(direct.overallFatigueIndex, adapter.evaluate(listOf(row)).ofi)
    }
    @Test fun `OFI 87 and axis 100 and tissue restriction each reject additions`() {
        val initial = f.plan(listOf(f.row("press", 1, 8)))
        listOf(StandaloneDayLoad(87, listOf(0)), StandaloneDayLoad(0, listOf(100))).forEach { load ->
            assertEquals(initial, f.complete(initial, authorized(), listOf(gap), projection = PlanDayProjection { load }).skeleton)
        }
        val restricted = snapshot.copy(recoverySignals = PlanningRecoverySignals(tissueRestrictedStableKeys = setOf("squat")))
        assertEquals(initial, f.complete(initial, authorized(), listOf(gap), snapshot = restricted).skeleton)
    }
    @Test fun `capacity cannot be bypassed and indivisible reviewed dose cannot shrink`() {
        val initial = f.plan(listOf(f.row("press", 1, 8)))
        assertEquals(initial, f.complete(initial, authorized(), listOf(gap), envelope = f.envelope(units = 8)).skeleton)
        val objective = AdaptationGap("BADMINTON_DROP_REACTION", "HIGH", "fixture")
        val auth = listOf(f.authorized(snapshot, f.source("direct", 3, objective.code)))
        val withCoverage = f.plan(listOf(f.row("direct", 1, 1)))
        val result = f.complete(withCoverage, auth, listOf(objective))
        // A legitimate supportive alternative may fit; the reviewed DIRECT prescription cannot be split.
        assertTrue(result.trace.additions.none { it.stableKey == "direct" })
        assertEquals(withCoverage.items.filter { it.exerciseStableKey == "direct" }, result.skeleton.items.filter { it.exerciseStableKey == "direct" })
        assertEquals(2.0, result.trace.residuals.first { it.unit == PlanningDemandUnit.DIRECT_SETS }.residual, 0.0)
    }
    @Test fun `non isomorphic weeks skip and projection failure falls back untouched`() {
        val initial = f.plan(listOf(f.row("press", 1, 8)))
        val broken = initial.copy(items = initial.items.map { if (it.weekNumber == 2) it.copy(prescription = "different") else it })
        val skipped = f.complete(broken, authorized(), listOf(gap))
        assertEquals("POST_PROCESS_SKIPPED_NON_ISOMORPHIC_WEEKS", skipped.trace.state); assertEquals(broken, skipped.skeleton)
        val failed = f.complete(initial, authorized(), listOf(gap), projection = PlanDayProjection { error("projection failure") })
        assertEquals(initial, failed.skeleton); assertEquals("POST_PROCESS_FAILED_SAFE_INITIAL_SKELETON", failed.trace.state)
    }
    @Test fun `plus one day requires genuine hard infeasibility and unfixed frequency`() {
        val initial = f.plan(listOf(f.row("squat", 1, 2), f.row("squat", 3, 2, id = "squat2"), f.row("squat", 5, 2, id = "squat3")))
        val auth = listOf(f.authorized(snapshot, f.source("squat", 8, gap.code)))
        val fixed = f.complete(initial, auth, listOf(gap))
        assertFalse(fixed.trace.addedDay)
        val flexible = f.complete(initial, auth, listOf(gap), fixed = false)
        assertTrue(flexible.trace.addedDay); assertEquals(4, flexible.skeleton.request.weeklyTrainingDays)
        assertEquals(RecordBasedReviewedPolicy.defaultSchedule(3, 4), flexible.skeleton.weekDaySchedule)
        assertEquals(1, flexible.trace.additions.size)
        assertEquals(0.0, flexible.trace.residuals.single().residual, 0.0)
        val blocked = f.complete(initial, auth, listOf(gap), fixed = false, projection = PlanDayProjection { StandaloneDayLoad(87, emptyList()) })
        assertFalse(blocked.trace.addedDay)
    }
    @Test fun `non sparse but feasible existing day prevents plus one`() {
        val initial = f.plan(listOf(f.row("press", 1, 8), f.row("row", 3, 8), f.row("other", 5, 8)))
        val auth = authorized() + listOf(f.authorized(snapshot, f.source("row", 8), true), f.authorized(snapshot, f.source("other", 8), true))
        assertFalse(f.complete(initial, auth, listOf(gap), fixed = false).trace.addedDay)
    }
    @Test fun `production insertion is after initial validation and fingerprint`() {
        val root = generateSequence(File(System.getProperty("user.dir")), File::getParentFile).first { File(it, "settings.gradle.kts").isFile }
        val source = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt").readText()
        assertTrue(source.indexOf("val selected = continuity + gapItems + optional") < source.indexOf("val placement ="))
        assertTrue(source.indexOf("val initialSkeleton =") > source.indexOf("val fingerprint ="))
        assertTrue(source.indexOf("val initialSkeleton =") > source.indexOf("require(remaining.isEmpty())"))
        val pass = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/ResidualCompletion.kt").readText()
        assertFalse(pass.contains("RPE 7")); assertFalse(pass.contains("AnalysisStimulusRpePolicy"))
    }
    @Test fun `user exclusion equipment and session time remain hard candidate gates`() {
        val initial = f.plan(listOf(f.row("press", 1, 8)))
        val excluded = initial.copy(request = initial.request.copy(excludedExerciseStableKeys = setOf("squat")))
        assertTrue(f.complete(excluded, authorized(), listOf(gap)).trace.additions.isEmpty())
        val equipment = snapshot.copy(exercises = snapshot.exercises + ("squat" to snapshot.exercises.getValue("squat").copy(equipment = "BARBELL")))
        val limited = initial.copy(request = initial.request.copy(availableEquipment = setOf("BODYWEIGHT")))
        assertTrue(f.complete(limited, authorized(), listOf(gap), snapshot = equipment).trace.additions.isEmpty())
        val short = f.plan(listOf(f.row("press", 1, 1, 1)), minutes = 1)
        assertTrue(f.complete(short, authorized(), listOf(gap)).trace.additions.isEmpty())
    }
    @Test fun `multi objective addition closes all related residuals before another selection`() {
        val source = snapshot.copy(badmintonObjectives = snapshot.badmintonObjectives + ("direct" to mapOf("REACTION" to 1.0, "STEP" to .6)),
            badmintonDirectObjectives = snapshot.badmintonDirectObjectives + ("direct" to setOf("REACTION", "STEP")))
        val gaps = listOf(AdaptationGap("BADMINTON_UNDERREPRESENTED_REACTION", "HIGH", "a"), AdaptationGap("BADMINTON_UNDERREPRESENTED_STEP", "HIGH", "b"))
        val item = f.source("direct", 3).copy(representedGapCodes = gaps.map { it.code }.toSet(), representedObjectives = setOf("REACTION", "STEP"))
        val auth = listOf(f.authorized(source, f.source("press", 8), true), f.authorized(source, item))
        val result = f.complete(f.plan(listOf(f.row("press", 1, 8))), auth, gaps, snapshot = source)
        assertEquals(1, result.trace.additions.size)
        assertTrue(result.trace.additions.single().residualsAfter.all { it.residual == 0.0 })
        assertEquals(result.trace.residuals, result.trace.additions.single().residualsAfter)
    }
    @Test fun `production post pass preserves initial fingerprint and historical gap outputs`() {
        val state = f.state(snapshot)
        val gaps = AdaptationGapAnalyzer().analyze(snapshot, state)
        val intent = BlockIntentPlanner().decide(state, gaps)
        val request = f.plan(listOf(f.row("press", 1))).request
        fun generate(source: PlanningHistorySnapshot) = PersonalizedProgramBuilder().build(source, state, gaps, intent,
            request.durationWeeks, request, PersonalizedPlanningAnswers(), null)
        val withoutProjection = generate(snapshot)
        val withProjection = generate(snapshot.copy(planDayProjection = f.safe))
        assertEquals(withoutProjection.personalizedDecision!!.originalGenerationFingerprint,
            withProjection.personalizedDecision!!.originalGenerationFingerprint)
        assertEquals(personalizedProgramFingerprint(withoutProjection.request, withoutProjection.items),
            withProjection.personalizedDecision!!.originalGenerationFingerprint)
        assertEquals(gaps, withProjection.personalizedDecision!!.adaptationGaps)
        assertEquals(gaps, AdaptationGapAnalyzer().analyze(snapshot, state))
        assertEquals("POST_GENERATION_RESIDUAL_COMPLETION", withProjection.personalizedDecision!!.residualCompletion!!.state)
        assertEquals(withProjection.items.filter { it.weekNumber == 1 && snapshot.activityKind(it.exerciseStableKey) == PlannedActivityKind.RESISTANCE }.sumOf { it.setCount },
            withProjection.personalizedDecision!!.planningBudget!!.plannedResistanceSets)
    }
}
