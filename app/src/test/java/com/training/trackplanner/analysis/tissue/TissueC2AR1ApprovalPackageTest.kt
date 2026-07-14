package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TissueC2AR1ApprovalPackageTest {
    @Test
    fun revisedRequestHashesEveryGovernedArtifactDeterministically() {
        val inputParts = inputFiles.mapValues { (_, name) ->
            TissueMetadataValidator.semanticCsvHash(tissueAsset(name))
        }
        val auditInputHash = TissueMetadataValidator.combinedHash(inputParts)
        val scopeHash = TissueMetadataValidator.combinedHash(inputParts + mapOf(
            "reviewPath" to request.getValue("reviewPath"),
            "auditManifestId" to request.getValue("auditManifestId"),
            "auditInputSnapshotHash" to auditInputHash
        ))

        assertEquals(request.getValue("auditInputSnapshotHash"), auditInputHash)
        assertEquals(request.getValue("approvalScopeHash"), scopeHash)
        assertEquals(
            "TISSUE_APPROVAL_REQUEST_C2A_R1_${scopeHash.take(16).uppercase()}",
            request.getValue("approvalRequestId")
        )

        val candidates = tissueAsset("tissue_evidence_claim_candidates_revised_v1.csv")
        val lines = candidates.lines().filter(String::isNotEmpty)
        val reordered = (listOf(lines.first()) + lines.drop(1).reversed()).joinToString("\n")
        val reorderedParts = inputParts + (
            "revisedCandidates" to TissueMetadataValidator.semanticCsvHash(reordered)
        )
        assertEquals(scopeHash, TissueMetadataValidator.combinedHash(reorderedParts + mapOf(
            "reviewPath" to request.getValue("reviewPath"),
            "auditManifestId" to request.getValue("auditManifestId"),
            "auditInputSnapshotHash" to TissueMetadataValidator.combinedHash(reorderedParts)
        )))

        val changedParts = inputParts + (
            "revisedCandidates" to TissueMetadataValidator.semanticCsvHash(candidates.replaceFirst("0.5", "0.5001"))
        )
        assertNotEquals(scopeHash, TissueMetadataValidator.combinedHash(changedParts + mapOf(
            "reviewPath" to request.getValue("reviewPath"),
            "auditManifestId" to request.getValue("auditManifestId"),
            "auditInputSnapshotHash" to TissueMetadataValidator.combinedHash(changedParts)
        )))
    }

    @Test
    fun requestListsTheExactRevisedScopeAndFutureStatement() {
        assertEquals("24", request.getValue("candidateCount"))
        assertEquals("2", request.getValue("rubricCount"))
        assertEquals("12", request.getValue("oldCandidateDispositionCount"))
        assertEquals("5", request.getValue("oldRubricDispositionCount"))
        assertEquals("12", request.getValue("humanDirectiveCount"))
        assertEquals("13", request.getValue("researchDecisionCount"))
        assertEquals("49", request.getValue("canonicalMappingCount"))
        assertEquals("6", request.getValue("blockedResearchTargetCount"))
        assertEquals("PENDING_HUMAN_DECISION", request.getValue("requestStatus"))

        val expected = """I approve revisedApprovalRequestId=${request.getValue("approvalRequestId")},
approvalScopeHash=${request.getValue("approvalScopeHash")},
reviewPath=SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL,
covering exactly 24 revised claim candidates, 2 revised rubric rows, 12 old-candidate dispositions, 5 old-rubric dispositions, 12 human research directives, 13 research decisions, 49 canonical mappings, and the listed source and publication-integrity snapshots.
I understand this revised package is same-session, non-independent, and non-production until formal promotion."""
        assertEquals(expected, request.getValue("requiredUserStatement"))
    }

    @Test
    fun oldRequestIsImmutableAndNoPromotionLedgerWasPopulated() {
        val oldRequest = assetFile("metadata/tissue_load_v1/tissue_review_batch_approval_request_v1.csv")
        assertEquals(
            "07acc2e6290206c224ee853a527db99ac8ffcb6091eac2e7934094a285684176",
            oldRequest.readBytes().sha256()
        )
        assertNotEquals(oldRequestTable.single().getValue("approvalRequestId"), request.getValue("approvalRequestId"))
        assertNotEquals(oldRequestTable.single().getValue("approvalScopeHash"), request.getValue("approvalScopeHash"))
        assertEquals(0, table("tissue_review_batch_approval_v1.csv").size)
        assertEquals(0, table("tissue_evidence_claims_v1.csv").size)
        assertEquals(0, table("tissue_evidence_blind_review_v1.csv").size)
        profileFiles.forEach { assertEquals(it, 0, table(it).size) }
    }

    @Test
    fun historicalAuditIsPartialAndRecordsTheNonProductionBoundary() {
        val audit = TissueMetadataParser.auditManifests(tissueAsset("tissue_metadata_audit_manifest_v1.csv"))
            .single { it.values.getValue("auditBatchId") == "TISSUE_RESEARCH_C2A_R1_LOWER_REVISED" }.values

        assertEquals("PRODUCTION_REVIEW_REQUIRED", audit.getValue("auditDecision"))
        assertEquals("REVISED_APPROVAL_PACKAGE_PARTIAL", audit.getValue("completionStatus"))
        assertEquals(request.getValue("approvalScopeHash"), audit.getValue("approvalScopeHash"))
        assertEquals("24", audit.getValue("approvalRequestCandidateCount"))
        assertEquals("2", audit.getValue("approvalRequestRubricCount"))
        assertEquals("0", audit.getValue("humanBatchApprovalCount"))
        assertEquals("0", audit.getValue("formalFinalClaimCount"))
        assertEquals("0", audit.getValue("productionProfileCount"))
        assertTrue("new sources 0" in audit.getValue("auditNotes"))
    }

    @Test
    fun upperLimbWorkIsQueuedWithoutCreatingClaims() {
        val backlog = table("tissue_rubric_research_backlog_v1.csv").single()

        assertEquals("TISSUE_RUBRIC_B2_UPPER_PRESS_PULL", backlog.getValue("backlogBatchId"))
        assertEquals(expectedUpperTissues, backlog.getValue("tissueIds").tokens())
        assertEquals(expectedExerciseFamilies, backlog.getValue("exerciseFamilies").tokens())
        assertEquals("QUEUED_FOR_SEPARATE_RESEARCH", backlog.getValue("status"))
    }

    private val request by lazy { table("tissue_review_batch_approval_request_revised_v1.csv").single() }
    private val oldRequestTable by lazy { table("tissue_review_batch_approval_request_v1.csv") }
    private fun String.tokens() = split('|').filter(String::isNotBlank).toSet()
    private fun table(name: String) = TissueMetadataParser.table(tissueAsset(name)).rows
    private fun tissueAsset(name: String) = assetFile("metadata/tissue_load_v1/$name").readText(Charsets.UTF_8)
    private fun assetFile(relative: String) = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists)
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
        .joinToString("") { "%02x".format(it) }

    private val inputFiles = linkedMapOf(
        "oldRequest" to "tissue_review_batch_approval_request_v1.csv",
        "requestResolution" to "tissue_approval_request_resolution_v1.csv",
        "claimDispositions" to "tissue_claim_candidate_disposition_v1.csv",
        "rubricDispositions" to "tissue_rubric_disposition_v1.csv",
        "humanDirectives" to "tissue_human_research_directive_v1.csv",
        "revisedCandidates" to "tissue_evidence_claim_candidates_revised_v1.csv",
        "revisedRubrics" to "tissue_load_band_rubric_revised_v1.csv",
        "researchDecisions" to "tissue_rubric_research_log_revised_v1.csv",
        "canonicalMappings" to "tissue_canonical_exercise_mapping_audit_v1.csv",
        "sourceSpecificMetrics" to "tissue_source_specific_metric_v1.csv",
        "evidenceRegistry" to "tissue_load_evidence_registry_v1.csv",
        "sourceVerification" to "tissue_source_verification_v1.csv",
        "publicationIntegrity" to "tissue_publication_integrity_verification_v1.csv"
    )
    private val profileFiles = listOf(
        "exercise_joint_load_profiles_v1.csv", "exercise_tendon_load_profiles_v1.csv",
        "exercise_ligament_load_profiles_v1.csv", "exercise_fascia_load_profiles_v1.csv"
    )
    private val expectedUpperTissues = setOf(
        "GLENOHUMERAL", "ACROMIOCLAVICULAR", "SCAPULOTHORACIC_FUNCTIONAL_COMPLEX", "SUPRASPINATUS_TENDON",
        "POSTERIOR_ROTATOR_CUFF_TENDON", "SUBSCAPULARIS_TENDON", "LONG_HEAD_BICEPS_TENDON",
        "PECTORALIS_MAJOR_TENDON", "TRICEPS_TENDON", "DISTAL_BICEPS_TENDON", "COMMON_FLEXOR_TENDON",
        "COMMON_EXTENSOR_TENDON", "WRIST_FLEXOR_TENDON_GROUP", "WRIST_EXTENSOR_TENDON_GROUP",
        "WRIST_LIGAMENT_TFCC_COMPLEX", "HUMEROULNAR", "HUMERORADIAL", "PROXIMAL_RADIOULNAR",
        "DISTAL_RADIOULNAR", "RADIOCARPAL_WRIST", "ELBOW_UCL", "ELBOW_LCL"
    )
    private val expectedExerciseFamilies = setOf(
        "Bench press", "Incline press", "Push-up variants", "Dips", "Overhead press", "Landmine press",
        "Lateral raise", "Front raise", "Fly", "Pull-up", "Assisted pull-up", "Lat pulldown", "Rows",
        "Biceps curls", "Triceps extensions", "Wrist and grip exercises"
    )
}
