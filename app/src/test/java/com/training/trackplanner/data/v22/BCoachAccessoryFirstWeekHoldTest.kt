package com.training.trackplanner.data.v22

import com.training.trackplanner.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BCoachAccessoryFirstWeekHoldTest {
    private val today = LocalDate.of(2026, 11, 14)
    private fun entry(id: Long, week: Int, confirmed: Boolean, kg: Double,
                      key: String = "ex_curl", manual: Boolean = false) =
        WorkoutEntryWithSets(
            WorkoutEntry(id = id, date = "2026-11-${14 + (week - 1) * 7}",
                exerciseStableKey = key, exerciseName = "Curl", category = "STRENGTH",
                sessionStableKey = "s-$id"),
            (1..3).map { index -> WorkoutSet(id = id * 10 + index,
                entryId = id, setIndex = index, reps = 8, weightKg = kg,
                confirmed = confirmed, manualWeight = manual,
                loadState = if (confirmed) ProgramLoadState.EXPLICIT_LOAD
                    else ProgramLoadState.USER_CALIBRATION_REQUIRED) }
        )
    private fun link(id: Long, week: Int, track: String = "curl", app: String = "app") =
        ProgramWorkoutLink(entryId = id, applicationId = app, sourceProgramStableKey = "b",
            sourceItemId = "item-$id", programName = "B", weekNumber = week,
            dayOfWeek = 1, trackId = track, trackLabel = "Curl", sequence = week,
            role = ProgressionRole.ASSISTANCE, mode = ProgressionMode.APP,
            basePolicy = ProgressionBase.REVIEW, anchorSetIndex = null,
            needsReview = true, rule = ProgressionRule())
    private fun prescriptions(id: Long) = (1..3).map { index ->
        ProgramPrescriptionSet(entryId = id, setIndex = index, originalReps = 8,
            originalKg = 0.0, originalSeconds = 0,
            originalLoadState = ProgramLoadState.USER_CALIBRATION_REQUIRED)
    }

    @Test fun firstCompletedAccessoryLoadIsRepeatedToAllUneditedFutureWeeks() {
        val first = entry(1, 1, true, 12.0)
        val next = entry(2, 2, false, 0.0)
        val last = entry(3, 3, false, 0.0)
        assertEquals(12.0, BCoachAccessoryFirstWeekHold.initialLoad(first)!!, 1e-9)
        val found = BCoachAccessoryFirstWeekHold.futureTargets(
            link(1, 1), listOf(link(1, 1), link(2, 2), link(3, 3)),
            listOf(first, next, last), prescriptions(2) + prescriptions(3), today
        )
        assertEquals(listOf(2L, 3L), found.map { it.first.entry.id })
    }

    @Test fun manuallyEditedAndConfirmedFutureWorkoutsAreProtected() {
        val first = entry(1, 1, true, 12.0)
        val changed = entry(2, 2, false, 13.0)
        val started = entry(3, 3, false, 0.0).let { record ->
            record.copy(sets = record.sets.mapIndexed { i, set ->
                if (i == 0) set.copy(confirmed = true) else set
            })
        }
        val found = BCoachAccessoryFirstWeekHold.futureTargets(
            link(1, 1), listOf(link(1, 1), link(2, 2), link(3, 3)),
            listOf(first, changed, started), prescriptions(2) + prescriptions(3), today
        )
        assertTrue(found.isEmpty())
    }

    @Test fun independentTrackAndLaterWeekNeverInheritUnrelatedFirstLoad() {
        val first = entry(1, 1, true, 12.0)
        val next = entry(2, 2, false, 0.0)
        assertTrue(BCoachAccessoryFirstWeekHold.futureTargets(
            link(1, 1), listOf(link(1, 1), link(2, 2, track = "manual-separated")),
            listOf(first, next), prescriptions(2), today
        ).isEmpty())
        assertTrue(BCoachAccessoryFirstWeekHold.futureTargets(
            link(2, 2), listOf(link(1, 1), link(2, 2)),
            listOf(first, next), prescriptions(2), today
        ).isEmpty())
    }

    @Test fun mixedOrMissingFirstLoadCannotBeFlattenedToOneKg() {
        val first = entry(1, 1, true, 12.0)
        assertNull(BCoachAccessoryFirstWeekHold.initialLoad(
            first.copy(sets = first.sets.mapIndexed { i, set ->
                if (i == 1) set.copy(weightKg = 10.0) else set
            })))
        assertNull(BCoachAccessoryFirstWeekHold.initialLoad(entry(1, 1, false, 0.0)))
    }
}
