package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueRecordContractsTest {
    @Test
    fun everyCanonicalLegacyTokenHasOneReviewOnlyMigrationDecision() {
        val canonical = TissueMetadataParser.table(
            asset("metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")
        )
        val fields = setOf("tendonStressTags", "ligamentJointStabilityStressTags", "jointImpactStressTags")
        val actualTokens = canonical.rows.flatMap { row ->
            fields.flatMap { field ->
                row.getValue(field).split('|').map(String::trim)
                    .filter { it.isNotBlank() && it != "NONE" }
                    .map { field to it }
            }
        }.toSet()
        val migrations = TissueRecordContractParser.legacyMigrations(
            tissueAsset("legacy_tissue_tag_migration_v1.csv")
        )
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))

        assertEquals(42, actualTokens.size)
        assertEquals(42, migrations.size)
        assertTrue(migrations.none(LegacyTissueTagMigration::automaticBandAllowed))
        assertTrue(TissueRecordContractValidator.legacyMigrations(migrations, actualTokens, catalog).isValid)

        val cuff = migrations.single { it.legacyToken == "ROTATOR_CUFF_TENDON_STRESS" }
        assertEquals(TissueMigrationSpecificity.BROAD_AMBIGUOUS, cuff.mappingSpecificity)
        assertEquals(3, cuff.candidateTissueIds.size)
        assertTrue("no equal split" in cuff.migrationNotes.lowercase())
    }

    @Test
    fun doseCapabilityAuditKeepsUnavailableEventCountsMissing() {
        val capabilities = TissueRecordContractParser.doseCapabilities(tissueAsset("dose_input_capability_v1.csv"))
        val byBasis = capabilities.associateBy(TissueDoseCapability::doseBasis)

        assertTrue(TissueRecordContractValidator.doseCapabilities(capabilities).isValid)
        assertTrue(
            byBasis.getValue(TissueDoseBasis.EFFECTIVE_BODYWEIGHT_REPETITIONS)
                .derivationMethod.contains("BodyweightEffectiveLoadCalculator")
        )
        assertTrue(
            byBasis.getValue(TissueDoseBasis.DURATION_HOLD)
                .derivationMethod.contains("DurationHoldLoadCalculator")
        )
        listOf(
            TissueDoseBasis.LANDING_CONTACT_COUNT,
            TissueDoseBasis.DIRECTION_CHANGE_COUNT,
            TissueDoseBasis.JUMP_COUNT,
            TissueDoseBasis.THROW_COUNT,
            TissueDoseBasis.STROKE_COUNT
        ).forEach { basis ->
            val capability = byBasis.getValue(basis)
            assertEquals(TissueInputAvailabilityStatus.NOT_CURRENTLY_AVAILABLE, capability.availabilityStatus)
            assertFalse(capability.fallbackAllowed)
            assertTrue(capability.requiresSchemaChange)
            assertTrue(capability.requiresUiChange)
        }
    }

    @Test
    fun missingLateralityRemainsUnsidedAndIsNeverSplit() {
        val unilateral = TissueSideResolver.resolve(TissueSideAllocationPolicy.UNILATERAL_SIDE_REQUIRED)
        val leadTrail = TissueSideResolver.resolve(TissueSideAllocationPolicy.LEAD_TRAIL_CONTEXT_REQUIRED)
        val balanced = TissueSideResolver.resolve(
            TissueSideAllocationPolicy.ALTERNATING_SYMMETRIC_ASSUMPTION_ALLOWED,
            balancedAlternatingProtocol = true
        )

        assertEquals(TissueSide.UNSIDED, unilateral.side)
        assertEquals(TissueSideResolutionStatus.UNRESOLVED, unilateral.status)
        assertEquals(TissueSide.UNSIDED, leadTrail.side)
        assertEquals(TissueSide.BILATERAL, balanced.side)
        assertEquals(TissueSideResolutionStatus.SYMMETRIC_ASSUMPTION, balanced.status)
    }

    @Test
    fun modifierSchemaIsEmptyAndUnapprovedOrInvalidRulesFailClosed() {
        val rules = TissueRecordContractParser.modifierRules(
            tissueAsset("exercise_tissue_modifier_rules_v1.csv")
        )
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        assertTrue(rules.isEmpty())
        assertTrue(TissueRecordContractValidator.modifierRules(rules, emptySet(), catalog).isValid)

        val invalid = modifierRule().copy(
            factor = Double.NaN,
            evidenceStatus = TissueEvidenceStatus.BLOCKED_INSUFFICIENT_EVIDENCE,
            humanApprovedBy = "not-allowed"
        )
        val report = TissueRecordContractValidator.modifierRules(
            listOf(invalid),
            setOf("fixture_squat"),
            catalog
        )
        assertFalse(report.isValid)
        assertTrue(report.errors.any { "not finite" in it })
        assertTrue(report.errors.any { "blocked evidence cannot be approved" in it })
    }

    private fun modifierRule() = TissueModifierRule(
        modifierRuleId = "fixture",
        stableKey = "fixture_squat",
        tissueId = "KNEE_PATELLOFEMORAL",
        loadDimension = TissueLoadDimension.COMPRESSION,
        modifierFamily = TissueModifierFamily.DEPTH,
        inputCondition = "deep",
        referenceCondition = "reference",
        operation = TissueModifierOperation.MULTIPLY_WEIGHT,
        factor = 1.1,
        bandShift = null,
        specificityLevel = TissueModifierSpecificity.EXACT_STABLE_KEY,
        exclusiveGroup = "depth",
        interactionGroup = "",
        precedence = 1,
        minimumCombinedFactor = 0.5,
        maximumCombinedFactor = 1.5,
        requiredRecordInputs = listOf("depth"),
        missingInputBehavior = TissueMissingInputBehavior.BLOCK_CALCULATION,
        evidenceStatus = TissueEvidenceStatus.ANATOMICAL_INFERENCE,
        evidenceClaimIds = emptyList(),
        sourceRefs = emptyList(),
        confidenceLevel = "LOW",
        reviewStatus = "FIXTURE_ONLY",
        humanApprovedBy = "",
        reviewNotes = "Explicitly non-production fixture."
    )

    private fun tissueAsset(name: String): String = asset("metadata/tissue_load_v1/$name")
    private fun asset(relative: String): String = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists).readText(Charsets.UTF_8)
}
