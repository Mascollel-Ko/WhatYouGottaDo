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
    fun `repetition-only sport drills enter task history without fabricated session minutes`() {
        val key = "sport_repetition_footwork"
        val genericKey = BadmintonPracticeCatalog.BADMINTON_STABLE_KEY
        val catalog = CanonicalBadmintonObjectiveCatalog.of(listOf(
            CanonicalBadmintonObjectiveRelation(
                "repetitions-footwork", key, BadmintonObjective.FOOTWORK,
                BadmintonObjectiveTransferLevel.DIRECT, "TEST",
                setOf("repetition-evidence"), "test"
            )
        ))
        val drills = mapOf(
            key to Exercise(stableKey = key, name = "Footwork", category = "스포츠"),
            genericKey to Exercise(stableKey = genericKey, name = "Badminton",
                category = "스포츠", activityKind = "SPORT_SESSION")
        )
        fun entry(id: Long, stableKey: String, reps: Int, seconds: Int, confirmed: Boolean) =
            WorkoutEntryWithSets(
                WorkoutEntry(id = id, date = "2026-11-10", exerciseStableKey = stableKey,
                    exerciseName = stableKey, category = "스포츠",
                    sessionStableKey = "sport-session-1"),
                listOf(WorkoutSet(id = id, entryId = id, setIndex = 1,
                    reps = reps, seconds = seconds, confirmed = confirmed))
            )
        val records = listOf(
            entry(1L, key, 6, 0, true),
            entry(2L, key, 10, 0, false),
            entry(3L, key, 3, 60, true),
            entry(4L, genericKey, 12, 3600, true)
        )
        val rows = V22HistoryInput.sportDrillHistory(records, catalog)
        assertEquals(2, rows.size)
        assertEquals(6.0, rows[0].reps!!, 0.0)
        assertTrue(rows[0].seconds == null)
        assertEquals(3.0, rows[1].reps!!, 0.0)
        assertEquals(60.0, rows[1].seconds!!, 0.0)
        assertTrue(rows.none { it.stableKey == genericKey })
        val sessions = V22HistoryInput.badmintonFromWorkoutHistory(records, drills, catalog)
        assertEquals(1, sessions.size)
        assertEquals(60.0, sessions.single().durationMinutes, 0.0)
        assertTrue(sessions.single().components.none { it.entryId == 1L })
    }

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
