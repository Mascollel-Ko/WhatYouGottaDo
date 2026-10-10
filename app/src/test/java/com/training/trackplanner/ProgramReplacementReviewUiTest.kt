package com.training.trackplanner

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.training.trackplanner.data.*
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR")
class ProgramReplacementReviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun candidateNeedsWholeProgramValidationAndSeparateExplicitApply() {
        val source = item("source", "barbell_curl", "Barbell Curl")
        val request = ProgramSkeletonRequest(
            name = "Preview",
            goal = ProgramGoal.BODYBUILDING,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            availableEquipment = setOf("DUMBBELL"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.0,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO
        )
        val option = ProgramReplacementCandidateOption(
            optionId = "option-1",
            targetId = "QUALITY:HYPERTROPHY",
            sourceRows = listOf(ProgramReplacementRowIdentity.from(source)),
            sourceItems = listOf(source),
            sourceStableKey = source.exerciseStableKey,
            sourceSelectionRole = source.selectionRole,
            candidateStableKey = "incline_dumbbell_curl",
            candidateSelectionRole = source.selectionRole,
            reasonCodes = listOf("B5_ELIGIBLE_CANDIDATE"),
            b5CandidateEligible = true
        )
        val sameTargetAlternative = option.copy(
            optionId = "option-2",
            candidateStableKey = "cable_curl"
        )
        val sourceDraft = skeleton(request, source)
        val review = ProgramReplacementReview(
            "session-1", ProgramReplacementReviewFingerprint.create(sourceDraft), listOf(option, sameTargetAlternative)
        )
        val base = sourceDraft.copy(replacementReview = review)
        val savedByApply = mutableListOf<GeneratedProgramSkeleton>()
        var validatedDraft: GeneratedProgramSkeleton? = null

        compose.setContent {
            TrainingTrackPlannerTheme {
                var draft by remember { mutableStateOf(base) }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProgramSkeletonPreview(
                skeleton = draft,
                exercises = emptyList(),
                metadataByExerciseId = emptyMap(),
                onSkeletonChange = { savedByApply.add(it); draft = it },
                onValidateReplacementSelection = { current, selected, callback ->
                    val selectedOption = current.replacementReview!!.options.single { it.optionId in selected }
                    val replacement = item("replacement", selectedOption.candidateStableKey, "Incline Dumbbell Curl")
                    val changed = current.copy(items = listOf(replacement))
                    val resultingFingerprint = ProgramReplacementReviewFingerprint.create(changed)
                    val validatedReview = requireNotNull(current.replacementReview).copy(
                        validation = ProgramReplacementCombinationValidation(
                            selectedOptionIds = selected,
                            sourceDraftFingerprint = requireNotNull(current.replacementReview).sourceDraftFingerprint,
                            resultingDraftFingerprint = resultingFingerprint,
                            status = ProgramReplacementValidationStatus.VALIDATED,
                            b7Status = com.training.trackplanner.data.personalized.StimulusExperimentalReadinessStatus.ELIGIBLE_FOR_FUTURE_CUTOVER_REVIEW,
                            b8Status = com.training.trackplanner.data.personalized.StimulusProductionCutoverAuthorityStatus.AUTHORIZED_FOR_BOUNDED_CUTOVER,
                            b9SelectedSource = com.training.trackplanner.data.personalized.StimulusProductionProgramSource.B8_HYPERTROPHY_V1,
                            causalEvidence = listOf(ProgramReplacementCausalEvidence(
                                optionId = selectedOption.optionId,
                                targetId = selectedOption.targetId,
                                displacedControlOwner = com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity(
                                    source.exerciseStableKey, "LEGACY_CONTROL_ROLE"
                                ),
                                keepOwner = com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity(
                                    source.exerciseStableKey, source.selectionRole
                                ),
                                replacementOwner = com.training.trackplanner.data.personalized.StimulusPrescriptionOwnerIdentity(
                                    selectedOption.candidateStableKey, selectedOption.candidateSelectionRole
                                ),
                                controlRows = listOf(source.copy(selectionRole = "LEGACY_CONTROL_ROLE")),
                                keepRows = listOf(source),
                                replacementRows = listOf(replacement)
                            )),
                            proofId = "proof"
                        ),
                        options = current.replacementReview!!.options.map { option ->
                            if (option.optionId == selectedOption.optionId) {
                                option.copy(b6Validated = true, b7Validated = true, b8Validated = true)
                            } else option
                        }
                    )
                    val validated = changed.copy(replacementReview = validatedReview)
                    validatedDraft = validated
                    callback(Result.success(validated))
                }
                )
                }
            }
        }

