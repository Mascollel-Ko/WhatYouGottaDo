package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class TissueC4ASupersessionTest {
    @Test
    fun c31RequestRemainsImmutableAndCannotBeApproved() {
        assertEquals(C31_REQUEST_SHA256, assetFile(C31_REQUEST_FILE).normalizedPayloadSha256())

        val resolution = TissueEvidenceParser.approvalRequestResolutions(asset(RESOLUTION_FILE))
            .single { it.approvalRequestId == C31_REQUEST_ID }
        assertEquals(C31_SCOPE_HASH, resolution.approvalScopeHash)
        assertEquals(TissueApprovalRequestResolutionStatus.SUPERSEDED_BEFORE_APPROVAL, resolution.resolutionStatus)
        assertEquals("TISSUE_C4A_MTC_METADATA_FOUNDATION", resolution.replacementResearchBatchId)

        assertTrue(TissueEvidenceParser.batchApprovals(asset("tissue_review_batch_approval_v1.csv")).isEmpty())
        assertTrue(TissueEvidenceParser.finalClaims(asset("tissue_evidence_claims_v1.csv")).isEmpty())
        assertTrue(TissueEvidenceParser.blindReviews(asset("tissue_evidence_blind_review_v1.csv")).isEmpty())
        assertEquals(0, productionProfileCount())
    }

    private fun productionProfileCount() = listOf(
        "exercise_tendon_load_profiles_v1.csv",
        "exercise_ligament_load_profiles_v1.csv",
        "exercise_joint_load_profiles_v1.csv",
        "exercise_fascia_load_profiles_v1.csv"
    ).sumOf { TissueMetadataParser.profiles(asset(it)).count(TissueLoadProfile::productionEligibility) }

    private fun asset(name: String) = assetFile("metadata/tissue_load_v1/$name").readText(Charsets.UTF_8)
    private fun assetFile(relative: String) = sequenceOf(
        File("src/main/assets/$relative"), File("app/src/main/assets/$relative")
    ).first(File::exists)
    private fun File.normalizedPayloadSha256() = readText(Charsets.UTF_8).removePrefix("\uFEFF")
        .replace("\r\n", "\n").toByteArray(Charsets.UTF_8).sha256()
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val C31_REQUEST_FILE = "metadata/tissue_load_v1/tissue_review_batch_approval_request_c3_1_v1.csv"
        const val RESOLUTION_FILE = "tissue_approval_request_resolution_v1.csv"
        const val C31_REQUEST_ID = "TISSUE_APPROVAL_REQUEST_C3_1_A00141AC34448C59"
        const val C31_SCOPE_HASH = "a00141ac34448c5904db5aff2a514c599b8d47582e0d5bbac6bb752a85bb3b06"
        const val C31_REQUEST_SHA256 = "28cbc9586cbb0bf1ab0e7f638ca96485a377c4abf86b1b4b7d5b0be767434a96"
    }
}
