package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramApplication
import com.training.trackplanner.data.ProgramProgressionItem
import com.training.trackplanner.data.ProgramProgressionTrack
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.CanonicalStrengthExposureCapability

/**
 * Recovers planned set intent only through the persisted application → source item → set link.
 * Exercise/date/repetition similarity is deliberately not used as a linkage fallback.
 */
internal object StrengthPlannedSetIntentResolver {
    fun resolve(
        history: List<WorkoutEntryWithSets>,
        programs: List<TrainingProgram>,
        programItems: List<TrainingProgramItem>,
        progressionTracks: List<ProgramProgressionTrack>,
        progressionItems: List<ProgramProgressionItem>,
        applications: List<ProgramApplication>,
        links: List<ProgramWorkoutLink>,
        prescriptions: List<ProgramPrescriptionSet>
    ): Map<StrengthSetSourceIdentity, StrengthSetIntentEvidence> {
        val programByStableKey = programs.groupBy(TrainingProgram::stableKey)
        val itemById = programItems.associateBy(TrainingProgramItem::id)
        val progressionByLogicalId = progressionItems.groupBy(ProgramProgressionItem::logicalItemId)
        val progressionTracksById = progressionTracks.groupBy(ProgramProgressionTrack::id)
        val applicationsById = applications.associateBy(ProgramApplication::id)
        val linkByEntryId = links.groupBy(ProgramWorkoutLink::entryId)
        val prescriptionByEntryAndSet = prescriptions.groupBy { it.entryId to it.setIndex }
        val result = linkedMapOf<StrengthSetSourceIdentity, StrengthSetIntentEvidence>()

        history.forEach workoutLoop@{ workout ->
            val link = linkByEntryId[workout.entry.id]?.singleOrNull() ?: return@workoutLoop
            val application = applicationsById[link.applicationId] ?: return@workoutLoop
            if (application.programStableKey != link.sourceProgramStableKey) return@workoutLoop
            val sourceProgram = programByStableKey[link.sourceProgramStableKey]?.singleOrNull() ?: return@workoutLoop
            val track = progressionTracksById[link.trackId]?.singleOrNull() ?: return@workoutLoop
            val binding = progressionByLogicalId[link.sourceItemId]?.singleOrNull() ?: return@workoutLoop
            val sourceItem = itemById[binding.programItemId] ?: return@workoutLoop
            if (sourceItem.programId != sourceProgram.id || sourceItem.exerciseStableKey != workout.entry.exerciseStableKey ||
                binding.signature.exerciseStableKey != workout.entry.exerciseStableKey || binding.trackId != link.trackId ||
                track.programStableKey != sourceProgram.stableKey || track.exerciseStableKey != workout.entry.exerciseStableKey) return@workoutLoop
            val selectionRole = sourceItem.selectionRole?.takeIf(String::isNotBlank) ?: return@workoutLoop

            workout.sets.filter { it.confirmed }.forEach setLoop@{ set ->
                val prescription = prescriptionByEntryAndSet[workout.entry.id to set.setIndex]?.singleOrNull()
                    ?: return@setLoop
                // A set without an exact original target is not promoted to planned intent.
                if (prescription.plannedSetIndex == null) return@setLoop
                val plannedStrength = selectionRole == CANONICAL_STRENGTH_SELECTION_ROLE &&
                    CanonicalStrengthExposureCapability.strengthPossible(workout.entry.exerciseStableKey) &&
                    prescription.plannedReps in 1..6
                result[StrengthSetSourceIdentity(workout.entry.id, set.setIndex)] = StrengthSetIntentEvidence(
                    intent = if (plannedStrength) StrengthSetIntent.PLANNED_STRENGTH else StrengthSetIntent.NOT_PLANNED_STRENGTH,
                    plannedReps = prescription.plannedReps,
                    selectionRole = selectionRole,
                    sourceProgramStableKey = link.sourceProgramStableKey,
                    sourceItemId = link.sourceItemId
                )
            }
        }
        return result
    }
}
