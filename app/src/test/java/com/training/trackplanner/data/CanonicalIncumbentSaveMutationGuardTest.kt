package com.training.trackplanner.data

import com.training.trackplanner.data.personalized.StimulusExperimentalChangeAttribution
import com.training.trackplanner.data.personalized.StimulusExperimentalChangeAttributionSource
import com.training.trackplanner.data.personalized.StimulusExperimentalReadinessAudit
import com.training.trackplanner.data.personalized.StimulusExperimentalReadinessStatus
import com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity
import com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityDecision
import com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityStatus
import com.training.trackplanner.data.personalized.StimulusProductionCutoverScope
import com.training.trackplanner.data.personalized.StimulusProductionProgramSource
import com.training.trackplanner.data.personalized.StimulusProductionRoutingDecision
import com.training.trackplanner.data.personalized.StimulusProductionRoutingMode
import com.training.trackplanner.data.personalized.StimulusUserApprovedReplacementEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalIncumbentSaveMutationGuardTest {
    private val owner = StimulusPrescriptionOwnerIdentity("barbell_bench_press", "CANONICAL_STRENGTH")
    private val token = CanonicalIncumbentSourceSnapshotToken("a".repeat(64))

    private fun item(
        localId: String = "bench",
        order: Int = 1,
        setCount: Int = 2
    ) = ProgramSkeletonItem(
        localId = localId,
        weekNumber = 1,
        dayOfWeek = 2,
        orderIndex = order,
        exerciseStableKey = owner.stableKey,
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
        selectionRole = owner.selectionRole,
        setPrescriptions = List(setCount) { index ->
            ProgramSetPrescription(index + 1, 5, 60.0, 0, 7.0, ProgramLoadState.EXPLICIT_LOAD)
        }
    )

    private fun skeleton(vararg rows: ProgramSkeletonItem) = GeneratedProgramSkeleton(
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
        incumbentSourceSnapshotToken = token
    )

    private fun row(item: ProgramSkeletonItem, id: Long = 1) = TrainingProgramItem(
        id = id, programId = 7, weekNumber = item.weekNumber, dayOfWeek = item.dayOfWeek,
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

    private fun authorizedMutationDraft(old: ProgramSkeletonItem, replacement: ProgramSkeletonItem): GeneratedProgramSkeleton {
        val draft = skeleton(replacement)
        val evidence = CanonicalIncumbentSaveMutationEvidence(
            sourceSnapshotToken = token,
            finalDraftFingerprint = CanonicalIncumbentSaveMutationGuard.draftFingerprint(draft),
            controlRows = listOf(old),
            readiness = StimulusExperimentalReadinessAudit(
                status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
                changeAttributions = listOf(StimulusExperimentalChangeAttribution(
                    stableKey = owner.stableKey,
                    selectionRole = owner.selectionRole,
                    source = StimulusExperimentalChangeAttributionSource.B6_SAFE_REPAIRED_PRESCRIPTION,
                    targetIds = listOf("QUALITY:STRENGTH"),
                    reasonCodes = listOf("EXACT_B6_REPAIR"),
                    evidenceSources = listOf("B4_B5_B6_EXACT_OWNER_WEEK")
                )),
                materializationIntegrityPassed = true,
                changeProvenanceClosed = true,
                collateralRegressionFree = true
            ),
            cutover = StimulusProductionCutoverAuthorityDecision(
                status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                scope = StimulusProductionCutoverScope.STRENGTH_V1,
                authorizedOwnerIdentities = listOf(owner),
                reasonCodes = listOf("EXACT_B8_SCOPE"),
                b7Status = StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW
            ),
            route = StimulusProductionRoutingDecision(
                mode = StimulusProductionRoutingMode.B8_STRENGTH_V1_ACTIVE,
                selectedSource = StimulusProductionProgramSource.B8_STRENGTH_V1,
                b8Status = StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                b8Scope = StimulusProductionCutoverScope.STRENGTH_V1,
                reasonCodes = emptyList(),
                productionRoutingActive = true
            )
        )
        return draft.copy(incumbentSaveMutationEvidence = evidence)
    }

    private fun authorizedReplacementDraft(
        old: ProgramSkeletonItem,
        replacement: ProgramSkeletonItem,
        includeExactEdge: Boolean = true
    ): GeneratedProgramSkeleton {
        val replacementOwner = StimulusPrescriptionOwnerIdentity(
            replacement.exerciseStableKey, replacement.selectionRole
        )
        val keepRow = old.copy(
            localId = "kept-squat",
            exerciseStableKey = "barbell_back_squat",
            selectionRole = "CANONICAL_STRENGTH"
        )
        val edge = StimulusUserApprovedReplacementEdge(
            optionId = "proposal-1",
            targetId = "QUALITY:STRENGTH",
            displacedControlOwner = owner,
            keepOwner = StimulusPrescriptionOwnerIdentity(keepRow.exerciseStableKey, keepRow.selectionRole),
            replacementOwner = replacementOwner,
            controlRows = listOf(old),
            keepRows = listOf(keepRow),
            replacementRows = listOf(replacement)
        )
        val draft = skeleton(replacement)
        val base = authorizedMutationDraft(old, replacement)
        val baseEvidence = checkNotNull(base.incumbentSaveMutationEvidence)
        val readiness = baseEvidence.readiness.copy(
            changeAttributions = listOf(StimulusExperimentalChangeAttribution(
                stableKey = owner.stableKey,
                selectionRole = owner.selectionRole,
                source = StimulusExperimentalChangeAttributionSource.USER_APPROVED_EXACT_EXERCISE_REPLACEMENT,
                targetIds = listOf(edge.targetId),
                reasonCodes = listOf("USER_APPROVED_EXACT_REPLACEMENT"),
                evidenceSources = listOf("EXACT_WEEK_MATERIALIZATION"),
                replacementOwner = replacementOwner,
                replacementEvidenceId = edge.optionId
            )),
            userApprovedReplacementEdges = if (includeExactEdge) listOf(edge) else emptyList()
        )
        val cutover = baseEvidence.cutover.copy(
            authorizedOwnerIdentities = listOf(replacementOwner)
        )
        val evidence = baseEvidence.copy(
            finalDraftFingerprint = CanonicalIncumbentSaveMutationGuard.draftFingerprint(draft),
            readiness = readiness,
            cutover = cutover
        )
        return draft.copy(incumbentSaveMutationEvidence = evidence)
    }

    @Test fun `unchanged exact owner week can be saved without mutation permit`() {
        val old = item()
        CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
            persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
            finalDraft = skeleton(old), expectedSourceSnapshotToken = token
        )
    }

    @Test fun `added candidate does not count as removal of an existing owner`() {
        val added = item(localId = "added", setCount = 3)
        CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
            persistedRows = emptyList(), persistedSets = emptyList(), persistedProgression = emptyList(),
            finalDraft = skeleton(added), expectedSourceSnapshotToken = token
        )
    }

    @Test fun `missing or changed owner week fails closed without exact B7 B8 B9 evidence`() {
        val old = item()
        val removed = runCatching {
            CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
                finalDraft = skeleton(), expectedSourceSnapshotToken = token
            )
        }.exceptionOrNull()
        assertTrue(removed is UnexplainedCanonicalIncumbentMutationException)
        assertEquals("CANONICAL_INCUMBENT_MUTATION_WITHOUT_B7_B8_B9_AUTHORITY",
            (removed as UnexplainedCanonicalIncumbentMutationException).reasonCode)

        val changed = item(localId = "changed", setCount = 3)
        val changedFailure = runCatching {
            CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
                finalDraft = skeleton(changed), expectedSourceSnapshotToken = token
            )
        }.exceptionOrNull()
        assertTrue(changedFailure is UnexplainedCanonicalIncumbentMutationException)
    }

    @Test fun `same stable key under a different role is not the same incumbent owner`() {
        val old = item()
        val roleChanged = old.copy(localId = "other-role", selectionRole = "COVERAGE_HORIZONTAL_PUSH")
        val failure = runCatching {
            CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
                finalDraft = skeleton(roleChanged), expectedSourceSnapshotToken = token
            )
        }.exceptionOrNull()
        assertTrue(failure is UnexplainedCanonicalIncumbentMutationException)
        assertEquals(owner.selectionRole, (failure as UnexplainedCanonicalIncumbentMutationException).selectionRole)
    }

    @Test fun `exact B7 owner cause and B8 B9 authorization permit a validated same-owner change`() {
        val old = item()
        val changed = item(localId = "changed", order = 2, setCount = 3)
        CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
            persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
            finalDraft = authorizedMutationDraft(old, changed), expectedSourceSnapshotToken = token
        )
    }

    @Test fun `stale final draft fingerprint cannot reuse prior mutation evidence`() {
        val old = item()
        val changed = item(localId = "changed", setCount = 3)
        val approved = authorizedMutationDraft(old, changed)
        val stale = approved.copy(items = listOf(changed.copy(setCount = 4, reps = 5)))
        val failure = runCatching {
            CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
                finalDraft = stale, expectedSourceSnapshotToken = token
            )
        }.exceptionOrNull()
        assertTrue(failure is UnexplainedCanonicalIncumbentMutationException)
        assertEquals("CANONICAL_INCUMBENT_MUTATION_WITHOUT_B7_B8_B9_AUTHORITY",
            (failure as UnexplainedCanonicalIncumbentMutationException).reasonCode)
    }

    @Test fun `eligible B7 without B8 route cannot overwrite a saved owner`() {
        val old = item()
        val changed = item(localId = "changed", setCount = 3)
        val authorized = authorizedMutationDraft(old, changed)
        val evidence = checkNotNull(authorized.incumbentSaveMutationEvidence)
        val noCutover = authorized.copy(incumbentSaveMutationEvidence = evidence.copy(
            cutover = evidence.cutover.copy(
                status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED,
                reasonCodes = listOf("B8_REJECTED")
            ),
            route = evidence.route.copy(
                selectedSource = StimulusProductionProgramSource.CONTROL,
                b8Status = StimulusProductionCutoverAuthorityStatus.CONTROL_REQUIRED,
                productionRoutingActive = false,
                reasonCodes = listOf("CONTROL_REQUIRED")
            )
        ))
        val failure = runCatching {
            CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
                finalDraft = noCutover, expectedSourceSnapshotToken = token
            )
        }.exceptionOrNull()
        assertTrue(failure is UnexplainedCanonicalIncumbentMutationException)
    }

    @Test fun `B7 and B8 cannot authorize owner outside exact owner list`() {
        val old = item()
        val changed = item(localId = "changed", setCount = 3)
        val authorized = authorizedMutationDraft(old, changed)
        val evidence = checkNotNull(authorized.incumbentSaveMutationEvidence)
        val wrongOwner = authorized.copy(incumbentSaveMutationEvidence = evidence.copy(
            cutover = evidence.cutover.copy(authorizedOwnerIdentities = emptyList())
        ))
        val failure = runCatching {
            CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
                finalDraft = wrongOwner, expectedSourceSnapshotToken = token
            )
        }.exceptionOrNull()
        assertTrue(failure is UnexplainedCanonicalIncumbentMutationException)
        assertEquals("CANONICAL_INCUMBENT_OWNER_NOT_EXACTLY_AUTHORIZED_BY_B8",
            (failure as UnexplainedCanonicalIncumbentMutationException).reasonCode)
    }

    @Test fun `exact user approved replacement edge closes only its owner week removal`() {
        val old = item()
        val replacement = item(localId = "db-bench", setCount = 2).copy(
            exerciseStableKey = "ex_3a7d3eda",
            exerciseName = "Dumbbell bench press"
        )
        CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
            persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
            finalDraft = authorizedReplacementDraft(old, replacement), expectedSourceSnapshotToken = token
        )
    }

    @Test fun `replacement label without matching exact edge cannot close incumbent removal`() {
        val old = item()
        val replacement = item(localId = "db-bench", setCount = 2).copy(
            exerciseStableKey = "ex_3a7d3eda",
            exerciseName = "Dumbbell bench press"
        )
        val failure = runCatching {
            CanonicalIncumbentSaveMutationGuard.requireNoUnprovenMutation(
                persistedRows = listOf(row(old)), persistedSets = sets(old), persistedProgression = emptyList(),
                finalDraft = authorizedReplacementDraft(old, replacement, includeExactEdge = false),
                expectedSourceSnapshotToken = token
            )
        }.exceptionOrNull()
        assertTrue(failure is UnexplainedCanonicalIncumbentMutationException)
        assertEquals("CANONICAL_INCUMBENT_OWNER_NOT_EXACTLY_AUTHORIZED_BY_B8",
            (failure as UnexplainedCanonicalIncumbentMutationException).reasonCode)
    }
}