        compose.onNodeWithTag("replacement-option-option-1").performClick()
        compose.onNodeWithTag("replacement-option-option-2").performClick()
        compose.onNodeWithTag("replacement-validate").assertExists().performScrollTo().performClick()
        compose.onNodeWithText("전체 프로그램 검증을 통과했습니다. 아래 처방을 확인한 뒤 명시적으로 적용하세요.").assertExists()
        compose.onAllNodesWithText("확인한 훈련 목표: 근비대", substring = true).assertCountEquals(3)
        assertEquals("barbell_curl", base.items.single().exerciseStableKey)
        assertEquals(0, savedByApply.size)
        assertEquals(setOf("option-2"), validatedDraft?.replacementReview?.validation?.selectedOptionIds)
        assertEquals(validatedDraft?.replacementReview?.validation?.resultingDraftFingerprint,
            validatedDraft?.let(ProgramReplacementReviewFingerprint::create))

        compose.onNodeWithTag("replacement-apply").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals("cable_curl", savedByApply.single().items.single().exerciseStableKey)
        assertEquals(setOf("option-2"), savedByApply.single().replacementReview?.appliedOptionIds)

        compose.onNodeWithTag("replacement-revert").performScrollTo().assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("barbell_curl", savedByApply.last().items.single().exerciseStableKey)
        assertEquals(emptySet<String>(), savedByApply.last().replacementReview?.appliedOptionIds)
        assertEquals(ProgramReplacementReviewFingerprint.create(savedByApply.last()),
            savedByApply.last().replacementReview?.sourceDraftFingerprint)
        compose.onNodeWithTag("replacement-option-option-1").performScrollTo().performClick()
        compose.onNodeWithTag("replacement-validate").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("replacement-apply").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("incline_dumbbell_curl", savedByApply.last().items.single().exerciseStableKey)
        assertEquals(setOf("option-1"), savedByApply.last().replacementReview?.appliedOptionIds)
    }

    @Test
    fun noReplacementReviewDoesNotRenderReviewPanel() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                ProgramSkeletonPreview(
                skeleton = skeleton(
                    ProgramSkeletonRequest(
                        name = "Preview", goal = ProgramGoal.BODYBUILDING, weeklyTrainingDays = 3,
                        sessionMinutes = 60, availableEquipment = emptySet(), excludedExerciseText = "",
                        badmintonTransferRatio = 0.0, sportStrengthRatio = "AUTO",
                        periodizationType = ProgramPeriodizationType.AUTO
                    ),
                    item("source", "barbell_curl", "Barbell Curl")
                ),
                exercises = emptyList(), metadataByExerciseId = emptyMap(), onSkeletonChange = {}
                )
            }
        }
        compose.onNodeWithTag("replacement-review").assertDoesNotExist()
    }

    private fun skeleton(request: ProgramSkeletonRequest, item: ProgramSkeletonItem) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = 7, request = request,
        periodizationType = ProgramPeriodizationType.AUTO, weekPlans = emptyList(),
        items = listOf(item), weekDaySchedule = mapOf(1 to setOf(1, 3, 5))
    )

    private fun item(id: String, key: String, name: String) = ProgramSkeletonItem(
        localId = id, weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
        exerciseStableKey = key, exerciseName = name, category = "Arms", restSeconds = 90,
        prescription = "3 × 10", setCount = 3, reps = 10, weightKg = 20.0, seconds = 0,
        selectionReason = "test", weightSource = "HISTORY",
        selectionRole = "CANONICAL_STIMULUS_QUALITY_HYPERTROPHY"
    )
}
