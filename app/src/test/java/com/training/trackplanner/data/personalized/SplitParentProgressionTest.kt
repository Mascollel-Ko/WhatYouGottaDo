package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class SplitParentProgressionTest {
    private val f = PostGenerationFixture
    internal fun plan(count: Int, days: Int): GeneratedProgramSkeleton {
        val snapshot = f.snapshot()
        val state = f.state(snapshot)
        val gaps = AdaptationGapAnalyzer().analyze(snapshot, state)
        val request = f.plan(listOf(f.row("press", 1))).request
        val template = PersonalizedProgramBuilder().build(snapshot, state, gaps, BlockIntentPlanner().decide(state, gaps),
            request.durationWeeks, request, PersonalizedPlanningAnswers(), null)
        val rx = f.rx(count).copy(sets = List(count) { ProgramSetPrescription(it + 1, 8, 50.0, 0) })
        val parent = AuthorizedSchedulingDemand("parent", f.source("press", count), rx, true)
        val chunks = ContinuitySplitPolicy.chunks(parent, days)
        val rows = chunks.mapIndexed { i, atom -> residualItem(snapshot, atom.timed.item, atom.timed.prescription, "chunk$i", i + 1, 1)
            .copy(progressionRole = ProgressionRole.MAIN) }
        val plan = f.plan(rows, (1..days).toList())
        val origins = plan.items.associate { row -> row.localId to chunks[row.dayOfWeek - 1].origin }
        return plan.copy(personalizedDecision = template.personalizedDecision!!.copy(authorizedScheduling =
            AuthorizedSchedulingTrace(listOf(parent), emptyList(), localOrigins = origins)))
    }
    @Test fun unevenChunksShareParentSessionWithoutFakingStyleAndUserChoicesSurvive() {
        for ((count, days) in listOf(7 to 3, 9 to 2, 9 to 3)) {
            val bound = bindSplitParentProgression(plan(count, days)).reconcileProgression(setOf("press"))
            assertEquals(1, bound.progressionSessions.size)
            assertEquals(1, bound.items.map { it.progressionBinding!!.sessionKey }.distinct().size)
            assertTrue(bound.items.all { it.progressionStyle.isBlank() && it.progressionRole == ProgressionRole.MAIN })
            assertEquals(bound, bindSplitParentProgression(bound))
            val row = bound.items.first()
            val separate = bound.configureProgressionSession(row.localId, ProgressionLinkMode.SEPARATE, null,
                ProgressionRole.MAIN, ProgressionMode.APP, ProgressionRule())
            assertEquals(2, separate.progressionSessions.size)
            assertEquals(separate, bindSplitParentProgression(separate))
            val linked = separate.configureProgressionSession(row.localId, ProgressionLinkMode.EXISTING,
                bound.progressionSessions.single().key, ProgressionRole.MAIN, ProgressionMode.APP, ProgressionRule())
            assertEquals(1, linked.progressionSessions.size)
            val off = linked.configureProgressionSession(row.localId, ProgressionLinkMode.OFF, null,
                ProgressionRole.MAIN, ProgressionMode.OFF, ProgressionRule())
            assertEquals(ProgressionLinkMode.OFF, off.items.first { it.localId == row.localId }.progressionBinding!!.linkMode)
        }
    }
}
