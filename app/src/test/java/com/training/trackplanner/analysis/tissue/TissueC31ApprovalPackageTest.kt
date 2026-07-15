package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TissueC31ApprovalPackageTest {
    @Test
    fun correctedScopeHashIsCompleteDeterministicAndContextSensitive() {
        val inputParts = inputFiles.mapValues { (key, file) ->
            TissueMetadataValidator.semanticCsvHash(
                if (key == "requestResolutions") file.readLines(Charsets.UTF_8)
                    .filterNot { C31_REQUEST_ID in it }.joinToString("\n")
                else file.readText(Charsets.UTF_8)
            )
        }
        val auditHash = TissueMetadataValidator.combinedHash(inputParts)
        val scopeHash = TissueMetadataValidator.combinedHash(inputParts + scopeReferences(auditHash))

        inputParts.forEach { (key, hash) -> assertEquals(key, hash, request.getValue("${key}SnapshotHash")) }
        assertEquals(auditHash, request.getValue("auditInputSnapshotHash"))
        assertEquals(scopeHash, request.getValue("approvalScopeHash"))
        assertEquals("TISSUE_APPROVAL_REQUEST_C3_1_${scopeHash.take(16).uppercase()}", request.getValue("approvalRequestId"))

        val candidateFile = inputFiles.getValue("claimCandidates")
        val lines = candidateFile.readLines(Charsets.UTF_8).filter(String::isNotBlank)
        val reordered = (listOf(lines.first()) + lines.drop(1).reversed()).joinToString("\n")
        assertEquals(inputParts.getValue("claimCandidates"), TissueMetadataValidator.semanticCsvHash(reordered))

        val changedContext = candidateFile.readText(Charsets.UTF_8).replaceFirst("JUMP_LANDING", "DROP_LANDING")
        assertNotEquals(inputParts.getValue("claimCandidates"), TissueMetadataValidator.semanticCsvHash(changedContext))
    }

    @Test
    fun achillesPointsAreExactConditionAnchorsNotIntervals() {
        assertTrue(TissueC31Validator.guidance(guidance).errors.toString(), TissueC31Validator.guidance(guidance).isValid)
        assertEquals(2, guidance.size)
        assertTrue(guidance.all { it.rubricKind == TissueRubricKind.CONDITION_ANCHOR })
        assertTrue(guidance.all { it.metricLowerBound == null && it.metricUpperBound == null && it.exactConditionMatchRequired })
        assertEquals(setOf("MODERATE", "VERY_HIGH"), guidance.map { it.loadBand }.toSet())

        val moderate = guidance.single { it.anchorValue == 3.0 }
        assertTrue(moderate.matches(3.0, "ex_5ca7133f", "C3COND_R1C_02_ACH_SINGLE_CALF_PEAK"))
        assertFalse(moderate.matches(3.1, "ex_5ca7133f", "C3COND_R1C_02_ACH_SINGLE_CALF_PEAK"))
        assertFalse(moderate.matches(3.0, "ex_314df428", "C3COND_R1C_02_ACH_SINGLE_CALF_PEAK"))
        assertFalse(guidance.any { it.matches(5.0, "ex_5ca7133f", "C3COND_R1C_02_ACH_SINGLE_CALF_PEAK") })
    }

    @Test
    fun historicalRequestIsImmutableAndReplacementRemainsNonProduction() {
        assertEquals(
            "c51f7fd111b2466addfc28317090c8d90eaf8e73c77169dd8dff9354b63a7378",
            asset("tissue_review_batch_approval_request_c3_v1.csv").normalizedPayloadSha256()
        )
        assertEquals("TISSUE_APPROVAL_REQUEST_C3_MD_48F86FEE6C39D28B", request.getValue("supersededApprovalRequestId"))
        assertEquals("48f86fee6c39d28b18e8ab9fbacd748e52a606db30c9b4cbfd377be4193162b8", request.getValue("supersededApprovalScopeHash"))
        assertEquals("TISSUE_APPROVAL_RESOLUTION_C3_1_48F86FEE", request.getValue("supersessionResolutionId"))
        assertEquals("PENDING_HUMAN_DECISION", request.getValue("requestStatus"))
        assertEquals("C3_1_ONTOLOGY_CORRECTION_PACKAGE_PARTIAL", request.getValue("completionStatus"))
        assertEquals("49", request.getValue("metricExtractionCount"))
        assertEquals("30", request.getValue("candidateCount"))
        assertEquals("2", request.getValue("conditionAnchorCount"))
        assertEquals("0", request.getValue("intervalRubricCount"))
        assertEquals("0", request.getValue("orderingRuleCount"))
        assertEquals("48", request.getValue("researchDecisionCount"))
        assertEquals("188", request.getValue("correctionDispositionCount"))
        assertEquals("15", request.getValue("sourceCount"))
        assertEquals("27", request.getValue("blockedResearchTargetCount"))

        assertTrue(table("tissue_review_batch_approval_v1.csv").isEmpty())
        assertTrue(table("tissue_evidence_claims_v1.csv").isEmpty())
        assertTrue(table("tissue_evidence_blind_review_v1.csv").isEmpty())
        profileFiles.forEach { assertTrue(it, table(it).isEmpty()) }
    }

    @Test
    fun requestStatementAuditAndHumanDocumentExposeExactPartialBoundary() {
        val expected = """I approve correctedTissueApprovalRequestId=${request.getValue("approvalRequestId")},
approvalScopeHash=${request.getValue("approvalScopeHash")},
reviewPath=SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL,
covering exactly 49 corrected metric extractions, 30 corrected claim candidates, 2 condition anchors, 0 interval rubrics, 0 ordering rules, 48 research decisions, 188 correction dispositions, and 15 verified source records in the listed ontology, context, evidence-relation, external-load, supersession, canonical-mapping, and publication-integrity snapshots.
I understand this package is same-session, non-independent, non-production, contains 27 explicitly blocked research targets, does not classify neighboring anchor values, permits no anchor interpolation or unapproved transfer, and does not approve a universal tissue-burden score or fixed multidimensional weights."""
        assertEquals(expected, request.getValue("requiredUserStatement"))

        val audits = table("tissue_metadata_audit_manifest_v1.csv")
        val audit = audits.single { it.getValue("auditBatchId") == batchId }
        assertEquals("ONTOLOGY_CORRECTION_BATCH", audit.getValue("auditScope"))
        assertEquals("PRODUCTION_REVIEW_REQUIRED", audit.getValue("auditDecision"))
        assertEquals("0", audit.getValue("approvalRequestRubricCount"))
        assertEquals("0", audit.getValue("humanBatchApprovalCount"))
        assertEquals("0", audit.getValue("formalFinalClaimCount"))
        assertEquals("0", audit.getValue("blindReviewCount"))
        assertEquals("0", audit.getValue("productionProfileCount"))
        assertEquals(1, audits.count { it.getValue("auditBatchId") == batchId })

        val doc = document("tissue_load_phase_c3_1_approval_request.md")
        table("tissue_source_metric_extraction_c3_1_v1.csv").forEach { assertTrue(it.getValue("metricExtractionId"), doc.contains(it.getValue("metricExtractionId"))) }
        table("tissue_evidence_claim_candidates_c3_1_v1.csv").forEach { assertTrue(it.getValue("claimCandidateId"), doc.contains(it.getValue("claimCandidateId"))) }
        guidance.forEach { assertTrue(it.guidanceId, doc.contains(it.guidanceId)) }
        assertTrue(doc.contains("Interval Rubrics (0)"))
        assertTrue(doc.contains("Ordering Rules (0)"))
        assertTrue(doc.replace("\r\n", "\n").contains(expected))
        assertFalse(doc.contains("APPROVED"))
    }

    private fun scopeReferences(auditHash: String) = mapOf(
        "reviewPath" to request.getValue("reviewPath"),
        "auditManifestId" to request.getValue("auditManifestId"),
        "auditInputSnapshotHash" to auditHash,
        "supersededApprovalRequestId" to request.getValue("supersededApprovalRequestId"),
        "supersededApprovalScopeHash" to request.getValue("supersededApprovalScopeHash"),
        "supersessionResolutionId" to request.getValue("supersessionResolutionId"),
        "completionStatus" to request.getValue("completionStatus")
    )

    private val request by lazy { table("tissue_review_batch_approval_request_c3_1_v1.csv").single() }
    private val guidance by lazy { TissueC31Parser.guidance(asset("tissue_load_guidance_c3_1_v1.csv").readText(Charsets.UTF_8)) }
    private fun table(name: String) = TissueMetadataParser.table(asset(name).readText(Charsets.UTF_8)).rows
    private fun asset(name: String) = sequenceOf(File("src/main/assets/metadata/tissue_load_v1/$name"), File("app/src/main/assets/metadata/tissue_load_v1/$name")).first(File::exists)
    private fun document(name: String) = sequenceOf(File("docs/$name"), File("../docs/$name")).first(File::exists).readText(Charsets.UTF_8)
    private fun File.normalizedPayloadSha256() = readText(Charsets.UTF_8).removePrefix("\uFEFF").replace("\r\n", "\n").toByteArray(Charsets.UTF_8).sha256()
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private val inputFiles by lazy {
        linkedMapOf(
            "c31MechanicalModes" to asset("tissue_mechanical_load_mode_registry_c3_1_v1.csv"), "eventContexts" to asset("tissue_event_context_registry_v1.csv"), "movementPhases" to asset("tissue_movement_phase_registry_v1.csv"), "positionContexts" to asset("tissue_position_context_registry_v1.csv"), "functionalDemands" to asset("tissue_functional_demand_registry_v1.csv"), "tissueResponses" to asset("tissue_response_metric_registry_v1.csv"), "evidenceRelations" to asset("tissue_evidence_relation_registry_v1.csv"), "rubricKinds" to asset("tissue_rubric_kind_registry_v1.csv"), "externalLoadRepresentations" to asset("tissue_external_load_representation_registry_v1.csv"), "temporalMetrics" to asset("tissue_temporal_metric_registry_v1.csv"), "c31MeasurementMetrics" to asset("tissue_measurement_metric_registry_c3_1_v1.csv"), "normalizationBases" to asset("tissue_normalization_registry_v1.csv"), "c31Dimensions" to asset("tissue_load_dimension_registry_c3_1_v1.csv"), "dimensionCorrections" to asset("tissue_c3_1_dimension_correction_v1.csv"), "correctionDispositions" to asset("tissue_c3_1_correction_disposition_v1.csv"), "metricExtractions" to asset("tissue_source_metric_extraction_c3_1_v1.csv"), "claimCandidates" to asset("tissue_evidence_claim_candidates_c3_1_v1.csv"), "guidance" to asset("tissue_load_guidance_c3_1_v1.csv"), "researchDecisions" to asset("tissue_research_decision_c3_1_v1.csv"), "proxyMappings" to asset("tissue_proxy_mapping_c3_1_v1.csv"), "sourceRereads" to asset("tissue_source_reread_c3_1_v1.csv"), "supersededC3Request" to asset("tissue_review_batch_approval_request_c3_v1.csv"), "requestResolutions" to asset("tissue_approval_request_resolution_v1.csv"), "historicalEvidence" to asset("tissue_load_evidence_registry_v1.csv"), "c3Evidence" to asset("tissue_load_evidence_registry_c3_v1.csv"), "historicalVerification" to asset("tissue_source_verification_v1.csv"), "c3Verification" to asset("tissue_source_verification_c3_v1.csv"), "historicalIntegrity" to asset("tissue_publication_integrity_verification_v1.csv"), "c3Integrity" to asset("tissue_publication_integrity_verification_c3_v1.csv"), "canonicalMapping" to asset("tissue_canonical_exercise_mapping_audit_v1.csv"), "transferCorrespondence" to asset("tissue_exercise_variant_correspondence_c3_v1.csv"), "upperBacklog" to asset("tissue_upper_limb_research_backlog_c3_v1.csv"),
            "canonicalExerciseMetadata" to asset("../canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")
        )
    }

    private val profileFiles = listOf("exercise_joint_load_profiles_v1.csv", "exercise_tendon_load_profiles_v1.csv", "exercise_ligament_load_profiles_v1.csv", "exercise_fascia_load_profiles_v1.csv")
    private companion object {
        const val batchId = "TISSUE_C3_1_ONTOLOGY_CORRECTION"
        const val C31_REQUEST_ID = "TISSUE_APPROVAL_REQUEST_C3_1_A00141AC34448C59"
    }
}
