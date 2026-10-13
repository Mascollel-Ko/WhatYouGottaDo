package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalIncumbentSaveMutationGuardTest {
    private val token = CanonicalIncumbentSourceSnapshotToken("a".repeat(64))

    private fun item(
        key: String = "barbell_bench_press",
        role: String = "CANONICAL_STRENGTH",
        localId: String = "bench",
        setCount: Int = 2
    ) = ProgramSkeletonItem(
        localId = localId,
        weekNumber = 1,
        dayOfWeek = 2,
        orderIndex = 1,
        exerciseStableKey = key,
        exerciseName = "Bench press",
        category = "STRENGTH",
        restSeconds = 120,
        prescription = "$setCount x 5",
        setCount = setCount,
        reps = 5,
        weightKg = 60.0,
        seconds = 0,
        selectionReason = "B5 selected",
        weightSource = "EXACT_PERSONAL_HISTORY",
        selectionRole = role,
        setPrescriptions = List(setCount) { index ->
            ProgramSetPrescription(index + 1, 5, 60.0, 0, 7.0, ProgramLoadState.EXPLICIT_LOAD)
        }
    )

    private fun skeleton(vararg rows: ProgramSkeletonItem, sourceToken: CanonicalIncumbentSourceSnapshotToken = token) =
        GeneratedProgramSkeleton(
            suggestedName = "Generated",
            durationDays = 14,
            request = ProgramSkeletonRequest(
                name = "Generated", goal = ProgramGoal.STRENGTH, weeklyTrainingDays = 3,
                sessionMinutes = 45, availableEquipment = setOf("BARBELL", "BENCH"),
                excludedExerciseText = "", badmintonTransferRatio = 0.0, sportStrengthRatio = "AUTO",
                periodizationType = ProgramPeriodizationType.AUTO
            ),
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = emptyList(),
            items = rows.toList(),
            incumbentSourceSnapshotToken = sourceToken
        )

    private fun row(item: ProgramSkeletonItem) = TrainingProgramItem(
        id = 1, programId = 7, weekNumber = item.weekNumber, dayOfWeek = item.dayOfWeek,
        orderIndex = item.orderIndex, exerciseStableKey = item.exerciseStableKey,
        exerciseName = item.exerciseName, category = item.category, restSeconds = item.restSeconds,
        prescription = item.prescription, setCount = item.setCount, reps = item.reps,
        weightKg = item.weightKg, seconds = item.seconds, trainingSlot = item.trainingSlot,
        dayIntensity = item.dayIntensity, weightSource = item.weightSource,
        selectionRole = item.selectionRole
    )

    private fun sets(item: ProgramSkeletonItem) = item.setPrescriptions.map {
        TrainingProgramItemSet(
            programItemId = 1, setIndex = it.setIndex, reps = it.reps, weightKg = it.weightKg,
            seconds = it.seconds, targetRpeMin = it.targetRpeMin, loadState = it.loadState
        )
    }

    @Test
    fun unchangedPersistedPrescriptionCanBeSavedWithoutReplacementApproval() {
        val existing = item()
        CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
            persistedRows = listOf(row(existing)), persistedSets = sets(existing), persistedProgression = emptyList(),
            finalDraft = skeleton(existing), expectedSourceSnapshotToken = token
        )
    }

    @Test
    fun generatedCandidateCannotDeleteOrChangeSavedRowsWithoutExplicitUserApproval() {
        val existing = item()
        val changed = item(localId = "changed", setCount = 3)
        for (draft in listOf(skeleton(), skeleton(changed))) {
            val failure = runCatching {
                CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                    persistedRows = listOf(row(existing)), persistedSets = sets(existing), persistedProgression = emptyList(),
                    finalDraft = draft, expectedSourceSnapshotToken = token
                )
            }.exceptionOrNull()
            assertTrue(failure is UnexplainedCanonicalIncumbentMutationException)
            assertEquals("CANONICAL_INCUMBENT_REPLACEMENT_REQUIRES_EXPLICIT_USER_APPROVAL",
                (failure as UnexplainedCanonicalIncumbentMutationException).reasonCode)
        }
    }

    @Test
    fun explicitlyConfirmedPreviewCanReplacePersistedRowsUsingPersistedBaseline() {
        val existing = item()
        val replacement = item(key = "dumbbell_bench_press", role = "CANONICAL_STRENGTH", localId = "replacement")
        CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
            persistedRows = listOf(row(existing)), persistedSets = sets(existing), persistedProgression = emptyList(),
            finalDraft = skeleton(replacement), expectedSourceSnapshotToken = token, userApprovedReplacement = true
        )
    }

    @Test
    fun userApprovalCannotBypassStalePersistedSourceToken() {
        val existing = item()
        val replacement = item(localId = "replacement", setCount = 3)
        val failure = runCatching {
            CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                persistedRows = listOf(row(existing)), persistedSets = sets(existing), persistedProgression = emptyList(),
                finalDraft = skeleton(replacement, sourceToken = CanonicalIncumbentSourceSnapshotToken("b".repeat(64))),
                expectedSourceSnapshotToken = token, userApprovedReplacement = true
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("CANONICAL_INCUMBENT_SOURCE_SNAPSHOT_MISMATCH", failure?.message)
    }

    @Test
    fun sameKeyWithDifferentRoleIsARealSavedRowMutation() {
        val existing = item()
        val roleChanged = existing.copy(localId = "other-role", selectionRole = "COVERAGE_HORIZONTAL_PUSH")
        val failure = runCatching {
            CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                persistedRows = listOf(row(existing)), persistedSets = sets(existing), persistedProgression = emptyList(),
                finalDraft = skeleton(roleChanged), expectedSourceSnapshotToken = token
            )
        }.exceptionOrNull()
        assertTrue(failure is UnexplainedCanonicalIncumbentMutationException)
        assertEquals("barbell_bench_press", (failure as UnexplainedCanonicalIncumbentMutationException).stableKey)
        assertEquals("CANONICAL_STRENGTH", failure.selectionRole)
    }
}
