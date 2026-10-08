package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramApplication
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramProgressionItem
import com.training.trackplanner.data.ProgramProgressionTrack
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.ProgressionBase
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgressionRule
import com.training.trackplanner.data.ProgressionSignature
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthPlannedSetIntentResolverTest {
    @Test
    fun onlyExactPersistedProgramSetLinkRestoresPlannedStrengthIntent() {
        val sourceProgram = TrainingProgram(id = 10, stableKey = "program-source", name = "Source", durationDays = 28)
        val sourceItem = TrainingProgramItem(
            id = 20,
            programId = sourceProgram.id,
            weekNumber = 1,
            dayOfWeek = 1,
            orderIndex = 1,
            exerciseStableKey = "barbell_bench_press",
            exerciseName = "Bench press",
            category = "STRENGTH",
            selectionRole = CANONICAL_STRENGTH_SELECTION_ROLE
        )
        val progressionItem = ProgramProgressionItem(
            programItemId = sourceItem.id,
            logicalItemId = "logical-source-item",
            trackId = "track",
            signature = ProgressionSignature("barbell_bench_press", 1, "5", 80.0, null, null)
        )
        val progressionTrack = ProgramProgressionTrack(
            id = "track",
            programStableKey = sourceProgram.stableKey,
            exerciseStableKey = sourceItem.exerciseStableKey,
            label = "Bench press"
        )
        val application = ProgramApplication("application", sourceProgram.stableKey, sourceProgram.name, "2026-10-01")
        val actual = WorkoutEntryWithSets(
            WorkoutEntry(50, "2026-10-02", "barbell_bench_press", "Bench press", "STRENGTH", sessionStableKey = "session"),
            listOf(WorkoutSet(id = 70, entryId = 50, setIndex = 1, reps = 7, weightKg = 80.0, confirmed = true))
        )
        val link = ProgramWorkoutLink(
            entryId = actual.entry.id,
            applicationId = application.id,
            sourceProgramStableKey = sourceProgram.stableKey,
            sourceItemId = progressionItem.logicalItemId,
            programName = sourceProgram.name,
            weekNumber = 1,
            dayOfWeek = 1,
            trackId = "track",
            trackLabel = "Bench",
            sequence = 1,
            role = ProgressionRole.MAIN,
            mode = ProgressionMode.APP,
            basePolicy = ProgressionBase.UNIFORM,
            anchorSetIndex = null,
            needsReview = false,
            rule = ProgressionRule()
        )
        val prescription = ProgramPrescriptionSet(
            entryId = actual.entry.id,
            setIndex = 1,
            originalReps = 5,
            originalKg = 80.0,
            originalSeconds = 0,
            plannedReps = 5,
            plannedKg = 80.0,
            plannedSetIndex = 1
        )

        val resolved = StrengthPlannedSetIntentResolver.resolve(
            history = listOf(actual),
            programs = listOf(sourceProgram),
            programItems = listOf(sourceItem),
            progressionTracks = listOf(progressionTrack),
            progressionItems = listOf(progressionItem),
            applications = listOf(application),
            links = listOf(link),
            prescriptions = listOf(prescription)
        ).getValue(StrengthSetSourceIdentity(actual.entry.id, 1))

        assertEquals(StrengthSetIntent.PLANNED_STRENGTH, resolved.intent)
        assertEquals(5, resolved.plannedReps)
        val actualClassification = RealizedStimulusClassifier.classify(
            RealizedStimulusInput(
                stableKey = actual.entry.exerciseStableKey,
                date = LocalDate.parse(actual.entry.date),
                activityKind = PlannedActivityKind.RESISTANCE,
                reps = actual.sets.single().reps,
                resolvedLoadKg = null,
                rpe = null,
                directQualities = emptySet(),
                reviewedIdentity = true,
                strengthSetIntentEvidence = resolved
            )
        )
        assertEquals(StrengthExposureAssessment.OVERPERFORMED, actualClassification.strengthExposureAssessment)
    }

    @Test
    fun missingOrMismatchedSourceLinkNeverInfersPlanIntentFromExerciseAndReps() {
        val actual = WorkoutEntryWithSets(
            WorkoutEntry(50, "2026-10-02", "barbell_bench_press", "Bench press", "STRENGTH"),
            listOf(WorkoutSet(id = 70, entryId = 50, setIndex = 1, reps = 7, confirmed = true))
        )
        val resolved = StrengthPlannedSetIntentResolver.resolve(
            history = listOf(actual),
            programs = emptyList(),
            programItems = emptyList(),
            progressionTracks = emptyList(),
            progressionItems = emptyList(),
            applications = emptyList(),
            links = emptyList(),
            prescriptions = emptyList()
        )
        assertTrue(resolved.isEmpty())
    }
}
