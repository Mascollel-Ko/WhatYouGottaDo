package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class AuthorizedPipelineInvariantTest {
    private val f = PostGenerationFixture
    private fun verify(count: Int) {
        val base = f.snapshot()
        val snapshot = base.copy(exercises = base.exercises + ("legpress" to base.exercises.getValue("squat").copy(stableKey = "legpress", name = "Leg press")),
            metadata = base.metadata + ("legpress" to base.metadata.getValue("squat").copy(stableKey = "legpress")))
        val squat = AuthorizedSchedulingDemand("squat-parent", f.source("squat", count), f.rx(count, 45, 60), true)
        val press = AuthorizedSchedulingDemand("gap-parent", f.source("legpress", 2, "LOWER_KNEE"), f.rx(2), false)
        val authorized = listOf(squat, press)
        val reduced = TimedPlannedExercise(squat.item.copy(targetSets = 2), f.rx(2, 45, 60))
        val baseline = TimedExecutionAllocation(mapOf(1 to listOf(reduced, TimedPlannedExercise(press.item, press.prescription)), 2 to emptyList(), 3 to emptyList()), emptyList())
        val allocation = SplitAwareContinuityAllocation(PersonalizedPrescriptionPlanner()).improve(snapshot, f.state(snapshot), authorized, baseline, 3, 5)
        val atoms = allocation.days.toSortedMap().flatMap { (day, rows) -> rows.map { day to it } }
        val first = atoms.mapIndexed { index, (day, atom) -> residualItem(snapshot, atom.timed.item, atom.timed.prescription, "item$index", day, index + 1)
            .copy(progressionRole = if (atom.origin.authorizedDemandId == squat.id) ProgressionRole.MAIN else ProgressionRole.ASSISTANCE) }
        assertEquals(ContinuitySplitPolicy.template(count).sorted(), first.filter { it.exerciseStableKey == "squat" }.map { it.setCount }.sorted())
        val initial = f.plan(first, listOf(1, 2, 3), 5)
        val origins = atoms.mapIndexed { index, pair -> "atom_$index" to pair.second.origin }.toMap()
        val sources = atoms.mapIndexed { index, pair -> "atom_$index" to pair.second.timed.item }.toMap()
        val completion = ResidualCompletion().complete(initial, snapshot, f.state(snapshot), listOf(AdaptationGap("LOWER_KNEE", "HIGH", "test")),
            authorized.map { AuthorizedPrescription(it.id, it.item, it.prescription, it.continuity) }, f.envelope(count + 2), f.atoms(initial), sources, true, f.safe, origins)
        assertTrue(completion.trace.exactShortfalls.all { it.shortfall == 0 })
        assertEquals(2.0, completion.trace.residuals.single { it.id == "LOWER_KNEE" }.requested, 0.0)
        val final = BoundedDayRebalancer().rebalance(completion, snapshot, f.state(snapshot), f.safe)
        val finalWeek = final.skeleton.items.filter { it.weekNumber == 1 }
        assertEquals(count, finalWeek.filter { it.exerciseStableKey == "squat" }.sumOf { it.setCount })
        assertEquals(2, finalWeek.filter { it.exerciseStableKey == "legpress" }.sumOf { it.setCount })
        assertEquals(completion.demand!!.residuals(completion.week!!.items), completion.demand.residuals(finalWeek))
        assertEquals(completion.week.items.associate { it.localId to it.copy(dayOfWeek = 1, orderIndex = 0) }, finalWeek.associate { it.localId to it.copy(dayOfWeek = 1, orderIndex = 0) })
        val content = finalWeek.filter { it.exerciseStableKey == "squat" }.flatMap { it.setPrescriptions }.map { it.copy(setIndex = 0) }
        assertEquals(squat.prescription.sets.map { it.copy(setIndex = 0) }, content)
        assertTrue(finalWeek.groupBy { it.dayOfWeek }.values.all { rows -> rows.map { it.exerciseStableKey }.distinct().size == rows.size })
    }
    @Test fun `four splits exactly and lower knee owner Q stays two through all stages`() = verify(4)
    @Test fun `five splits three plus two and never becomes six through all stages`() = verify(5)
}
