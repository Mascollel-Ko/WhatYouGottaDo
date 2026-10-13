package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgramReplacementReviewTest {
    @Test
    fun draftFingerprintIgnoresOnlyTheEditableProgramName() {
        val base = skeleton()
        assertEquals(
            ProgramReplacementReviewFingerprint.create(base),
            ProgramReplacementReviewFingerprint.create(base.copy(
                request = base.request.copy(name = "Renamed")
            ))
        )
        assertNotEquals(
            ProgramReplacementReviewFingerprint.create(base),
            ProgramReplacementReviewFingerprint.create(base.copy(items = listOf(item(reps = 11))))
        )
        assertNotEquals(
            ProgramReplacementReviewFingerprint.create(base),
            ProgramReplacementReviewFingerprint.create(base.copy(weekDaySchedule = mapOf(1 to setOf(2))))
        )
        assertNotEquals(
            ProgramReplacementReviewFingerprint.create(base),
            ProgramReplacementReviewFingerprint.create(base.copy(weekPlans = listOf(
                ProgramWeekPlan(1, "BUILD", 1.0, 1.0, 2, 4.0, 2, 2, false)
            )))
        )
    }

    @Test
    fun replacementRequestFingerprintIgnoresNameButTracksUserConstraints() {
        val request = skeleton().request
        assertEquals(
            ProgramReplacementReviewFingerprint.requestConstraints(request),
            ProgramReplacementReviewFingerprint.requestConstraints(request.copy(name = "Renamed"))
        )
        assertNotEquals(
            ProgramReplacementReviewFingerprint.requestConstraints(request),
            ProgramReplacementReviewFingerprint.requestConstraints(request.copy(sessionMinutes = request.sessionMinutes + 15))
        )
    }

    @Test
    fun replacementMustNotChangeUnselectedPhysicalOwnerWeeks() {
        val keep = skeleton(listOf(item(), item(localId = "other", key = "lat_pulldown")))
        val option = ProgramReplacementCandidateOption(
            optionId = "option", targetId = "QUALITY:HYPERTROPHY",
            sourceRows = listOf(ProgramReplacementRowIdentity.from(keep.items.first())),
            sourceItems = listOf(keep.items.first()), sourceStableKey = "barbell_curl",
            sourceSelectionRole = keep.items.first().selectionRole,
            candidateStableKey = "incline_dumbbell_curl", candidateSelectionRole = keep.items.first().selectionRole,
            reasonCodes = listOf("B5_DIRECT_TARGET_CANDIDATE"), b5CandidateEligible = true
        )
        val allowedChange = keep.copy(items = listOf(item(localId = "new", key = "incline_dumbbell_curl"), keep.items.last()))
        assertEquals(true, replacementLeavesUnselectedOwnerWeeksUnchanged(keep, allowedChange, listOf(option)))
        val unrelatedMutation = allowedChange.copy(items = allowedChange.items.map {
            if (it.exerciseStableKey == "lat_pulldown") it.copy(setCount = it.setCount + 1) else it
        })
        assertEquals(false, replacementLeavesUnselectedOwnerWeeksUnchanged(keep, unrelatedMutation, listOf(option)))
    }

    @Test
    fun roleOnlyChangeCannotBecomeAnExerciseReplacement() {
        val source = item()
        assertThrows(IllegalArgumentException::class.java) {
            ProgramReplacementCandidateOption(
                optionId = "same-exercise-other-role",
                targetId = "QUALITY:HYPERTROPHY",
                sourceRows = listOf(ProgramReplacementRowIdentity.from(source)),
                sourceItems = listOf(source),
                sourceStableKey = source.exerciseStableKey,
                sourceSelectionRole = source.selectionRole,
                candidateStableKey = source.exerciseStableKey,
                candidateSelectionRole = "ANOTHER_ROLE",
                reasonCodes = listOf("ROLE_ONLY"),
                b5CandidateEligible = true
            )
        }
    }

    @Test
    fun sourcePrescriptionMustBeBoundToExactRows() {
        val source = item()
        assertThrows(IllegalArgumentException::class.java) {
            ProgramReplacementCandidateOption(
                optionId = "wrong-row",
                targetId = "QUALITY:HYPERTROPHY",
                sourceRows = listOf(ProgramReplacementRowIdentity.from(source)),
                sourceItems = listOf(item(localId = "different-row")),
                sourceStableKey = source.exerciseStableKey,
                sourceSelectionRole = source.selectionRole,
                candidateStableKey = "incline_dumbbell_curl",
                candidateSelectionRole = "CANONICAL_STIMULUS_QUALITY_HYPERTROPHY",
                reasonCodes = listOf("B5_CANDIDATE"),
                b5CandidateEligible = true
            )
        }
    }

    @Test
    fun replacementSaveRequiresAnExplicitApplyAfterValidation() {
        val base = skeleton()
        val source = base.items.single()
        val option = ProgramReplacementCandidateOption(
            optionId = "option", targetId = "QUALITY:HYPERTROPHY",
            sourceRows = listOf(ProgramReplacementRowIdentity.from(source)), sourceItems = listOf(source),
            sourceStableKey = source.exerciseStableKey, sourceSelectionRole = source.selectionRole,
            candidateStableKey = "incline_dumbbell_curl", candidateSelectionRole = source.selectionRole,
            reasonCodes = listOf("B5_DIRECT_TARGET_CANDIDATE"), b5CandidateEligible = true,
            b6Validated = true, b7Validated = true, b8Validated = true
        )
        val replacementRow = item(localId = "replacement", key = option.candidateStableKey)
        val replacementDraft = base.copy(items = listOf(replacementRow))
        val unselectedValidation = ProgramReplacementCombinationValidation(
            selectedOptionIds = setOf(option.optionId),
            sourceDraftFingerprint = ProgramReplacementReviewFingerprint.create(base),
            resultingDraftFingerprint = ProgramReplacementReviewFingerprint.create(replacementDraft),
            status = ProgramReplacementValidationStatus.VALIDATED,
            b7Status = com.training.trackplanner.data.personalized.StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
            b8Status = com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
            b9SelectedSource = com.training.trackplanner.data.personalized.StimulusProductionProgramSource.B8_HYPERTROPHY_V1,
            causalEvidence = listOf(ProgramReplacementCausalEvidence(
                optionId = option.optionId,
                targetId = option.targetId,
                displacedControlOwner = com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity(
                    source.exerciseStableKey, "LEGACY_CONTROL_ROLE"
                ),
                keepOwner = com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity(
                    source.exerciseStableKey, source.selectionRole
                ),
                replacementOwner = com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity(
                    option.candidateStableKey, option.candidateSelectionRole
                ),
                controlRows = listOf(source.copy(selectionRole = "LEGACY_CONTROL_ROLE")),
                keepRows = listOf(source),
                replacementRows = listOf(replacementRow)
            )),
            proofId = "proof"
        )
        val previewNotApplied = base.copy(replacementReview = ProgramReplacementReview(
            "session", ProgramReplacementReviewFingerprint.create(base), listOf(option), unselectedValidation
        ))
        assertEquals("REPLACEMENT_NOT_EXPLICITLY_APPLIED", org.junit.Assert.assertThrows(
            ProgramReplacementSaveRejectedException::class.java
        ) { ProgramReplacementSaveGate.requireSaveable(previewNotApplied) }.reasonCode)
        ProgramReplacementSaveGate.requireSaveable(base)

        val applied = replacementDraft.copy(replacementReview = previewNotApplied.replacementReview!!.copy(
            appliedOptionIds = setOf(option.optionId)
        ))
        ProgramReplacementSaveGate.requireSaveable(applied)
        val restored = ProgramReplacementReviewReverter.restoreKeepDraft(applied)
        assertEquals(listOf(source), restored.items)
        assertEquals(emptySet<String>(), restored.replacementReview?.appliedOptionIds)
        assertEquals(null, restored.replacementReview?.validation)
        assertEquals(ProgramReplacementReviewFingerprint.create(base), ProgramReplacementReviewFingerprint.create(restored))
        ProgramReplacementSaveGate.requireSaveable(restored)
        val evidenceTampered = applied.copy(replacementReview = applied.replacementReview!!.copy(
            validation = applied.replacementReview!!.validation!!.copy(
                causalEvidence = applied.replacementReview!!.validation!!.causalEvidence.map { evidence ->
                    evidence.copy(replacementRows = listOf(item(localId = "replacement", key = option.candidateStableKey, reps = 9)))
                }
            )
        ))
        assertEquals("REPLACEMENT_CAUSAL_EVIDENCE_MISMATCH", org.junit.Assert.assertThrows(
            ProgramReplacementSaveRejectedException::class.java
        ) { ProgramReplacementSaveGate.requireSaveable(evidenceTampered) }.reasonCode)
        val staleApplied = applied.copy(items = listOf(item(reps = 12)))
        assertEquals("REPLACEMENT_VALIDATION_STALE", org.junit.Assert.assertThrows(
            ProgramReplacementSaveRejectedException::class.java
        ) { ProgramReplacementSaveGate.requireSaveable(staleApplied) }.reasonCode)
        assertEquals("REPLACEMENT_VALIDATION_STALE", org.junit.Assert.assertThrows(
            ProgramReplacementSaveRejectedException::class.java
        ) { ProgramReplacementReviewReverter.restoreKeepDraft(staleApplied) }.reasonCode)
    }

    private fun skeleton(items: List<ProgramSkeletonItem> = listOf(item())) = GeneratedProgramSkeleton(
        suggestedName = "Plan",
        durationDays = 7,
        request = ProgramSkeletonRequest(
            name = "Plan",
            goal = ProgramGoal.BODYBUILDING,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            availableEquipment = setOf("dumbbell"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.0,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO
        ),
        periodizationType = ProgramPeriodizationType.AUTO,
        weekPlans = emptyList(),
        items = items,
        weekDaySchedule = mapOf(1 to setOf(1, 3, 5))
    )

    private fun item(localId: String = "row-1", reps: Int = 10, key: String = "barbell_curl") = ProgramSkeletonItem(
        localId = localId,
        weekNumber = 1,
        dayOfWeek = 1,
        orderIndex = 1,
        exerciseStableKey = key,
        exerciseName = "Barbell Curl",
        category = "Arms",
        restSeconds = 90,
        prescription = "3x10",
        setCount = 3,
        reps = reps,
        weightKg = 20.0,
        seconds = 0,
        selectionReason = "history",
        weightSource = "HISTORY",
        selectionRole = "CANONICAL_STIMULUS_QUALITY_HYPERTROPHY"
    )
}
