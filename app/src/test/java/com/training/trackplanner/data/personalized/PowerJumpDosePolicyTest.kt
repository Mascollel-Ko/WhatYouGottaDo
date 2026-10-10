package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.PhysicalQualityMode
import com.training.trackplanner.data.PhysicalQualityRegion
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PowerJumpDosePolicyTest {
    @Test
    fun lowerCapUsesResistanceAndCourtBandsAndUnknownCourtIsNotZero() {
        assertEquals(6, PowerJumpIntegratedDosePolicy.lowerPowerJumpCap(5, BadmintonActivityBand.LOW))
        assertEquals(2, PowerJumpIntegratedDosePolicy.lowerPowerJumpCap(12, BadmintonActivityBand.HIGH))
        assertEquals(0, PowerJumpIntegratedDosePolicy.lowerPowerJumpCap(18, BadmintonActivityBand.HIGH))
        assertEquals(4, PowerJumpIntegratedDosePolicy.lowerPowerJumpCap(5, BadmintonActivityBand.UNKNOWN))
        assertEquals(BadmintonActivityBand.UNKNOWN, PowerJumpIntegratedDosePolicy.badmintonBand(null))
    }

    @Test
    fun upperPowerCapDoesNotUseBadmintonBand() {
        assertEquals(6, PowerJumpIntegratedDosePolicy.upperPowerCap(0))
        assertEquals(4, PowerJumpIntegratedDosePolicy.upperPowerCap(8))
        assertEquals(2, PowerJumpIntegratedDosePolicy.upperPowerCap(16))
    }

    @Test
    fun lowerCapImplementsEveryUserApprovedResistanceAndCourtCell() {
        val expected = mapOf(
            (ResistanceSetBand.LOW to BadmintonActivityBand.LOW) to 6,
            (ResistanceSetBand.LOW to BadmintonActivityBand.MODERATE) to 4,
            (ResistanceSetBand.LOW to BadmintonActivityBand.HIGH) to 2,
            (ResistanceSetBand.MODERATE to BadmintonActivityBand.LOW) to 4,
            (ResistanceSetBand.MODERATE to BadmintonActivityBand.MODERATE) to 2,
            (ResistanceSetBand.MODERATE to BadmintonActivityBand.HIGH) to 2,
            (ResistanceSetBand.HIGH to BadmintonActivityBand.LOW) to 2,
            (ResistanceSetBand.HIGH to BadmintonActivityBand.MODERATE) to 2,
            (ResistanceSetBand.HIGH to BadmintonActivityBand.HIGH) to 0
        )
        val representatives = mapOf(ResistanceSetBand.LOW to 7, ResistanceSetBand.MODERATE to 8, ResistanceSetBand.HIGH to 16)
        expected.forEach { (cell, cap) ->
            assertEquals("$cell", cap, PowerJumpIntegratedDosePolicy.lowerPowerJumpCap(
                representatives.getValue(cell.first), cell.second
            ))
        }
    }

    @Test
    fun recentBadmintonProjectionUsesOnlyObservedCompleteNonExcludedWeeks() {
        val cutoff = LocalDate.parse("2026-10-10")
        val observations = listOf(
            LocalDate.parse("2026-09-13") to 500.0, // week starts outside the rolling window
            LocalDate.parse("2026-09-14") to 50.0,
            LocalDate.parse("2026-09-20") to 50.0,
            LocalDate.parse("2026-09-21") to 100.0,
            LocalDate.parse("2026-09-27") to 100.0,
            LocalDate.parse("2026-09-28") to 200.0,
            LocalDate.parse("2026-10-04") to 200.0,
            LocalDate.parse("2026-10-05") to 999.0 // current incomplete week
        )
        assertEquals(
            listOf(100.0, 200.0, 400.0),
            recentCompleteBadmintonWeekMinutes(cutoff, observations)
        )
        assertEquals(
            listOf(100.0, 400.0),
            recentCompleteBadmintonWeekMinutes(cutoff, observations, setOf(LocalDate.parse("2026-09-21")))
        )
        assertNull(recentCompleteBadmintonWeekMinutes(cutoff, emptyList()))
    }

    @Test
    fun needIsRequiredAndColdStartOnlyApprovesTwoRatherThanFillingCap() {
        val evidence = evidence(lower = 5, upper = 0, minutes = listOf(60.0))
        val need = need("lower-power", PowerJumpDoseKind.POWER, PowerJumpBodyRegion.LOWER)
        val result = PowerJumpIntegratedDosePolicy.resolve(need, evidence)
        assertEquals(PowerJumpDoseStatus.AUTHORIZED, result.status)
        assertEquals(2, result.approvedWeeklySetUnits)
        assertEquals(6, result.applicableWeeklyCap)
        assertEquals(StimulusTargetNumericAuthority.USER_APPROVED_PROJECT_POLICY, result.numericAuthority)

        val absent = PowerJumpIntegratedDosePolicy.resolve(
            need.copy(needDecision = TrainingNeedDecision.NO_EXTRA_NEED), evidence
        )
        assertEquals(PowerJumpDoseStatus.NO_NEED, absent.status)
        assertEquals(0, absent.approvedWeeklySetUnits)
    }

    @Test
    fun powerAndJumpSharePerRegionAndWholeWeekBudget() {
        val evidence = evidence(lower = 12, upper = 0, minutes = listOf(420.0))
        val needs = listOf(
            need("lower-power", PowerJumpDoseKind.POWER, PowerJumpBodyRegion.LOWER, TargetPriority.PRIMARY),
            need("lower-jump", PowerJumpDoseKind.JUMP_LANDING, PowerJumpBodyRegion.LOWER, TargetPriority.SECONDARY),
            need("upper-power", PowerJumpDoseKind.POWER, PowerJumpBodyRegion.UPPER, TargetPriority.SECONDARY)
        )
        val results = PowerJumpIntegratedDosePolicy.resolveAll(needs, evidence).associateBy { it.targetId }
        assertEquals(2, results.getValue("lower-power").approvedWeeklySetUnits)
        assertEquals(0, results.getValue("lower-jump").approvedWeeklySetUnits)
        assertEquals(PowerJumpDoseStatus.CAPACITY_ZERO, results.getValue("lower-jump").status)
        assertEquals(2, results.getValue("upper-power").approvedWeeklySetUnits)
        assertTrue(results.values.sumOf { it.approvedWeeklySetUnits } <= PowerJumpIntegratedDosePolicy.MAX_TOTAL_NEW_WEEKLY_SETS)
    }

    @Test
    fun existingPowerJumpSetsConsumeCapButAreNeverRemoved() {
        val existing = (1..5).map { ExistingPowerJumpSet("existing-$it", setOf(PowerJumpBodyRegion.LOWER)) }
        val result = PowerJumpIntegratedDosePolicy.resolve(
            need("lower-power", PowerJumpDoseKind.POWER, PowerJumpBodyRegion.LOWER),
            evidence(lower = 5, upper = 0, minutes = listOf(60.0), existing = existing)
        )
        assertEquals(5, result.existingAuthorizedSetUnits)
        assertEquals(0, result.approvedWeeklySetUnits)
        assertEquals(PowerJumpDoseStatus.LESS_THAN_MINIMUM_SET_DOSE, result.status)
    }

    @Test
    fun directResistanceProjectionDeduplicatesPhysicalSetsAndIgnoresSupportive() {
        val exact = relation("barbell_back_squat", TrainableQuality.STRENGTH, StimulusCapabilityLevel.DIRECT_CAPABILITY,
            PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT)
        val hypertrophy = relation("barbell_back_squat", TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.DIRECT_CAPABILITY,
            PhysicalQualityRegion.LOWER, PhysicalQualityMode.SQUAT)
        val supportive = relation("barbell_bench_press", TrainableQuality.HYPERTROPHY, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY,
            PhysicalQualityRegion.CHEST, PhysicalQualityMode.HORIZONTAL_PRESS)
        val result = PowerJumpIntegratedDosePolicy.projectResistanceWorkload(
            plannedSets = listOf(
                PlannedQualityResistanceSet("week1-day1-set1", "barbell_back_squat", TrainableQuality.STRENGTH),
                PlannedQualityResistanceSet("week1-day1-set1", "barbell_back_squat", TrainableQuality.HYPERTROPHY),
                PlannedQualityResistanceSet("week1-day1-set2", "barbell_bench_press", TrainableQuality.HYPERTROPHY)
            ),
            physicalRelations = listOf(exact, hypertrophy, supportive),
            projectionComplete = true
        )
        assertEquals(1, result.lowerDirectSets)
        assertEquals(0, result.upperDirectSets)
    }

    @Test
    fun incompleteResistanceProjectionRemainsUnknown() {
        val result = PowerJumpIntegratedDosePolicy.projectResistanceWorkload(emptyList(), emptyList(), projectionComplete = false)
        assertEquals(PowerJumpWorkloadStatus.UNKNOWN, result.status)
        assertNull(result.lowerDirectSets)
    }

    @Test
    fun exactDirectBodyweightPowerRequestGetsApprovedShapeWithoutExternalLoad() {
        val decision = PowerJumpPrescriptionShapeResolver.resolve(
            PowerJumpPrescriptionRequest(
                PowerJumpDoseKind.POWER, "bilateral_bodyweight_power_fixture",
                relation("bilateral_bodyweight_power_fixture", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                    PhysicalQualityRegion.UPPER_PUSH, PhysicalQualityMode.BALLISTIC),
                PowerJumpLoadMode.BODYWEIGHT_CONFIRMED, exactApprovedSetCount = 2
            )
        )
        assertEquals(PowerJumpPrescriptionStatus.AUTHORIZED, decision.status)
        val prescription = requireNotNull(decision.prescription)
        assertEquals(2, prescription.sets.size)
        assertTrue(prescription.sets.all { it.reps == 4 })
        assertTrue(prescription.sets.all { it.loadState == ProgramLoadState.NOT_APPLICABLE })
        assertEquals(120, prescription.restSeconds)
    }

    @Test
    fun exactPowerLoadWithoutUserConfirmationRequiresInputAndOnlySuggestsSameExerciseLoad() {
        val decision = PowerJumpPrescriptionShapeResolver.resolve(
            PowerJumpPrescriptionRequest(
                PowerJumpDoseKind.POWER, "exact_power_lift",
                relation("exact_power_lift", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                    PhysicalQualityRegion.UPPER_PUSH, PhysicalQualityMode.BALLISTIC),
                PowerJumpLoadMode.GENERAL_RESISTANCE_POWER,
                exactApprovedSetCount = 3,
                exactExerciseOneRmKg = 100.0,
                exactOneRmReliable = true
            )
        )
        assertEquals(PowerJumpPrescriptionStatus.USER_INPUT_REQUIRED, decision.status)
        assertEquals(50.0, decision.suggestedExternalLoadKg!!, 0.0)
        assertTrue(decision.prescription!!.sets.all { it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED })
        assertTrue(decision.prescription!!.sets.all { it.weightKg == 0.0 })
    }

    @Test
    fun equipmentOnlyExternalPowerLoadRequiresUserInputWithoutGenericOneRmSuggestion() {
        val decision = PowerJumpPrescriptionShapeResolver.resolve(
            PowerJumpPrescriptionRequest(
                PowerJumpDoseKind.POWER, "exact_direct_external_power_owner",
                relation("exact_direct_external_power_owner", TrainableQuality.POWER,
                    StimulusCapabilityLevel.DIRECT_CAPABILITY, PhysicalQualityRegion.UPPER_PUSH,
                    PhysicalQualityMode.BALLISTIC),
                PowerJumpLoadMode.EXTERNAL_LOAD_USER_CONFIRMATION,
                exactApprovedSetCount = 2,
                exactExerciseOneRmKg = 100.0,
                exactOneRmReliable = true
            )
        )
        assertEquals(PowerJumpPrescriptionStatus.USER_INPUT_REQUIRED, decision.status)
        assertNull(decision.suggestedExternalLoadKg)
        assertTrue(decision.prescription!!.sets.all {
            it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED && it.weightKg == 0.0
        })
        assertTrue(decision.reasonCodes.contains("EXTERNAL_LOAD_REQUIRES_EXPLICIT_USER_CALIBRATION"))
    }

    @Test
    fun exactPowerCleanMaySuggestSeventyPercentOfItsOwnReliableOneRmForUserConfirmation() {
        val decision = PowerJumpPrescriptionShapeResolver.resolve(
            PowerJumpPrescriptionRequest(
                PowerJumpDoseKind.POWER, "exact_power_clean",
                relation("exact_power_clean", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                    PhysicalQualityRegion.POSTERIOR_CHAIN, PhysicalQualityMode.BALLISTIC),
                PowerJumpLoadMode.POWER_CLEAN,
                exactApprovedSetCount = 2,
                exactExerciseOneRmKg = 80.0,
                exactOneRmReliable = true
            )
        )
        assertEquals(PowerJumpPrescriptionStatus.USER_INPUT_REQUIRED, decision.status)
        assertEquals(56.0, decision.suggestedExternalLoadKg!!, 0.0)
        assertTrue(decision.prescription!!.sets.all { it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED })
    }

    @Test
    fun jumpSquatNeverBorrowsARegularSquatOneRmAndRequiresExactUserLoad() {
        val base = PowerJumpPrescriptionRequest(
            PowerJumpDoseKind.POWER, "jump_squat_variant",
            relation("jump_squat_variant", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                PhysicalQualityRegion.LOWER, PhysicalQualityMode.PLYOMETRIC),
            PowerJumpLoadMode.JUMP_SQUAT,
            exactApprovedSetCount = 2,
            exactExerciseOneRmKg = 100.0,
            exactOneRmReliable = true
        )
        val unconfirmed = PowerJumpPrescriptionShapeResolver.resolve(base)
        assertEquals(PowerJumpPrescriptionStatus.USER_INPUT_REQUIRED, unconfirmed.status)
        assertNull(unconfirmed.suggestedExternalLoadKg)
        assertTrue(unconfirmed.reasonCodes.contains("NORMAL_SQUAT_1RM_NOT_TRANSFERRED"))

        val confirmed = PowerJumpPrescriptionShapeResolver.resolve(base.copy(userConfirmedExternalLoadKg = 10.0))
        assertEquals(PowerJumpPrescriptionStatus.AUTHORIZED, confirmed.status)
        assertTrue(confirmed.prescription!!.sets.all {
            it.weightKg == 10.0 && it.loadState == ProgramLoadState.EXPLICIT_LOAD
        })
    }

    @Test
    fun exactLandingRelationRequestPreservesLandingCountAndRejectsLossySideSemantics() {
        val relation = relation("bilateral_bodyweight_landing_fixture", TrainableQuality.REACTIVE_STRENGTH_SSC,
            StimulusCapabilityLevel.DIRECT_CAPABILITY, PhysicalQualityRegion.LOWER, PhysicalQualityMode.LANDING)
        val authorized = PowerJumpPrescriptionShapeResolver.resolve(
            PowerJumpPrescriptionRequest(PowerJumpDoseKind.JUMP_LANDING, "bilateral_bodyweight_landing_fixture", relation,
                PowerJumpLoadMode.BODYWEIGHT_CONFIRMED, exactApprovedSetCount = 2)
        )
        assertEquals(PowerJumpPrescriptionStatus.AUTHORIZED, authorized.status)
        assertEquals(4, authorized.prescription!!.sets.first().reps)

        val lossy = PowerJumpPrescriptionShapeResolver.resolve(
            PowerJumpPrescriptionRequest(PowerJumpDoseKind.JUMP_LANDING, "bilateral_bodyweight_landing_fixture", relation,
                PowerJumpLoadMode.BODYWEIGHT_CONFIRMED, exactApprovedSetCount = 2,
                sideAndLandingSemanticsPreserved = false)
        )
        assertEquals(PowerJumpPrescriptionStatus.UNSUPPORTED, lossy.status)
    }

    @Test
    fun supportiveOrWrongQualityRelationsCannotAuthorizePower() {
        val supportive = PowerJumpPrescriptionShapeResolver.resolve(
            PowerJumpPrescriptionRequest(PowerJumpDoseKind.POWER, "machine_press",
                relation("machine_press", TrainableQuality.POWER, StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY,
                    PhysicalQualityRegion.UPPER_PUSH, PhysicalQualityMode.BALLISTIC),
                PowerJumpLoadMode.BODYWEIGHT_CONFIRMED, exactApprovedSetCount = 2)
        )
        assertEquals(PowerJumpPrescriptionStatus.UNSUPPORTED, supportive.status)
        assertNull(supportive.prescription)

        val wrongMode = PowerJumpPrescriptionShapeResolver.resolve(
            PowerJumpPrescriptionRequest(PowerJumpDoseKind.JUMP_LANDING, "power_clean",
                relation("power_clean", TrainableQuality.POWER, StimulusCapabilityLevel.DIRECT_CAPABILITY,
                    PhysicalQualityRegion.LOWER, PhysicalQualityMode.BALLISTIC),
                PowerJumpLoadMode.BODYWEIGHT_CONFIRMED, exactApprovedSetCount = 2)
        )
        assertEquals(PowerJumpPrescriptionStatus.UNSUPPORTED, wrongMode.status)
        assertFalse(wrongMode.reasonCodes.isEmpty())
    }

    private fun evidence(
        lower: Int,
        upper: Int,
        minutes: List<Double>?,
        existing: List<ExistingPowerJumpSet> = emptyList()
    ) = PowerJumpDoseEvidence(
        resistanceWorkload = PowerJumpResistanceWorkload(PowerJumpWorkloadStatus.COMPLETE, lower, upper),
        validRecentBadmintonWeekMinutes = minutes,
        existingAuthorizedDirectSets = existing
    )

    private fun need(
        id: String,
        kind: PowerJumpDoseKind,
        region: PowerJumpBodyRegion,
        priority: TargetPriority = TargetPriority.PRIMARY
    ) = PowerJumpDoseNeed(id, kind, region, TrainingNeedDecision.DEVELOP, priority)

    private fun relation(
        key: String,
        quality: TrainableQuality,
        capability: StimulusCapabilityLevel,
        region: PhysicalQualityRegion,
        mode: PhysicalQualityMode
    ) = ExercisePhysicalQualityRelation(
        relationId = "$key:${quality.name}:${mode.name}",
        exerciseStableKey = key,
        qualityId = quality,
        relationLevel = capability,
        regionQualifier = region,
        modeQualifier = mode,
        prescriptionDependent = true,
        provenance = "TEST",
        evidenceRelationKeys = emptySet(),
        reviewStatus = "PASS",
        notes = ""
    )
}
