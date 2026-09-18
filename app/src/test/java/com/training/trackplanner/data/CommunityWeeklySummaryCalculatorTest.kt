package com.training.trackplanner.data

import com.training.trackplanner.analysis.badminton.BadmintonPracticeCatalog
import org.junit.Assert.assertEquals
import org.json.JSONObject
import org.junit.Test
import java.time.LocalDate

class CommunityWeeklySummaryCalculatorTest {
    @Test fun sessionsGroupByStableSessionIdentityInsteadOfExerciseRows() {
        val exercises = listOf(
            Exercise(stableKey = "squat", name = "Squat", category = "STRENGTH", activityKind = "STRENGTH"),
            Exercise(stableKey = BadmintonPracticeCatalog.BADMINTON_STABLE_KEY, name = "Court", category = "SPORT", activityKind = "SPORT_SESSION")
        )
        val strengthSession = "session-strength"
        val badmintonSession = "session-badminton"
        val rows = listOf(
            row("2026-09-15", "squat", strengthSession, 1, confirmed = true),
            row("2026-09-15", "squat", strengthSession, 2, confirmed = true),
            row("2026-09-16", BadmintonPracticeCatalog.BADMINTON_STABLE_KEY, badmintonSession, 1, confirmed = true, seconds = 1800),
            row("2026-09-16", BadmintonPracticeCatalog.BADMINTON_STABLE_KEY, badmintonSession, 2, confirmed = true, seconds = 600),
            row("2026-09-17", "squat", "session-strength-2", 1, confirmed = true)
        )
        val payload = CommunityWeeklySummaryCalculator.payload(
            rows,
            exercises,
            LocalDate.parse("2026-09-15"),
            LocalDate.parse("2026-09-17"),
            RuntimeExerciseMetadataCatalog.EMPTY
        )
        assertEquals(3, payload.getInt("trainingDays"))
        assertEquals(2, payload.getInt("strengthSessionCount"))
        assertEquals(3, payload.getInt("confirmedStrengthSetCount"))
        assertEquals(1, payload.getInt("badmintonSessionCount"))
        assertEquals(40, payload.getInt("badmintonMinutes"))
    }

    private fun row(date: String, exerciseStableKey: String, session: String, id: Int, confirmed: Boolean, seconds: Int = 0) =
        WorkoutEntryWithSets(
            WorkoutEntry(
                id = id.toLong(), date = date, exerciseStableKey = exerciseStableKey,
                exerciseName = exerciseStableKey, category = "", sessionStableKey = session
            ),
            listOf(WorkoutSet(id = id.toLong(), entryId = id.toLong(), setIndex = 1, confirmed = confirmed, seconds = seconds))
        )
}
