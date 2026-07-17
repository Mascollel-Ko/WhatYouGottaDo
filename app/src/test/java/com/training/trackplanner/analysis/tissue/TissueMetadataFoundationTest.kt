package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.ExerciseMetadataAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueMetadataFoundationTest {
    @Test
    fun catalogAndScopeCoverEveryCanonicalStableKeyWithoutProductionClaims() {
        val canonical = ExerciseMetadataAdapter.fromCsv(asset("metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"))
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        val scope = TissueMetadataParser.scope(tissueAsset("exercise_tissue_scope_manifest_v1.csv"))

        assertEquals(239, canonical.size)
        assertEquals(20, catalog.count { it.tissueClass == TissueClass.JOINT })
        assertEquals(23, catalog.count { it.tissueClass == TissueClass.TENDON })
        assertEquals(16, catalog.count { it.tissueClass == TissueClass.LIGAMENT })
        assertEquals(2, catalog.count { it.tissueClass == TissueClass.FASCIA })
        assertTrue(TissueMetadataValidator.catalog(catalog).errors.toString(), TissueMetadataValidator.catalog(catalog).isValid)

        val report = TissueMetadataValidator.scope(scope, canonical.map { it.stableKey }.toSet(), catalog)
        assertTrue(report.errors.toString(), report.isValid)
        assertEquals(canonical.size * catalog.size, scope.size)
        assertTrue(scope.all { it.scopeStatus == TissueScopeStatus.NOT_YET_EVALUATED })
        assertTrue(scope.none(TissueScopeEntry::productionEligibility))
        assertTrue(scope.none { it.humanApprovedBy.isNotBlank() })
    }

    @Test
    fun catalogKeepsRequiredAnatomyDistinctAndCatalogEvidenceCannotCarryLoadClaims() {
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        val byId = catalog.associateBy(TissueCatalogEntry::tissueId)

        assertEquals(TissueClass.FASCIA, byId.getValue("PLANTAR_FASCIA").tissueClass)
        assertNotEquals(byId.getValue("KNEE_TIBIOFEMORAL"), byId.getValue("KNEE_PATELLOFEMORAL"))
        listOf("KNEE_ACL", "KNEE_PCL", "KNEE_MCL", "KNEE_LCL").forEach { id ->
            assertEquals(TissueClass.LIGAMENT, byId.getValue(id).tissueClass)
        }
        assertFalse(byId.getValue("SCAPULOTHORACIC_FUNCTIONAL_COMPLEX").isTrueJoint)

        val invalid = catalog.first().copy(functionalDescription = "Claims exercise compression load")
        assertFalse(TissueMetadataValidator.catalog(listOf(invalid)).isValid)
    }

    @Test
    fun missingEvaluationStatesNeverBecomeZeroOrProductionEligible() {
        val catalog = TissueMetadataParser.catalog(tissueAsset("canonical_tissue_catalog_v1.csv"))
        val stableKey = "ex_fixture"
        val base = profile(stableKey)

        assertFalse(
            TissueMetadataValidator.profiles(
                listOf(base.copy(loadBand = TissueLoadBand.NONE)),
                setOf(stableKey),
                catalog
            ).isValid
        )
        assertFalse(
            TissueMetadataValidator.profiles(
                listOf(base.copy(evidenceStatus = TissueEvidenceStatus.EVALUATED_ABSENT)),
                setOf(stableKey),
                catalog
            ).isValid
        )
        assertFalse(
            TissueMetadataValidator.profiles(
                listOf(
                    base.copy(
                        evidenceStatus = TissueEvidenceStatus.CONFLICTING_EVIDENCE,
                        productionEligibility = true,
                        loadBand = TissueLoadBand.HIGH
                    )
                ),
                setOf(stableKey),
                catalog
            ).isValid
        )
    }

    @Test
    fun repositoryUsesExactStableKeyAndRejectsDuplicateProfileIdentity() {
        val profile = profile("exact_key")
        val repository = TissueLoadProfileRepository(listOf(profile))

        assertEquals(listOf(profile), repository.profilesForStableKey("exact_key"))
        assertTrue(repository.profilesForStableKey("Exact Key").isEmpty())
        assertTrue(runCatching { TissueLoadProfileRepository(listOf(profile, profile)) }.isFailure)
    }

    @Test
    fun foundationAuditRemainsHistoricalAndSemanticHashIgnoresRowOrder() {
        val canonicalCsv = asset("metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")
        val catalogCsv = tissueAsset("canonical_tissue_catalog_v1.csv")
        val scopeCsv = tissueAsset("exercise_tissue_scope_manifest_v1.csv")
        val rubricCsv = tissueAsset("tissue_load_band_rubric_v1.csv")
        val profileFiles = listOf(
            "exercise_joint_load_profiles_v1.csv",
            "exercise_tendon_load_profiles_v1.csv",
            "exercise_ligament_load_profiles_v1.csv",
            "exercise_fascia_load_profiles_v1.csv"
        )
        val profileHash = TissueMetadataValidator.combinedHash(
            profileFiles.associateWith { TissueMetadataValidator.semanticCsvHash(tissueAsset(it)) }
        )
        val claimHash = TissueMetadataValidator.combinedHash(
            mapOf(
                "draft" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_evidence_claims_draft_v1.csv")),
                "blind" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_evidence_blind_review_v1.csv")),
                "final" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_evidence_claims_v1.csv"))
            )
        )
        val inputParts = mapOf(
                "canonical" to TissueMetadataValidator.semanticCsvHash(canonicalCsv),
                "catalog" to TissueMetadataValidator.semanticCsvHash(catalogCsv),
                "scope" to TissueMetadataValidator.semanticCsvHash(scopeCsv),
                "profiles" to profileHash,
                "rubric" to TissueMetadataValidator.semanticCsvHash(rubricCsv),
                "evidence" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_load_evidence_registry_v1.csv")),
                "claims" to claimHash,
                "sourceVerification" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_source_verification_v1.csv")),
                "legacyMigration" to TissueMetadataValidator.semanticCsvHash(tissueAsset("legacy_tissue_tag_migration_v1.csv")),
                "doseCapability" to TissueMetadataValidator.semanticCsvHash(tissueAsset("dose_input_capability_v1.csv")),
                "modifier" to TissueMetadataValidator.semanticCsvHash(tissueAsset("exercise_tissue_modifier_rules_v1.csv")),
                "recovery" to TissueMetadataValidator.semanticCsvHash(tissueAsset("tissue_recovery_profiles_v1.csv"))
            )
        val inputHash = TissueMetadataValidator.combinedHash(inputParts)
        val audits = TissueMetadataParser.auditManifests(tissueAsset("tissue_metadata_audit_manifest_v1.csv"))
        val audit = audits.single { it.values.getValue("auditScope") == "FOUNDATION_FULL" }
        val values = audit.values

        assertEquals(239, audit.canonicalExerciseCount)
        assertEquals(239 * 61, audit.scopeManifestRowCount)
        assertEquals("ff6000e5998a516f6f5259c48e498a8766af656c0dfab52c631822fa4a8786af", audit.inputSnapshotHash)
        assertNotEquals(inputHash, audit.inputSnapshotHash)
        assertEquals(TissueAuditDecision.FOUNDATION_COMPLETE_CANDIDATE, audit.auditDecision)
        assertEquals("PASS", values.getValue("doseCapabilityStatus"))
        assertEquals("NOT_APPLICABLE", values.getValue("lateralityCoverageStatus"))
        assertTrue(audits.all { it.values.getValue("lateralityCoverageStatus") == "NOT_APPLICABLE" })
        assertTrue(audits.all { it.values.getValue("sideUnresolvedCount") == "0" })
        assertEquals("PASS", values.getValue("modifierValidationStatus"))
        assertEquals("PASS", values.getValue("recoveryValidationStatus"))
        listOf(
            "automatedValidationStatus", "stableKeyCoverageStatus", "scopeCoverageStatus",
            "profileIntegrityStatus", "catalogEvidenceStatus", "exerciseLoadEvidenceIntegrityStatus",
            "citationVerificationStatus", "blindReviewCoverageStatus", "humanApprovalCoverageStatus",
            "doseCapabilityStatus", "lateralityCoverageStatus", "modifierValidationStatus",
            "recoveryValidationStatus"
        ).forEach { field -> assertTrue(values.getValue(field) in TissueValidationStatus.entries.map { it.name }) }

        val tiny = "id,value\na,1\nb,2\n"
        val reordered = "id,value\nb,2\na,1\n"
        assertEquals(TissueMetadataValidator.semanticCsvHash(tiny), TissueMetadataValidator.semanticCsvHash(reordered))
        assertNotEquals(TissueMetadataValidator.semanticCsvHash(tiny), TissueMetadataValidator.semanticCsvHash("id,value\na,9\nb,2\n"))

        val batchAudit = audits.single { it.values.getValue("auditBatchId") == "TISSUE_RUBRIC_B1_LOWER_KNEE_ANKLE" }
        assertEquals("73bbb560046d5ee8c2da1305a0305929cbf609be8815d93edaf3897c4472f851", batchAudit.inputSnapshotHash)
        assertEquals(TissueAuditDecision.PRODUCTION_REVIEW_REQUIRED, batchAudit.auditDecision)
        assertEquals("10", batchAudit.values.getValue("sourceCount"))
        assertEquals("12", batchAudit.values.getValue("draftClaimCount"))
        assertEquals("5", batchAudit.values.getValue("draftRubricCount"))
        assertEquals("31", batchAudit.values.getValue("researchDecisionCount"))
        assertEquals("15", batchAudit.values.getValue("targetExerciseReviewCount"))
        assertEquals("0", batchAudit.values.getValue("blindReviewCount"))
        assertEquals("0", batchAudit.values.getValue("finalClaimCount"))
        assertEquals("0", batchAudit.values.getValue("humanApprovalCount"))
        assertEquals("0", batchAudit.values.getValue("productionEligibleProfileCount"))
    }

    private fun profile(stableKey: String) = TissueLoadProfile(
        profileRowId = "fixture",
        stableKey = stableKey,
        tissueClass = TissueClass.JOINT,
        tissueId = "KNEE_PATELLOFEMORAL",
        loadDimension = TissueLoadDimension.COMPRESSION,
        loadBand = null,
        evidenceStatus = TissueEvidenceStatus.NOT_YET_EVALUATED,
        evidenceLevel = "",
        confidenceLevel = "",
        reviewStatus = "NOT_REVIEWED",
        productionEligibility = false,
        doseBasis = "",
        referenceConditionId = "",
        modifierSetId = "",
        recoveryProfileId = "",
        sideAllocationPolicy = "",
        rubricId = "",
        evidenceSetId = "",
        evidenceClaimIds = emptyList(),
        sourceRefs = emptyList(),
        reviewBatchId = "",
        preparedBy = "Codex",
        preparedByType = TissueActorType.AI_AGENT,
        preparedAt = "2026-07-13",
        blindReviewedBy = "",
        blindReviewedByType = null,
        blindReviewedAt = "",
        humanApprovedBy = "",
        humanApprovedAt = "",
        reviewNotes = "fixture"
    )

    private fun tissueAsset(name: String): String = asset("metadata/tissue_load_v1/$name")

    private fun asset(relative: String): String = sequenceOf(
        File("src/main/assets/$relative"),
        File("app/src/main/assets/$relative")
    ).first(File::exists).readText(Charsets.UTF_8)
}
