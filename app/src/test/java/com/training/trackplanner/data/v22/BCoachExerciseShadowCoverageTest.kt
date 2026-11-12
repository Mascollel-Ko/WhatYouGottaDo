package com.training.trackplanner.data.v22

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.data.ProgramSkeletonItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachExerciseShadowCoverageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val today = LocalDate.of(2026, 11, 12)
    private val units = BBlockEquipmentIncrements(1.5, 1.0, 5.0)

    private fun plan(key: String, name: String = key) = ProgramSkeletonItem(
        localId = "test-$key", weekNumber = 1, dayOfWeek = 2, orderIndex = 1,
        exerciseStableKey = key, exerciseName = name, category = "근력운동",
        restSeconds = 120, prescription = "review-only", setCount = 3,
        reps = 8, weightKg = 0.0, seconds = 0, selectionReason = "TEST",
        weightSource = "USER_CALIBRATION_REQUIRED"
    )

    private fun observed(
        key: String, load: Double? = 50.0, reps: Double? = 8.0,
        date: LocalDate = today.minusDays(1), seconds: Double? = null
    ) = V22HistoryRow(date, key, key, "근력운동", 1, reps, load, seconds, 8.0)

    private fun inspect(
        plans: List<ProgramSkeletonItem>,
        sets: List<V22HistoryRow> = emptyList(),
        increments: BBlockEquipmentIncrements? = units
    ) = BCoachExerciseShadowCoverage.inspect(today, plans, sets, registry, emptyMap(), increments)

    @Test
    fun `only exact canonical keys receive canonical tier and weighted pullup demands bodyweight`() {
        val keys = listOf("barbell_back_squat", "barbell_deadlift", "barbell_bench_press", "ex_e41f4c2b")
        val rows = inspect(keys.map(::plan), keys.map { observed(it) })
        assertEquals(3, rows.size) // squat already has its independent Gate D report
        assertTrue(rows.all { it.tier == "A_CANONICAL" })
        assertEquals("BODYWEIGHT_AND_POSTERIOR_BRIDGE_REQUIRED",
            rows.single { it.stableKey == "ex_e41f4c2b" }.status)
        assertTrue(rows.none { it.status == "APPROVED_LOAD" })
    }

    @Test
    fun `reviewed RDL proxy relation cannot pretend to be a canonical posterior`() {
        val row = inspect(listOf(plan("barbell_romanian_deadlift")),
            listOf(observed("barbell_romanian_deadlift"))).single()
        assertEquals("B_LOCAL", row.tier)
        assertEquals("LOCAL_POSTERIOR_ELIGIBILITY_UNVERIFIED", row.status)
        assertTrue(row.explanation.contains("local posterior"))
    }

    @Test
    fun `ordinary barbell lift receives only unranked hold reps and configured-grid actions`() {
        val key = "barbell_noncanonical_auxiliary"
        val row = inspect(listOf(plan(key)), listOf(observed(key, 60.0, 8.0))).single()
        assertEquals("C_OBSERVED", row.tier)
        assertEquals("OBSERVATION_ONLY", row.status)
        assertTrue(row.unrankedAlternatives.any { it.contains("반복 +1") })
        assertTrue(row.unrankedAlternatives.any { it.contains("+3.0kg") })
        assertFalse(row.explanation.contains("성공률 94"))
    }

    @Test
    fun `actual increments contradicting user configuration block numeric load alternative`() {
        val key = "barbell_noncanonical_auxiliary"
        val rows = listOf(observed(key, 60.0, 8.0),
            observed(key, 62.5, 8.0, today.minusDays(2)))
        val result = inspect(listOf(plan(key)), rows).single()
        assertEquals("INCREMENT_CONFLICT_REVIEW", result.status)
        assertFalse(result.unrankedAlternatives.any { it.contains("표시 중량 +") })
        assertTrue(result.explanation.contains("사용자 확인"))
    }

    @Test
    fun `no increment information never invents candidate kilograms`() {
        val key = "dumbbell_unregistered_curl"
        val row = inspect(listOf(plan(key)), listOf(observed(key, 12.0, 12.0)), null).single()
        assertEquals("INCREMENT_UNKNOWN", row.status)
        assertFalse(row.unrankedAlternatives.any { it.contains("표시 중량 +") })
        assertTrue(row.unrankedAlternatives.any { it.contains("반복 +1") })
    }

    @Test
    fun `time hold without mechanical load is task-specific and never uses RIR`() {
        val key = "generic_duration_task"
        val row = inspect(listOf(plan(key)),
            listOf(observed(key, load = null, reps = null, seconds = 25.0))).single()
        assertEquals("D_TASK", row.tier)
        assertEquals("TASK_SPECIFIC_REVIEW", row.status)
        assertTrue(row.unrankedAlternatives.any { it.contains("유지시간") })
        assertFalse(row.unrankedAlternatives.any { it.contains("중량 +") })
    }

    @Test
    fun `task exercise above twenty repetitions is observed but not run through e1rm curve`() {
        val key = "functional_fast_footwork"
        val row = inspect(listOf(plan(key)), listOf(
            observed(key, load = null, reps = 30.0)
        )).single()
        assertEquals("D_TASK", row.tier)
        assertTrue(row.confirmedObservation!!.contains("30.0회"))
        assertFalse(row.unrankedAlternatives.any { it.contains("중량 +") })
    }

    @Test
    fun `future completed events and unplanned exercises cannot contaminate audit`() {
        val key = "barbell_noncanonical_auxiliary"
        val before = listOf(plan(key))
        val rows = inspect(before, listOf(
            observed(key, 50.0, 8.0, today.minusDays(1)),
            observed(key, 200.0, 8.0, today.plusDays(1)),
            observed("barbell_unrelated", 300.0, 1.0)
        ))
        assertEquals(1, rows.size)
        assertTrue(rows.single().confirmedObservation!!.contains("50.0kg"))
        assertFalse(rows.single().confirmedObservation!!.contains("200.0kg"))
        assertEquals(0.0, before.single().weightKg, 0.0)
    }

    @Test
    fun `no observed evidence is explicit unknown rather than zero performance`() {
        val row = inspect(listOf(plan("aux_no_records"))).single()
        assertEquals("NO_COMPARABLE_OBSERVATION", row.status)
        assertNull(row.confirmedObservation)
        assertTrue(row.unrankedAlternatives.isEmpty())
    }
}
