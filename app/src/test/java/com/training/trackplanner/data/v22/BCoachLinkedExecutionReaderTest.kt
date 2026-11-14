package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramApplication
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.ProgressionBase
import com.training.trackplanner.data.ProgressionRule
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BCoachLinkedExecutionReaderTest {
    private val date = LocalDate.of(2026, 11, 12)
    private fun record(id: Long, day: String, confirmations: List<Boolean>) =
        WorkoutEntryWithSets(
            WorkoutEntry(id = id, date = day, exerciseStableKey = "barbell_back_squat",
                exerciseName = "Squat", category = "근력운동"),
            confirmations.mapIndexed { index, confirmed ->
                WorkoutSet(entryId = id, setIndex = index + 1, confirmed = confirmed)
            }
        )
    private fun link(id: Long, app: String, program: String) =
        ProgramWorkoutLink(
            entryId = id, applicationId = app, sourceProgramStableKey = program,
            sourceItemId = "item-" + id, programName = "My plan",
            weekNumber = 1, dayOfWeek = 1, trackId = "track-" + id,
            trackLabel = "Squat", sequence = 1, role = ProgressionRole.AUTO,
            mode = ProgressionMode.APP, basePolicy = ProgressionBase.REVIEW,
            anchorSetIndex = null, needsReview = false, rule = ProgressionRule()
        )
    private fun prescription(id: Long, index: Int) = ProgramPrescriptionSet(
        entryId = id, setIndex = index,
        originalReps = 8, originalKg = 0.0, originalSeconds = 0
    )
    private val app = ProgramApplication(
        id = "application-a", programStableKey = "program-a",
        programName = "My plan", startDate = "2026-11-01"
    )

    @Test
    fun countsOnlyDueLinkedSetsFromTheRequestedApplication() {
        val rows = listOf(
            record(10, "2026-11-10", listOf(true, true, false)),
            record(11, "2026-11-12", listOf(false, false)),
            record(12, "2026-11-09", listOf(true, true, true))
        )
        val links = listOf(link(10, app.id, "program-a"),
            link(11, app.id, "program-a"), link(12, "other-app", "program-b"))
        val targets = (1..3).map { prescription(10, it) }
        val result = BCoachLinkedExecutionReader.read("program-a", date, rows,
            listOf(app), links, targets)!!
        assertEquals(1, result.dueLinkedEntries)
        assertEquals(3, result.duePlannedSets)
        assertEquals(2, result.confirmedSets)
        assertEquals(2.0 / 3.0, result.confirmedRatio, 1e-9)
        val evidence = BCoachJointBlockOptimizer.evidence(
            emptyList(), date, result.duePlannedSets, result.confirmedSets)
        assertEquals(result.confirmedRatio, evidence.plannedCompletionRate!!, 1e-9)
    }

    @Test
    fun unrelatedProgramCannotBecomeCompletionEvidence() {
        assertNull(BCoachLinkedExecutionReader.read("program-b", date,
            listOf(record(10, "2026-11-10", listOf(true))),
            listOf(app), listOf(link(10, app.id, "program-a")), emptyList()))
    }
}
