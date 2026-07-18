package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class TissueExposureShadowPipelineTest {
    @Test
    fun doseResolverUsesExistingBodyweightAndHoldAuthoritiesAndNeverInventsCounts() {
        val external = TissueDoseResolver.resolve(record(), TissueDoseBasis.EXTERNAL_LOAD_REPETITIONS)
        val bodyweight = TissueDoseResolver.resolve(
            record(stableKey = "push_up", weightKg = 0.0, bodyWeightKg = 80.0),
            TissueDoseBasis.EFFECTIVE_BODYWEIGHT_REPETITIONS
        )
        val hold = TissueDoseResolver.resolve(
            record(stableKey = "plank", reps = 0, weightKg = 0.0, seconds = 30, setRpe = 8.0),
            TissueDoseBasis.DURATION_HOLD
        )
        val missingCount = TissueDoseResolver.resolve(record(), TissueDoseBasis.LANDING_CONTACT_COUNT)
        val zero = TissueDoseResolver.resolve(record(reps = 0, weightKg = 0.0), TissueDoseBasis.EXTERNAL_LOAD_REPETITIONS)

        assertEquals(200.0, external.resolvedDose!!, 1e-9)
        assertEquals(520.0, bodyweight.resolvedDose!!, 1e-9)
        assertEquals(34.5, hold.resolvedDose!!, 1e-9)
        assertTrue(hold.rpeAlreadyApplied)
        assertNull(missingCount.resolvedDose)
        assertEquals(TissueDoseResolutionStatus.MISSING_RECORD_INPUT, missingCount.status)
        assertTrue(missingCount.diagnostics.single().contains("no event count was invented"))
        assertEquals(0.0, zero.resolvedDose!!, 1e-9)
    }

    @Test
    fun modifierResolutionIsOrderIndependentAndReplacementPrecedesMultiplication() {
        val exactReplacement = modifierRule(
            id = "exact_depth",
            family = TissueModifierFamily.DEPTH,
            condition = "deep",
            operation = TissueModifierOperation.REPLACE_WEIGHT,
            factor = 1.5,
            exclusiveGroup = "depth",
            specificity = TissueModifierSpecificity.EXACT_STABLE_KEY,
            precedence = 2
        )
        val broadReplacement = exactReplacement.copy(
            modifierRuleId = "global_depth",
            stableKey = "",
            factor = 2.0,
            specificityLevel = TissueModifierSpecificity.GLOBAL_TISSUE_RULE,
            precedence = 1
        )
        val multiplier = modifierRule(
            id = "rom_multiplier",
            family = TissueModifierFamily.ROM,
            condition = "full",
            operation = TissueModifierOperation.MULTIPLY_WEIGHT,
            factor = 2.0
        )
        val inputs = mapOf(TissueModifierFamily.DEPTH to "deep", TissueModifierFamily.ROM to "full")
        val first = TissueModifierResolver.resolve(
            1.0, "fixture", "SQUAT", "KNEE_PATELLOFEMORAL", TissueLoadDimension.COMPRESSION,
            inputs, listOf(broadReplacement, multiplier, exactReplacement), allowNonProductionFixtures = true
        )
        val reversed = TissueModifierResolver.resolve(
            1.0, "fixture", "SQUAT", "KNEE_PATELLOFEMORAL", TissueLoadDimension.COMPRESSION,
            inputs, listOf(exactReplacement, multiplier, broadReplacement).reversed(), allowNonProductionFixtures = true
        )

        assertEquals(3.0, first.adjustedWeight!!, 1e-9)
        assertEquals(first, reversed)
        assertEquals(listOf("exact_depth", "rom_multiplier"), first.appliedModifierIds)
        assertEquals(listOf("global_depth"), first.skippedModifierIds)
    }

    @Test
    fun modifierInteractionsAndMissingInputsFailClosed() {
        val surface = modifierRule(
            "surface", TissueModifierFamily.SURFACE, "unstable",
            TissueModifierOperation.MULTIPLY_WEIGHT, 1.1, interactionGroup = "unstable_fatigue"
        )
        val fatigue = modifierRule(
            "fatigue", TissueModifierFamily.FATIGUED_TECHNIQUE, "fatigued",
            TissueModifierOperation.MULTIPLY_WEIGHT, 1.1, interactionGroup = "unstable_fatigue"
        )
        val unsupported = TissueModifierResolver.resolve(
            1.0, "fixture", "SQUAT", "KNEE_PATELLOFEMORAL", TissueLoadDimension.COMPRESSION,
            mapOf(TissueModifierFamily.SURFACE to "unstable", TissueModifierFamily.FATIGUED_TECHNIQUE to "fatigued"),
            listOf(surface, fatigue), allowNonProductionFixtures = true
        )
        val missing = TissueModifierResolver.resolve(
            1.0, "fixture", "SQUAT", "KNEE_PATELLOFEMORAL", TissueLoadDimension.COMPRESSION,
            emptyMap(), listOf(surface.copy(missingInputBehavior = TissueMissingInputBehavior.BLOCK_CALCULATION)),
            allowNonProductionFixtures = true
        )

        assertEquals(TissueCalculationStatus.UNSUPPORTED_MODIFIER_COMBINATION, unsupported.calculationStatus)
        assertNull(unsupported.adjustedWeight)
        assertEquals(TissueCalculationStatus.MISSING_RECORD_INPUT, missing.calculationStatus)
    }

    @Test
    fun independentTissueProfilesCalculateWithoutSumPreservationAndApplyRpeOnce() {
        val record = record().copy(approvedRpeMultiplier = 1.2)
        val joint = profile("joint", TissueClass.JOINT, "KNEE_PATELLOFEMORAL", TissueLoadDimension.COMPRESSION)
        val tendon = profile("tendon", TissueClass.TENDON, "PATELLAR_TENDON", TissueLoadDimension.PEAK_TENSILE_LOAD)
        val exposures = TissueExposureCalculator.calculate(
            record,
            listOf(tendon, joint),
            mapOf(
                "joint" to fixtureWeight("joint", 0.5),
                "tendon" to fixtureWeight("tendon", 0.8)
            ),
            allowNonProductionFixtures = true
        ).associateBy { it.tissueLoadKey.tissueId }

        assertEquals(100.0, exposures.getValue("KNEE_PATELLOFEMORAL").rawExposure!!, 1e-9)
        assertEquals(120.0, exposures.getValue("KNEE_PATELLOFEMORAL").adjustedExposure!!, 1e-9)
        assertEquals(160.0, exposures.getValue("PATELLAR_TENDON").rawExposure!!, 1e-9)
        assertEquals(192.0, exposures.getValue("PATELLAR_TENDON").adjustedExposure!!, 1e-9)
        assertTrue(exposures.values.all { it.calculationStatus == TissueCalculationStatus.CALCULABLE })
    }

    @Test
    fun canonicalOfiAxisCorrectionDoesNotChangeConnectiveTissueExposureNumbers() {
        val exposure = TissueExposureCalculator.calculate(
            record().copy(approvedRpeMultiplier = 1.2),
            listOf(profile("joint", TissueClass.JOINT, "KNEE_PATELLOFEMORAL", TissueLoadDimension.COMPRESSION)),
            mapOf("joint" to fixtureWeight("joint", 0.5)),
            allowNonProductionFixtures = true
        ).single()

        assertEquals(100.0, exposure.rawExposure!!, 1e-9)
        assertEquals(120.0, exposure.adjustedExposure!!, 1e-9)
        assertEquals(TissueCalculationStatus.CALCULABLE, exposure.calculationStatus)
    }

    @Test
    fun unapprovedMetadataIsNotZeroAndExecutionSideDoesNotSplitUnsidedState() {
        val unilateral = profile(
            "unilateral", TissueClass.TENDON, "PATELLAR_TENDON", TissueLoadDimension.PEAK_TENSILE_LOAD,
            sidePolicy = TissueSideAllocationPolicy.UNILATERAL_SIDE_REQUIRED
        )
        val blocked = TissueExposureCalculator.calculate(
            record(), listOf(unilateral), mapOf("unilateral" to fixtureWeight("unilateral", 0.5))
        ).single()
        val left = TissueExposureCalculator.calculate(
            record().copy(performedSide = TissueSide.LEFT),
            listOf(unilateral),
            mapOf("unilateral" to fixtureWeight("unilateral", 0.5)),
            allowNonProductionFixtures = true
        ).single()
        val right = TissueExposureCalculator.calculate(
            record().copy(performedSide = TissueSide.RIGHT),
            listOf(unilateral),
            mapOf("unilateral" to fixtureWeight("unilateral", 0.5)),
            allowNonProductionFixtures = true
        ).single()
        val unspecified = TissueExposureCalculator.calculate(
            record(),
            listOf(unilateral),
            mapOf("unilateral" to fixtureWeight("unilateral", 0.5)),
            allowNonProductionFixtures = true
        ).single()
        val snapshot = TissueWindowedExposureCalculator.snapshot(
            listOf(left, right, unspecified),
            LocalDate.of(2026, 7, 13)
        )

        assertEquals(TissueCalculationStatus.EVIDENCE_NOT_APPROVED, blocked.calculationStatus)
        assertNull(blocked.adjustedExposure)
        assertEquals(TissueCalculationStatus.CALCULABLE, left.calculationStatus)
        assertEquals(TissueCalculationStatus.CALCULABLE, right.calculationStatus)
        assertEquals(TissueCalculationStatus.CALCULABLE, unspecified.calculationStatus)
        assertEquals(left.tissueLoadKey, right.tissueLoadKey)
        assertEquals(left.tissueLoadKey, unspecified.tissueLoadKey)
        assertEquals(300.0, snapshot.tendonLoads.single().rolling24HourExposure, 1e-9)
    }

    @Test
    fun windowedExposureUsesCalendarBoundariesAndExcludesFutureRecords() {
        val target = LocalDate.of(2026, 7, 13)
        val key = TissueLoadKey(TissueClass.TENDON, "PATELLAR_TENDON", TissueLoadDimension.CYCLIC_TENSILE_LOAD)
        val exposures = listOf(
            exposure(1, target, key, 10.0),
            exposure(2, target.minusDays(2), key, 20.0),
            exposure(3, target.minusDays(6), key, 30.0),
            exposure(4, target.minusDays(7), key, 40.0),
            exposure(5, target.plusDays(1), key, 100.0)
        )
        val state = TissueWindowedExposureCalculator.snapshot(exposures, target).tendonLoads.single()

        assertEquals(10.0, state.rolling24HourExposure, 1e-9)
        assertEquals(30.0, state.rolling72HourExposure, 1e-9)
        assertEquals(60.0, state.rolling7DayExposure, 1e-9)
        assertNull(state.residualExposure)
        assertEquals(TissueRecoveryCalculationMode.WINDOWED_EXPOSURE, state.calculationMode)
        assertEquals(listOf(3L, 2L, 1L), state.topContributingRecords.map(TissueContributionSummary::recordId))
    }

    @Test
    fun shadowPipelineDiagnosesUnknownStableKeysAndRecoverySchemaIsFailClosed() {
        val result = TissueExposureShadowPipeline(emptyList(), emptyMap()).calculate(
            listOf(record(stableKey = "unknown_fixture")),
            LocalDate.of(2026, 7, 13)
        )
        val recovery = TissueRecordContractParser.recoveryProfiles(tissueAsset("tissue_recovery_profiles_v1.csv"))
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))

        assertTrue(result.exposures.isEmpty())
        assertEquals("unknown_fixture", result.snapshot.incompleteMetadata.single().stableKey)
        assertTrue(recovery.isEmpty())
        assertTrue(TissueRecordContractValidator.recoveryProfiles(recovery, catalog).isValid)
    }

    private fun record(
        stableKey: String = "fixture",
        reps: Int = 10,
        weightKg: Double = 20.0,
        seconds: Int = 0,
        setRpe: Double? = null,
        bodyWeightKg: Double? = null
    ): TissueWorkoutRecord {
        val exercise = Exercise(id = 10, name = stableKey, category = "fixture", stableKey = stableKey)
        return TissueWorkoutRecord(
            entry = WorkoutEntry(id = 1, date = "2026-07-13", exerciseId = 10, exerciseName = stableKey, category = "fixture"),
            sets = listOf(WorkoutSet(id = 1, entryId = 1, setIndex = 0, reps = reps, weightKg = weightKg,
                seconds = seconds, confirmed = true, rpe = setRpe)),
            exercise = exercise,
            bodyWeightKg = bodyWeightKg
        )
    }

    private fun profile(
        id: String,
        tissueClass: TissueClass,
        tissueId: String,
        dimension: TissueLoadDimension,
        sidePolicy: TissueSideAllocationPolicy = TissueSideAllocationPolicy.BILATERAL_SHARED
    ) = TissueLoadProfile(
        profileRowId = id,
        stableKey = "fixture",
        tissueClass = tissueClass,
        tissueId = tissueId,
        loadDimension = dimension,
        loadBand = TissueLoadBand.MODERATE,
        evidenceStatus = TissueEvidenceStatus.ANATOMICAL_INFERENCE,
        evidenceLevel = "FIXTURE_ONLY",
        confidenceLevel = "LOW",
        reviewStatus = "FIXTURE_ONLY",
        productionEligibility = false,
        doseBasis = TissueDoseBasis.EXTERNAL_LOAD_REPETITIONS.name,
        referenceConditionId = "fixture_reference",
        modifierSetId = "",
        recoveryProfileId = "",
        sideAllocationPolicy = sidePolicy.name,
        rubricId = "fixture",
        evidenceSetId = "",
        evidenceClaimIds = listOf("fixture", "fixture"),
        sourceRefs = emptyList(),
        reviewBatchId = "",
        preparedBy = "test",
        preparedByType = TissueActorType.AUTOMATED_VALIDATOR,
        preparedAt = "2026-07-13",
        blindReviewedBy = "",
        blindReviewedByType = null,
        blindReviewedAt = "",
        humanApprovedBy = "",
        humanApprovedAt = "",
        reviewNotes = "Explicitly non-production test fixture."
    )

    private fun fixtureWeight(id: String, weight: Double) = TissueProfileDimensionWeight(
        profileRowId = id,
        weight = weight,
        explicitlyNonProductionFixture = true
    )

    private fun modifierRule(
        id: String,
        family: TissueModifierFamily,
        condition: String,
        operation: TissueModifierOperation,
        factor: Double,
        exclusiveGroup: String = "",
        interactionGroup: String = "",
        specificity: TissueModifierSpecificity = TissueModifierSpecificity.EXACT_STABLE_KEY,
        precedence: Int = 1
    ) = TissueModifierRule(
        modifierRuleId = id,
        stableKey = "fixture",
        tissueId = "KNEE_PATELLOFEMORAL",
        loadDimension = TissueLoadDimension.COMPRESSION,
        modifierFamily = family,
        inputCondition = condition,
        referenceCondition = "reference",
        operation = operation,
        factor = factor,
        bandShift = null,
        specificityLevel = specificity,
        exclusiveGroup = exclusiveGroup,
        interactionGroup = interactionGroup,
        precedence = precedence,
        minimumCombinedFactor = null,
        maximumCombinedFactor = null,
        requiredRecordInputs = listOf(family.name),
        missingInputBehavior = TissueMissingInputBehavior.USE_REFERENCE_CONDITION,
        evidenceStatus = TissueEvidenceStatus.ANATOMICAL_INFERENCE,
        evidenceClaimIds = emptyList(),
        sourceRefs = emptyList(),
        confidenceLevel = "LOW",
        reviewStatus = "FIXTURE_ONLY",
        humanApprovedBy = "",
        reviewNotes = "Explicitly non-production test fixture."
    )

    private fun exposure(id: Long, date: LocalDate, key: TissueLoadKey, value: Double) = RecordTissueExposure(
        recordId = id,
        date = date,
        stableKey = "fixture_$id",
        tissueLoadKey = key,
        resolvedDose = value,
        rawExposure = value,
        adjustedExposure = value,
        calculationStatus = TissueCalculationStatus.CALCULABLE,
        doseResolutionStatus = TissueDoseResolutionStatus.DERIVED_FROM_CURRENT_RECORD,
        appliedModifierIds = emptyList(),
        evidenceStatus = TissueEvidenceStatus.ANATOMICAL_INFERENCE,
        confidenceLevel = "LOW",
        evidenceClaimIds = emptyList(),
        sourceRefs = emptyList(),
        diagnostics = emptyList()
    )

    private fun tissueAsset(name: String): String = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
