package com.training.trackplanner.analysis.tissue

import java.security.MessageDigest

object TissuePhaseCContract {
    const val reviewBatchId = "TISSUE_REAUDIT_C_LOWER_KNEE_ANKLE"
    const val achillesAdjudicationId = "USER_ADJUDICATION_ACHILLES_HOP_TRANSFER_V1"
    const val pfjAdjudicationId = "USER_ADJUDICATION_PFJ_COMPOSITE_COMPRESSION_V1"

    val adjudicationIds = setOf(achillesAdjudicationId, pfjAdjudicationId)

    fun reauditId(draftClaimId: String, sourceId: String): String =
        "REAUDIT_C_${digest("$reviewBatchId|$draftClaimId|$sourceId")}".uppercase()

    fun claimCandidateId(draftClaimId: String, reauditId: String): String =
        "CLAIM_C_${digest("$reviewBatchId|$draftClaimId|$reauditId")}".uppercase()

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { "%02x".format(it) }
}
