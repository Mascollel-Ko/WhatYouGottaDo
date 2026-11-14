package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramApplication
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

/**
 * Reconcile actual confirmed sets with the SAME program application and its
 * due prescription rows. Never equate a workout on the same date/exercise with
 * a completed program item without an explicit ProgramWorkoutLink.
 */
internal object BCoachLinkedExecutionReader {
    data class Summary(
        val sourceProgramStableKey: String,
        val applicationId: String,
        val dueLinkedEntries: Int,
        val duePlannedSets: Int,
        val confirmedSets: Int,
        val confirmedRatio: Double,
        val lastLinkedWeek: Int,
        val authority: String = "SAME_APPLICATION_LINKED_CONFIRMED_WORKOUT_SETS"
    )

    fun read(
        sourceProgramStableKey: String,
        asOf: LocalDate,
        workouts: List<WorkoutEntryWithSets>,
        applications: List<ProgramApplication>,
        links: List<ProgramWorkoutLink>,
        prescriptions: List<ProgramPrescriptionSet>
    ): Summary? {
        if (sourceProgramStableKey.isBlank()) return null
        val application = applications.filter { it.programStableKey == sourceProgramStableKey &&
            runCatching { LocalDate.parse(it.startDate) }.getOrNull()?.isAfter(asOf) == false }
            .maxWithOrNull(compareBy<ProgramApplication> { it.startDate }.thenBy { it.appliedAt })
            ?: return null
        val byEntry = workouts.associateBy { it.entry.id }
        val due = links.filter { it.applicationId == application.id &&
            it.sourceProgramStableKey == sourceProgramStableKey }.mapNotNull { link ->
            val record = byEntry[link.entryId] ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull()
                ?: return@mapNotNull null
            // Today's not-yet-completed workout is not an overdue missed session.
            if (!date.isBefore(asOf)) null else link to record
        }
        if (due.isEmpty()) return null
        val prescribed = prescriptions.groupBy(ProgramPrescriptionSet::entryId)
        var planned = 0
        var confirmed = 0
        due.forEach { (link, record) ->
            val targets = prescribed[link.entryId]
            val expected = targets?.count { it.plannedSetIndex != null }
                ?: record.sets.size
            if (expected <= 0) return@forEach
            planned += expected
            confirmed += record.sets.count { it.confirmed }
        }
        if (planned == 0) return null
        return Summary(
            sourceProgramStableKey = sourceProgramStableKey,
            applicationId = application.id,
            dueLinkedEntries = due.size,
            duePlannedSets = planned,
            confirmedSets = confirmed,
            confirmedRatio = (confirmed.toDouble() / planned).coerceIn(0.0, 1.0),
            lastLinkedWeek = due.maxOf { it.first.weekNumber }
        )
    }
}
