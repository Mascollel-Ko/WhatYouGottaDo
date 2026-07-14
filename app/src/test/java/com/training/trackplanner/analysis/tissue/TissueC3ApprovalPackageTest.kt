package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TissueC3ApprovalPackageTest {
    @Test
    fun scopeHashCoversEveryScientificArtifactAndIsRowOrderIndependent() {
        val inputParts = inputFiles.mapValues { (_, name) ->
            TissueMetadataValidator.semanticCsvHash(asset(name))
        }
        val auditInputHash = TissueMetadataValidator.combinedHash(inputParts)
        val scopeHash = TissueMetadataValidator.combinedHash(inputParts + scopeReferences(auditInputHash))

        val exposedHashes = mapOf(
            "modeRegistry" to "modeRegistrySnapshotHash",
            "temporalMetricRegistry" to "temporalMetricRegistrySnapshotHash",
            "measurementMetricRegistry" to "measurementMetricRegistrySnapshotHash",
            "normalizationRegistry" to "normalizationRegistrySnapshotHash",
            "dimensionRegistry" to "dimensionRegistrySnapshotHash",
            "legacyMigration" to "migrationSnapshotHash",
            "metricExtractions" to "metricExtractionSnapshotHash",
            "previousRevisedRequest" to "previousRequestSnapshotHash",
            "requestResolutions" to "supersessionResolutionSnapshotHash",
            "revisedCandidateDispositions" to "dispositionSnapshotHash",
            "multidimensionalCandidates" to "candidateSnapshotHash",
            "rubrics" to "rubricSnapshotHash",
            "researchDecisions" to "researchDecisionSnapshotHash",
            "canonicalMappings" to "canonicalMappingSnapshotHash",
            "transferCorrespondence" to "transferCorrespondenceSnapshotHash",
            "historicalEvidenceRegistry" to "historicalSourceRegistrySnapshotHash",
            "c3EvidenceRegistry" to "c3SourceRegistrySnapshotHash",
            "historicalSourceVerification" to "historicalSourceVerificationSnapshotHash",
            "c3SourceVerification" to "c3SourceVerificationSnapshotHash",
            "historicalPublicationIntegrity" to "historicalPublicationIntegritySnapshotHash",
            "c3PublicationIntegrity" to "c3PublicationIntegritySnapshotHash",
            "upperLimbBacklog" to "upperLimbBacklogSnapshotHash"
        )
        val mismatches = exposedHashes.mapNotNull { (inputKey, requestField) ->
            inputKey.takeIf { inputParts.getValue(inputKey) != request.getValue(requestField) }?.let {
                "$it(kotlin=${inputParts.getValue(inputKey)},generator=${request.getValue(requestField)})"
            }
        }
        assertTrue("Generator/Kotlin semantic hash mismatch: $mismatches", mismatches.isEmpty())
        assertEquals(request.getValue("auditInputSnapshotHash"), auditInputHash)
        assertEquals(request.getValue("approvalScopeHash"), scopeHash)
        assertEquals("TISSUE_APPROVAL_REQUEST_C3_MD_${scopeHash.take(16).uppercase()}", request.getValue("approvalRequestId"))

        val candidates = asset("tissue_evidence_claim_candidates_multidimensional_v1.csv")
        val lines = candidates.lines().filter(String::isNotEmpty)
        val reordered = (listOf(lines.first()) + lines.drop(1).reversed()).joinToString("\n")
        val reorderedParts = inputParts + (
            "multidimensionalCandidates" to TissueMetadataValidator.semanticCsvHash(reordered)
        )
        val reorderedInputHash = TissueMetadataValidator.combinedHash(reorderedParts)
        assertEquals(scopeHash, TissueMetadataValidator.combinedHash(reorderedParts + scopeReferences(reorderedInputHash)))

        val changed = candidates.replaceFirst(
            "Healthy trained runners; task-specific strain; no force conversion.",
            "Healthy trained runners; task-specific strain; no force or stress conversion."
        )
        val changedParts = inputParts + (
            "multidimensionalCandidates" to TissueMetadataValidator.semanticCsvHash(changed)
        )
        val changedInputHash = TissueMetadataValidator.combinedHash(changedParts)
        assertNotEquals(scopeHash, TissueMetadataValidator.combinedHash(changedParts + scopeReferences(changedInputHash)))
    }

    @Test
    fun requestListsExactPartialNonProductionScopeAndFutureStatement() {
        assertEquals("49", request.getValue("metricExtractionCount"))
        assertEquals("30", request.getValue("candidateCount"))
        assertEquals("2", request.getValue("rubricCount"))
        assertEquals("24", request.getValue("priorCandidateDispositionCount"))
        assertEquals("48", request.getValue("researchDecisionCount"))
        assertEquals("49", request.getValue("canonicalMappingCount"))
        assertEquals("49", request.getValue("transferCorrespondenceCount"))
        assertEquals("15", request.getValue("sourceCount"))
        assertEquals("27", request.getValue("blockedResearchTargetCount"))
        assertEquals("PENDING_HUMAN_DECISION", request.getValue("requestStatus"))
        assertEquals("MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL", request.getValue("completionStatus"))
        assertEquals(previousRequestId, request.getValue("previousApprovalRequestId"))
        assertEquals(previousScopeHash, request.getValue("previousApprovalScopeHash"))
        assertEquals(resolutionId, request.getValue("supersessionResolutionId"))

        val expected = """I approve multidimensionalApprovalRequestId=${request.getValue("approvalRequestId")},
approvalScopeHash=${request.getValue("approvalScopeHash")},
reviewPath=SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL,
covering exactly 49 metric extractions, 30 multidimensional claim candidates, 2 metric-compatible rubric rows, 24 prior-candidate dispositions, 48 research decisions, 49 canonical transfer mappings, 15 source records, and the listed ontology, source-verification, publication-integrity, supersession, transfer, and upper-limb-backlog snapshots.
I understand this package is same-session, non-independent, non-production, contains 27 explicitly blocked targets, and does not approve a universal tissue-burden score or fixed multidimensional weights."""
        assertEquals(expected, request.getValue("requiredUserStatement"))
    }

    @Test
    fun supersededRequestIsImmutableAndPromotionLedgersRemainEmpty() {
        assertEquals(
            "c5db161e3b6a0e8c04c89e38634561b4a2a14e1d4b5129e7be8f51ce23f853ab",
            assetFile("tissue_review_batch_approval_request_revised_v1.csv").normalizedPayloadSha256()
        )
        assertNotEquals(previousRequestId, request.getValue("approvalRequestId"))
        assertNotEquals(previousScopeHash, request.getValue("approvalScopeHash"))
        val resolution = TissueEvidenceParser.approvalRequestResolutions(asset("tissue_approval_request_resolution_v1.csv"))
            .single { it.resolutionId == resolutionId }
        assertEquals(TissueApprovalRequestResolutionStatus.SUPERSEDED_BEFORE_APPROVAL, resolution.resolutionStatus)
        assertEquals("TISSUE_C3_MULTIDIMENSIONAL_LOWER_R1", resolution.replacementResearchBatchId)

        assertTrue(table("tissue_review_batch_approval_v1.csv").isEmpty())
        assertTrue(table("tissue_evidence_claims_v1.csv").isEmpty())
        assertTrue(table("tissue_evidence_blind_review_v1.csv").isEmpty())
        profileFiles.forEach { assertTrue(it, table(it).isEmpty()) }
    }

    @Test
    fun auditIsAppendOnlyPartialAndRecordsExactBoundaries() {
        val audits = TissueMetadataParser.auditManifests(asset("tissue_metadata_audit_manifest_v1.csv"))
        val audit = audits.single { it.values.getValue("auditBatchId") == "TISSUE_C3_MULTIDIMENSIONAL_LOWER_R1" }.values

        assertEquals("MULTIDIMENSIONAL_EVIDENCE_BATCH", audit.getValue("auditScope"))
        assertEquals("PRODUCTION_REVIEW_REQUIRED", audit.getValue("auditDecision"))
        assertEquals("MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL", audit.getValue("completionStatus"))
        assertEquals(request.getValue("approvalScopeHash"), audit.getValue("approvalScopeHash"))
        assertEquals(request.getValue("auditInputSnapshotHash"), audit.getValue("inputSnapshotHash"))
        assertEquals("15", audit.getValue("verifiedSourceCount"))
        assertEquals("30", audit.getValue("approvalRequestCandidateCount"))
        assertEquals("2", audit.getValue("approvalRequestRubricCount"))
        assertEquals("27", audit.getValue("blockedTissueDimensionTargetCount"))
        assertEquals("0", audit.getValue("anomalyFlagCount"))
        assertEquals("0", audit.getValue("humanBatchApprovalCount"))
        assertEquals("0", audit.getValue("formalFinalClaimCount"))
        assertEquals("0", audit.getValue("blindReviewCount"))
        assertEquals("0", audit.getValue("productionProfileCount"))
        assertTrue(audit.getValue("auditNotes").contains("mode 17, temporal 11, measurement 21, normalization 14"))
        assertEquals(1, audits.count { it.values.getValue("auditBatchId") == "TISSUE_C3_MULTIDIMENSIONAL_LOWER_R1" })
    }

    @Test
    fun everyCandidateAndRubricIsPresentedForHumanReview() {
        val approvalDoc = document("tissue_load_phase_c3_approval_request.md")
        val candidates = table("tissue_evidence_claim_candidates_multidimensional_v1.csv")
        val rubrics = table("tissue_load_rubric_v2.csv")

        candidates.forEach { assertTrue(it.getValue("claimCandidateId"), approvalDoc.contains(it.getValue("claimCandidateId"))) }
        rubrics.forEach { assertTrue(it.getValue("rubricId"), approvalDoc.contains(it.getValue("rubricId"))) }
        assertTrue(approvalDoc.contains("Exercise metric availability"))
        assertTrue(approvalDoc.contains("Cumulative exposure"))
        assertTrue(approvalDoc.replace("\r\n", "\n").contains(request.getValue("requiredUserStatement")))
        assertFalse(approvalDoc.contains("APPROVED"))
    }

    @Test
    fun upperLimbWorkIsQueuedAsASeparateEvidenceBatch() {
        val backlog = table("tissue_upper_limb_research_backlog_c3_v1.csv").single()
        assertEquals("TISSUE_C3_MULTIDIMENSIONAL_UPPER_PRESS_PULL_R1", backlog.getValue("backlogBatchId"))
        assertEquals("QUEUED_FOR_SEPARATE_RESEARCH", backlog.getValue("status"))
        assertTrue(expectedUpperTissues.all { it in backlog.getValue("tissueIds").tokens() })
        assertTrue(expectedExerciseFamilies.all { it in backlog.getValue("exerciseFamilies").tokens() })
        assertTrue(backlog.getValue("transferBoundary").contains("No lower-limb source"))
        assertTrue(document("tissue_load_phase_c3_upper_backlog.md").contains("creates no upper-limb claims"))
    }

    @Test
    fun sourceAndIntegrityScopeContainsAllFifteenVerifiedSources() {
        val sources = table("tissue_load_evidence_registry_v1.csv") + table("tissue_load_evidence_registry_c3_v1.csv")
        val verifications = TissueEvidenceParser.sourceVerifications(
            asset("tissue_source_verification_v1.csv")
        ) + TissueEvidenceParser.sourceVerifications(asset("tissue_source_verification_c3_v1.csv"))
        val integrity = TissueEvidenceParser.publicationIntegrityVerifications(
            asset("tissue_publication_integrity_verification_v1.csv")
        ) + TissueEvidenceParser.publicationIntegrityVerifications(asset("tissue_publication_integrity_verification_c3_v1.csv"))

        assertEquals(15, sources.size)
        assertEquals(15, verifications.size)
        assertTrue(verifications.all { it.identifierVerificationStatus == TissueIdentifierVerificationStatus.PMID_AND_DOI_VERIFIED })
        assertTrue(verifications.all { it.bibliographicMatchStatus == TissueBibliographicMatchStatus.MATCHED })
        assertEquals(15, integrity.size)
        assertTrue(integrity.all { it.integrityCheckStatus == TissuePublicationIntegrityCheckStatus.NO_ADVERSE_NOTICE_FOUND })
    }

    private fun scopeReferences(auditInputHash: String) = mapOf(
        "reviewPath" to request.getValue("reviewPath"),
        "auditManifestId" to request.getValue("auditManifestId"),
        "auditInputSnapshotHash" to auditInputHash,
        "previousApprovalRequestId" to previousRequestId,
        "previousApprovalScopeHash" to previousScopeHash,
        "supersessionResolutionId" to resolutionId
    )

    private val request by lazy { table("tissue_review_batch_approval_request_c3_v1.csv").single() }
    private fun String.tokens() = split('|').filter(String::isNotBlank).toSet()
    private fun table(name: String) = TissueMetadataParser.table(asset(name)).rows
    private fun asset(name: String) = assetFile(name).readText(Charsets.UTF_8)
    private fun assetFile(name: String) = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists)
    private fun document(name: String) = sequenceOf(File("docs/$name"), File("../docs/$name")).first(File::exists)
        .readText(Charsets.UTF_8)
    private fun File.normalizedPayloadSha256() = readText(Charsets.UTF_8).removePrefix("\uFEFF")
        .replace("\r\n", "\n").toByteArray(Charsets.UTF_8).sha256()
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
        .joinToString("") { "%02x".format(it) }

    private val inputFiles = linkedMapOf(
        "modeRegistry" to "tissue_mechanical_load_mode_registry_v1.csv",
        "temporalMetricRegistry" to "tissue_temporal_metric_registry_v1.csv",
        "measurementMetricRegistry" to "tissue_measurement_metric_registry_v1.csv",
        "normalizationRegistry" to "tissue_normalization_registry_v1.csv",
        "dimensionRegistry" to "tissue_load_dimension_registry_v2.csv",
        "legacyMigration" to "tissue_load_dimension_migration_v1.csv",
        "metricExtractions" to "tissue_source_metric_extraction_v1.csv",
        "previousRevisedRequest" to "tissue_review_batch_approval_request_revised_v1.csv",
        "requestResolutions" to "tissue_approval_request_resolution_v1.csv",
        "revisedCandidateDispositions" to "tissue_revised_candidate_disposition_c3_v1.csv",
        "multidimensionalCandidates" to "tissue_evidence_claim_candidates_multidimensional_v1.csv",
        "rubrics" to "tissue_load_rubric_v2.csv",
        "researchDecisions" to "tissue_research_decision_c3_v1.csv",
        "canonicalMappings" to "tissue_canonical_exercise_mapping_audit_v1.csv",
        "transferCorrespondence" to "tissue_exercise_variant_correspondence_c3_v1.csv",
        "historicalEvidenceRegistry" to "tissue_load_evidence_registry_v1.csv",
        "c3EvidenceRegistry" to "tissue_load_evidence_registry_c3_v1.csv",
        "historicalSourceVerification" to "tissue_source_verification_v1.csv",
        "c3SourceVerification" to "tissue_source_verification_c3_v1.csv",
        "historicalPublicationIntegrity" to "tissue_publication_integrity_verification_v1.csv",
        "c3PublicationIntegrity" to "tissue_publication_integrity_verification_c3_v1.csv",
        "upperLimbBacklog" to "tissue_upper_limb_research_backlog_c3_v1.csv"
    )
    private val profileFiles = listOf(
        "exercise_joint_load_profiles_v1.csv", "exercise_tendon_load_profiles_v1.csv",
        "exercise_ligament_load_profiles_v1.csv", "exercise_fascia_load_profiles_v1.csv"
    )
    private val expectedUpperTissues = setOf(
        "GLENOHUMERAL", "ACROMIOCLAVICULAR", "SCAPULOTHORACIC_FUNCTIONAL_COMPLEX", "HUMEROULNAR",
        "HUMERORADIAL", "PROXIMAL_RADIOULNAR", "DISTAL_RADIOULNAR", "RADIOCARPAL_WRIST",
        "SUPRASPINATUS_TENDON", "POSTERIOR_ROTATOR_CUFF_TENDON", "SUBSCAPULARIS_TENDON",
        "LONG_HEAD_BICEPS_TENDON", "PECTORALIS_MAJOR_TENDON", "TRICEPS_TENDON", "DISTAL_BICEPS_TENDON",
        "COMMON_FLEXOR_TENDON", "COMMON_EXTENSOR_TENDON", "WRIST_LIGAMENT_TFCC_COMPLEX"
    )
    private val expectedExerciseFamilies = setOf(
        "Bench press", "Incline press", "Push-up variants", "Dips", "Overhead press", "Landmine press",
        "Lateral raise", "Fly", "Pull-up variants", "Assisted pull-up", "Lat pulldown", "Rows",
        "Biceps curls", "Triceps extensions", "Wrist and grip exercises"
    )

    private companion object {
        const val previousRequestId = "TISSUE_APPROVAL_REQUEST_C2A_R1_74ECC66495637BDD"
        const val previousScopeHash = "74ecc66495637bdd70720957970aac41537c4726c9060a5e781bfcfc1c96678f"
        const val resolutionId = "TISSUE_APPROVAL_RESOLUTION_C3_MD_R1_74ECC664"
    }
}
