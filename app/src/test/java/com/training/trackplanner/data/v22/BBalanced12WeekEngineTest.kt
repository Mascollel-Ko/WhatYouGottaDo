package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepositoryProvider
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.SeedData
import com.training.trackplanner.data.personalized.BBalancedTaskCatalog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStreamReader
import java.time.LocalDate
import java.util.zip.GZIPInputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BBalanced12WeekEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val metadata = CanonicalExerciseMetadataRepositoryProvider.get(context)
    private val exercises = SeedData.exercises(context).associateBy { it.stableKey }

    @Test
    fun `builds exactly three stable four week blocks without automatic deload`() {
        val plan = build(realRows(), request())
        assertEquals(12, plan.weeks.size)
        assertEquals(listOf(1 to 4, 5 to 8, 9 to 12), plan.blocks.map { it.startWeek to it.endWeek })
        plan.blocks.forEach { block ->
            val weeks = plan.weeks.filter { it.block == block.block }
            assertEquals(4, weeks.size)
            assertTrue(weeks.all { it.sessions == weeks.first().sessions })
        }
        val skeleton = plan.toSkeleton(request(), exercises)
        assertTrue(skeleton.weekPlans.none { it.deloadFlag })
        assertTrue(skeleton.items.flatMap { it.setPrescriptions }.all {
            it.weightKg == 0.0 && it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED
        })
        assertEquals(64, plan.search.evaluatedCandidates)
        assertTrue(plan.search.feasibleCandidates > 0)
    }

    @Test
    fun `importance override changes the selected twelve week result`() {
        val strength = build(realRows(), request(BBalancedGoalImportance(5, 1, 1, 1)))
        val sport = build(realRows(), request(BBalancedGoalImportance(1, 1, 1, 5)))
        assertNotEquals(strength.goalTargets, sport.goalTargets)
        assertNotEquals(strength.blocks.map { it.focus to it.sessions }, sport.blocks.map { it.focus to it.sessions })
        assertEquals(1, strength.blocks.map { it.focus }.distinct().size)

        val mixed = build(emptyList(), request(BBalancedGoalImportance(3, 3, 3, 3)))
        assertTrue(mixed.blocks.map { it.focus }.distinct().size > 1)
    }

    @Test
    fun `missing history and partially missing load or rpe still generate reviewable plans`() {
        val cold = build(emptyList(), request())
        assertEquals(12, cold.weeks.size)
        assertTrue(cold.goalTargets.all { it.confidence.history == 0.0 })
        assertEquals("UNAVAILABLE_NO_ELIGIBLE_PERSONAL_HISTORY", cold.legacyComparatorSchema)

        val incomplete = realRows().mapIndexed { index, row ->
            if (index % 2 == 0) row.copy(weightKg = null, rpe = null) else row
        }
        val partial = build(incomplete, request())
        assertEquals(12, partial.weeks.size)
        assertTrue(partial.goalTargets.all { it.confidence.history in 0.0..1.0 })
    }

    @Test
    fun `history and personal response confidence are independent and conflicting trends reduce response influence`() {
        val date = LocalDate.of(2026, 10, 11)
        val rows = buildList {
            repeat(8) { week ->
                val squatKg = 80.0 + week
                val pressKg = 50.0 - week
                repeat(3) { set ->
                    add(V22HistoryRow(date.minusDays((7L * (7 - week))), "스쿼트", "barbell_back_squat", "근력운동", set + 1, 5.0, squatKg, null, 8.0))
                    add(V22HistoryRow(date.minusDays((7L * (7 - week))), "벤치프레스", "barbell_bench_press", "근력운동", set + 1, 5.0, pressKg, null, 8.0))
                }
            }
        }
        val target = build(rows, request().copy(days = 2)).goalTargets.single { it.goal == BBalancedGoal.STRENGTH }
        assertNotEquals(target.confidence.history, target.confidence.personalResponse)
        assertTrue(target.confidence.reasons.any { "CONFLICTING_RESPONSE" in it })
        assertTrue(target.confidence.personalResponse < target.confidence.history)
    }

    @Test
    fun `time constrained search keeps the best feasible plan and is deterministic`() {
        val request = request().copy(sessionMinutes = 15)
        val first = build(realRows(), request)
        val second = build(realRows(), request)
        assertEquals(first, second)
        assertTrue(first.weeks.all { week -> week.sessions.all { it.items.isNotEmpty() } })
        assertEquals("BOUNDED_EXHAUSTIVE_4x4x4_FOCUS_SEQUENCE", first.search.terminationReason)
    }

    @Test
    fun `physical sets stay unique while separate goal contributions and maintenance shortages are evaluated`() {
        val rows = realRows()
        val plan = build(rows, request())
        val historicalKeys = rows.mapTo(mutableSetOf(), V22HistoryRow::stableKey)
        plan.weeks.forEach { week ->
            week.sessions.forEach { session ->
                assertEquals(session.items.size, session.items.map(V22ExercisePlan::stableKey).distinct().size)
                assertEquals(session.nominalSets, session.items.sumOf(V22ExercisePlan::setCount))
                assertTrue(session.items.all { it.stableKey in historicalKeys })
            }
        }
        val skeleton = plan.toSkeleton(request(), exercises)
        assertTrue(skeleton.items.all { it.setCount == it.setPrescriptions.size })
        assertTrue(plan.blocks.flatMap(BBalancedBlock::evaluation).any {
            it.maintenanceShortfall != null && it.maintenanceShortfall > 0.0 && it.status == "BELOW_MAINTENANCE_REVIEW"
        })
        val metadata = JSONObject(plan.metadataJson)
        val contributions = metadata.getJSONObject("contributions")
        val expectedCoreDose = plan.blocks.first().sessions.flatMap(V22Session::items).sumOf { item ->
            contributions.optJSONObject(item.stableKey)?.optDouble(BBalancedGoal.CORE.name, 0.0)?.times(item.setCount)
                ?: 0.0
        }
        assertEquals(
            expectedCoreDose,
            plan.blocks.first().evaluation.single { it.goal == BBalancedGoal.CORE }.plannedDose,
            0.0001
        )
    }

    @Test
    fun `scoped user edit reevaluates metadata without compensating another exercise`() {
        val plan = build(realRows(), request())
        val skeleton = plan.toSkeleton(request(), exercises)
        val removed = skeleton.items.first()
        val edited = skeleton.copy(items = skeleton.items.filterNot { it.localId == removed.localId })
            .reassessBBalancedAfterEdit()
        assertEquals(skeleton.items.size - 1, edited.items.size)
        assertTrue(requireNotNull(edited.planningMetadataJson).contains("\"userEdited\":true"))
        assertTrue(edited.planningMetadataJson!!.contains("postEditEvaluation"))
        val restored = restoredBBalancedWeekPlans(edited.planningMetadataJson, 12)
        assertNotNull(restored)
        assertTrue(restored!!.none { it.deloadFlag })
    }


    @Test
    fun authorizedFourthDayHasConservedPhysicalSetsAndSavedDayCount() {
        val settings = request().copy(sessionMinutes = 35, bMaxTrainingDays = 4)
        val expanded = build(realRows(), settings)
        val fixed = build(realRows(), settings.copy(bMaxTrainingDays = null))
        assertTrue(expanded.input.days in 3..4)
        assertEquals(3, fixed.input.days)
        assertEquals(expanded.input.days, expanded.weeks.first().sessions.size)
        assertEquals(expanded.input.days, expanded.toSkeleton(settings, exercises).request.weeklyTrainingDays)
        val baseline = fixed.weeks.first().sessions
        val placed = BBalancedDayPlacementPlanner.optimize(baseline, 3, 4, 35, exercises)
        assertEquals(baseline.sumOf(V22Session::nominalSets), placed.sessions.sumOf(V22Session::nominalSets))
        fun countKeys(sessions: List<V22Session>) = sessions.flatMap(V22Session::items)
            .groupBy(V22ExercisePlan::stableKey)
            .mapValues { (_, values) -> values.sumOf(V22ExercisePlan::setCount) }
        assertEquals(countKeys(baseline), countKeys(placed.sessions))
        assertTrue(placed.sessions.all {
            it.items.map(V22ExercisePlan::stableKey).distinct().size == it.items.size
        })
        assertTrue(JSONObject(expanded.metadataJson).getJSONObject("placementAudit")
            .getInt("candidateEvaluations") > 0)
    }

    @Test
    fun fixedDayRequestKeepsAllExercisesAndDeletionAuditIsReviewOnly() {
        val plan = build(realRows(), request().copy(sessionMinutes = 25, bMaxTrainingDays = 3))
        assertEquals(3, plan.input.days)
        val root = JSONObject(plan.metadataJson)
        assertEquals("AUDIT_ONLY_NO_AUTOMATIC_REMOVAL", root.getString("deletionPolicy"))
        val audit = root.getJSONArray("deletionAudit")
        assertTrue(audit.length() > 0)
        for (i in 0 until audit.length()) {
            assertTrue(audit.getJSONObject(i).getBoolean("notRemoved"))
        }
        assertEquals("HEURISTIC_PLACEMENT_REVIEW_ONLY_NOT_OFI_OR_TISSUE_PROJECTION",
            root.getJSONObject("placementAudit").getString("authority"))
        assertTrue(plan.weeks.all { week -> week.sessions.sumOf(V22Session::nominalSets) > 0 })
    }

    @Test
    fun defaultBRequestDoesNotAutoExpandOrChangeWeekdayIdentities() {
        val previous = build(realRows(), request())
        val repeated = build(realRows(), request())
        assertEquals(previous, repeated)
        assertEquals(listOf(1, 2, 3), previous.weeks.first().sessions.map(V22Session::session))
        assertEquals(false, JSONObject(previous.metadataJson).getJSONObject("placementAudit").getBoolean("expanded"))
    }

    private fun build(rows: List<V22HistoryRow>, request: V22GenerationRequest): BBalanced12WeekPlan =
        BBalanced12WeekEngine.build(
            rows,
            request,
            exercises,
            metadata.muscleRelations(),
            metadata.coreCatalog(),
            metadata.badmintonObjectiveCatalog(),
            BBalancedTaskCatalog.fromAssets(context)
        )

    private fun request(importance: BBalancedGoalImportance = BBalancedGoalImportance()) = V22GenerationRequest(
        name = "B balanced test",
        weeks = 12,
        days = 3,
        mode = V22Mode.B,
        asOf = LocalDate.of(2026, 10, 11),
        sessionMinutes = 60,
        bGoalImportance = importance
    )

    private fun realRows(): List<V22HistoryRow> = GZIPInputStream(checkNotNull(javaClass.classLoader?.getResourceAsStream(
        "v22-real-history/whatyougottado_backup_2026-10-12-v22.csv.gz"
    ))).use { input -> V22HistoryInput.fromBackupCsv(InputStreamReader(input, Charsets.UTF_8)) }
}
