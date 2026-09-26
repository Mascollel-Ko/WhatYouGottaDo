package com.training.trackplanner.data.personalized

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builder-level proof that a same-owner B10 conflict is localized before placement. */
class StimulusMultiQualityConflictLocalizationTest {
    @Test
    fun existingConflictedOwnerIsPreservedWithoutGlobalFailureOrGenericFallback() {
        val baseSnapshot = PostGenerationFixture.snapshot()
        val snapshot = baseSnapshot.copy(
            allConfirmedSets = (1..8).flatMap { week ->
                listOf(
                    PlanningSetRecord(baseSnapshot.cutoff.minusDays((week * 7).toLong()), "press", "press", "운동", 1, 5, 70.0, 0, 8.0),
                    PlanningSetRecord(baseSnapshot.cutoff.minusDays((week * 7).toLong()), "press", "press", "운동", 2, 5, 70.0, 0, 8.0)
                )
            },
            profilePrimaryGoal = "STRENGTH_GAIN"
        )
        val state = PostGenerationFixture.state(snapshot)
        val request = com.training.trackplanner.data.ProgramSkeletonRequest(
            name = "B10.2 owner-local conflict",
            goal = com.training.trackplanner.data.ProgramGoal.STRENGTH,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.0,
            sportStrengthRatio = "AUTO",
            periodizationType = com.training.trackplanner.data.ProgramPeriodizationType.AUTO,
            durationWeeks = 1
        )
        val builder = PersonalizedProgramBuilder()
        val gaps = emptyList<AdaptationGap>()
        val intent = BlockIntentPlanner().decide(state, gaps)
        val control = builder.build(
            snapshot, state, gaps, intent, 1, request, PersonalizedPlanningAnswers(), null
        )
        val row = control.items.firstOrNull() ?: error("fixture must produce a control owner")
        val identity = StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)
        val controlPrescription = PlannedPrescription(row.prescription, row.setPrescriptions, row.restSeconds, row.weightSource)
        fun authorization(quality: com.training.trackplanner.data.TrainableQuality, reps: Int, weight: Double) =
            StimulusPrescriptionAuthorization(
                targetId = "QUALITY:${quality.name}", quality = quality,
                owner = StimulusPrescriptionOwner(identity.stableKey, identity.selectionRole),
                source = StimulusPrescriptionAuthorizationSource.CONTROL_EXISTING_DIRECT_IDENTITY,
                inputPrescription = controlPrescription,
                plannedCompatibility = null,
                authorizedPrescription = controlPrescription.copy(
                    text = "$reps reps",
                    sets = controlPrescription.sets.map { it.copy(reps = reps, weightKg = weight) }
                ),
                status = StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR
            )
        val authorizationPlan = StimulusPrescriptionAuthorizationPlan(
            listOf(
                authorization(com.training.trackplanner.data.TrainableQuality.STRENGTH, 5, 80.0),
                authorization(com.training.trackplanner.data.TrainableQuality.HYPERTROPHY, 8, 60.0)
            ),
            controlPrescriptions = mapOf(identity to controlPrescription)
        )
        assertEquals(StimulusPrescriptionOwnerExecutionDisposition.PRESERVE_CONTROL_OWNER,
            authorizationPlan.ownerExecutionDispositions.getValue(identity))
        var globalFailure = false
        val experimental = builder.build(
            snapshot, state, gaps, intent, 1, request, PersonalizedPlanningAnswers(), null,
            exactPrescriptionAuthorizationProvider = authorizationPlan.provider(),
            canonicalFailureEmitter = { _, _ ->
                globalFailure = true
                error("owner-local conflict must not emit a global canonical failure")
            }
        )
        val controlOwnerRows = control.items.filter { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) == identity }
        val experimentalOwnerRows = experimental.items.filter { StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) == identity }
        assertFalse(globalFailure)
        assertTrue(controlOwnerRows.isNotEmpty())
        assertEquals(controlOwnerRows.map { ownerSignature(it) }, experimentalOwnerRows.map { ownerSignature(it) })
        assertTrue(experimentalOwnerRows.all { it.setPrescriptions == controlOwnerRows.first().setPrescriptions })
        assertTrue(authorizationPlan.authorizedOwners.isEmpty())
        assertTrue(authorizationPlan.authorizedPrescriptions.size == 2)
    }

    private fun ownerSignature(item: com.training.trackplanner.data.ProgramSkeletonItem) = listOf(
        item.weekNumber, item.dayOfWeek, item.orderIndex, item.exerciseStableKey, item.selectionRole,
        item.setPrescriptions, item.prescription, item.reps, item.weightKg, item.seconds,
        item.restSeconds, item.weightSource
    )
}
