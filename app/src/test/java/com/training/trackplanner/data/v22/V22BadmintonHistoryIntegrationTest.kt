package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.BadmintonPracticeCatalog
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V22BadmintonHistoryIntegrationTest {
    @Test
    fun `v22 counterexamples 31 to 34 keep real RPE and avoid generic plus drill duplication`() {
        val sessionId = "badminton-2026-11-14"
        val genericKey = BadmintonPracticeCatalog.BADMINTON_STABLE_KEY
        val drillKey = "test_footwork_drill"
        val exercises = mapOf(
            genericKey to Exercise(
                stableKey = genericKey, name = "Badminton", category = "스포츠",
                activityKind = "SPORT_SESSION"
            ),
            drillKey to Exercise(
                stableKey = drillKey, name = "Footwork", category = "스포츠",
                systemicLoadWeight = 0.6, neuralSpeedWeight = 1.0,
                decelerationWeight = 1.0, elasticSscWeight = 0.8
            )
        )
        val catalog = CanonicalBadmintonObjectiveCatalog.of(listOf(
            CanonicalBadmintonObjectiveRelation(
                relationId = "test-footwork",
                exerciseStableKey = drillKey,
                objective = BadmintonObjective.FOOTWORK,
                transferLevel = BadmintonObjectiveTransferLevel.DIRECT,
                provenance = "TEST",
                evidenceRelationKeys = setOf("test-evidence"),
                reviewReason = "test"
            )
        ))
        fun record(id: Long, key: String, seconds: Int) = WorkoutEntryWithSets(
            WorkoutEntry(
                id = id, date = "2026-11-14", exerciseStableKey = key,
                exerciseName = exercises.getValue(key).name, category = "스포츠",
                rpe = 8.0, sessionStableKey = sessionId
            ),
            listOf(WorkoutSet(id = id, entryId = id, setIndex = 1, seconds = seconds,
                rpe = 8.0, confirmed = true))
        )

        val session = V22HistoryInput.badmintonFromWorkoutHistory(
            listOf(record(1L, genericKey, 3_600), record(2L, drillKey, 1_200)), exercises, catalog
        ).single()

        assertEquals(60.0, session.durationMinutes, 0.0)
        assertEquals(63.0, session.practiceLoad, 0.0)
        assertEquals(2, session.components.size)
        assertTrue(session.components.any { it.stableKey == drillKey && it.rpe == 8.0 })
    }
}
