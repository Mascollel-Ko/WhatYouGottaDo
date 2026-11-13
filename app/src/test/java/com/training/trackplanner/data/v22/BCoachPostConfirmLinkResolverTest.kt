package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.ProgressionBase
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BCoachPostConfirmLinkResolverTest {
    private val asOf = LocalDate.of(2026, 11, 13)
    private fun entry(
        id: Long,
        date: String,
        allConfirmed: Boolean,
        exercise: String = "barbell_back_squat",
        kg: Double = if (allConfirmed) 135.0 else 0.0,
        rep: Int = 6,
        rpe: Double? = if (allConfirmed) 8.0 else null
    ) = WorkoutEntryWithSets(
        entry = WorkoutEntry(
            id = id, date = date,
            exerciseStableKey = exercise, exerciseName = "Squat",
            category = "STRENGTH", sessionStableKey = "s-$id", createdAt = id
        ),
        sets = (1..3).map { i ->
            WorkoutSet(id=id * 10 + i, entryId=id, setIndex=i,
                weightKg=kg, reps=rep, confirmed=allConfirmed, rpe=rpe,
                loadState = if (kg > 0.0) ProgramLoadState.EXPLICIT_LOAD
                    else ProgramLoadState.USER_CALIBRATION_REQUIRED)
        }
    )
    private fun link(
        id: Long, week: Int, day: Int = 5,
        app: String = "app-1", exerciseTrack: String = "track-squat",
        sequence: Int = week
    ) = ProgramWorkoutLink(
        entryId = id, applicationId = app, sourceProgramStableKey = "program-1",
        sourceItemId = "item-$id", programName = "Training",
        weekNumber = week, dayOfWeek = day, trackId = exerciseTrack,
        trackLabel = "Squat", sequence = sequence,
        mode = ProgressionMode.APP, basePolicy = ProgressionBase.REVIEW,
        needsReview = true
    )
    private fun resolve(
        records: List<WorkoutEntryWithSets> = listOf(
            entry(1, "2026-11-13", true),
            entry(2, "2026-11-20", false)
        ),
        links: List<ProgramWorkoutLink> = listOf(link(1,1), link(2,2))
    ) = BCoachPostConfirmLinkResolver.resolve(
        sourceEntryId = 1L, asOf = asOf, records = records, links = links
    )

    @Test fun `completed real 135kg six reps three RPE 8 sets find next-week squat`() {
        val result = resolve()
        assertEquals("LINKED_FUTURE_TARGET_READY", result.status)
        assertEquals(2L, result.targetEntryId)
    }

    @Test fun `one unchecked set prohibits auto progression`() {
        val partial = entry(1, "2026-11-13", true).let {
            it.copy(sets = it.sets.mapIndexed { index, set ->
                if (index == 2) set.copy(confirmed=false) else set
            })
        }
        val result=resolve(records=listOf(partial,entry(2,"2026-11-20",false)))
        assertEquals("WAITING_FOR_CONFIRMED_RPE_SETS", result.status)
        assertNull(result.targetEntryId)
    }

    @Test fun `RPE null or uncalibrated weight cannot be used as evidence`() {
        for (source in listOf(
            entry(1,"2026-11-13",true,rpe=null),
            entry(1,"2026-11-13",true,kg=0.0)
        )) {
            assertEquals("WAITING_FOR_CONFIRMED_RPE_SETS",
                resolve(records=listOf(source,entry(2,"2026-11-20",false))).status)
        }
    }

    @Test fun `source needs a real program application link`() {
        assertEquals("SOURCE_NOT_PROGRAM_LINKED", resolve(links=listOf(link(2,2))).status)
    }

    @Test fun `next week must match application and weekday`() {
        assertEquals("NO_NEXT_WEEK_SQUAT",resolve(
            links=listOf(link(1,1),link(2,2,app="other-application"))).status)
        assertEquals("NO_NEXT_WEEK_SQUAT",resolve(
            links=listOf(link(1,1),link(2,2,day=2))).status)
        assertEquals("NO_NEXT_WEEK_SQUAT",resolve(
            links=listOf(link(1,1),link(2,3))).status)
    }

    @Test fun `multiple future squats refuse automatic selection`() {
        val many=resolve(
            records=listOf(entry(1,"2026-11-13",true),
                entry(2,"2026-11-20",false),entry(3,"2026-11-20",false)),
            links=listOf(link(1,1,sequence=1),link(2,2,sequence=2),link(3,2,sequence=3))
        )
        assertEquals("MULTIPLE_NEXT_WEEK_SQUATS",many.status)
        assertNull(many.targetEntryId)
    }

    @Test fun `never overwrite future workout that already has a confirmed set`() {
        val started=entry(2,"2026-11-20",false).let { workout ->
            workout.copy(sets=workout.sets.mapIndexed { i,set ->
                if (i==0) set.copy(confirmed=true) else set
            })
        }
        assertEquals("TARGET_ALREADY_STARTED",resolve(
            records=listOf(entry(1,"2026-11-13",true),started)).status)
    }

    @Test fun `date must remain after source and not before present day`() {
        assertEquals("TARGET_DATE_NOT_FUTURE",resolve(records=listOf(
            entry(1,"2026-11-13",true),entry(2,"2026-11-12",false))).status)
    }

    @Test fun `unrelated exercises never become next squat candidate`() {
        assertEquals("NO_NEXT_WEEK_SQUAT",resolve(records=listOf(
            entry(1,"2026-11-13",true),
            entry(2,"2026-11-20",false,exercise="barbell_deadlift"))).status)
    }
}
