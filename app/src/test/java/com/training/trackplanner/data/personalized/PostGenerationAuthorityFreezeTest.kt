package com.training.trackplanner.data.personalized

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PostGenerationAuthorityFreezeTest {
    @Test fun `existing historical demand capacity timing and prescription authorities remain byte frozen`() {
        val root = generateSequence(File(System.getProperty("user.dir")), File::getParentFile).first { File(it, "settings.gradle.kts").isFile }
        // Baseline 3d3c016, except the explicitly approved full incumbent ranking in AthletePlanningStateBuilder.
        // ExecutionAllocationPlanner permits Stage 1 review and the reviewed exact-primary state handoff
        // after authorization only; the greedy funding-feasibility kernel is the approved performance
        // change, while timing, score and prescription code remain unchanged. C7 adds owner-local
            // finite-allocation evidence for B7 displacement adjudication without changing allocation authority.
            // C10 additionally adds source-emitted trace fields/sinks at allocation and placement mutations;
            // the C9 standard generated-coverage report remains byte-identical.
        // TrainingStateAssessment additionally permits Stage 2 LOW_WEEK_RATIO .625 -> .85 only.
        // C15 makes unresolved calibration rows ineligible as observed planning history;
        // it does not change any historical authority for completed, load-resolved sets.
        // C23 adds typed, lossless task-guide shapes beside the existing legacy scalar adapter;
        // the adapter fields and resolver behavior remain byte/field compatible.
        // All unrelated historical, style, numerical and prescription authorities remain frozen.
        val frozen = mapOf(
            // Domain-separated volume and court-deviation trace are the approved
            // changes in this follow-up; keep the remaining authorities frozen.
            "AthletePlanningStateBuilder.kt" to "1e2f9e50cf77b92cb111bd2a6a2e59fc41e620be5e6cdfaa198c3777d8a9ad8d",
            // Approved Phase A closeout correction: the snapshot resolver delegates to the
            // explicit-argument resolver; classification branch order/predicates remain
            // unchanged, and production uses the explicit overload only in the shadow ledger.
            "ExposureRepresentation.kt" to "fd0e5b041f83fa5d6cc512cf8713a1daed5d58c69d7169757663d78f1582b368",
            "PersonalizedDecisionComponents.kt" to "66c6acc65bc46f5bc957d8f4f8b57f072b44d2a621f060edabe5d047aed89958",
            // C20 extracts the exact session-time predicate so the live incumbent feasibility
            // evaluator calls the production placement bound instead of maintaining a duplicate.
            "ExecutionAllocationPlanner.kt" to "3eeb5c937499367537134884143950c6d64a1fec59595a1de8646401c48b4644",
            "PerformancePrescriptionResolver.kt" to "48eda34c9e390ca109bbb1e19e7a8ac79802f01f60c25470f92c8eb7f87063be",
            "RecordBasedReviewedPolicy.kt" to "915be5354ac988774f740a366655d26f74cb3c8e2f820eecf63e8914b671ed08",
            "PlanningHistorySnapshotBuilder.kt" to "8cb9bd8c1be966e4878d2fa7cb3bf72ea0f722c7a565ac38a728323da755ddc3",
            "TrainingStateAssessment.kt" to "9027cb6d45422fc2cdecafa19fbec34a8b8fcba8c14cc594058d15e802e53bf2",
            "TrainingStateRouting.kt" to "d086f1008c2247bda65e33b5db2277fc962a0ea666163dfe0f46e119a434b5f7"
        )
        frozen.forEach { (name, expected) ->
            val source = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/$name").readText().replace("\r\n", "\n")
            val actual = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
            assertEquals(name, expected, actual)
        }
        val builderAuthorities = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt")
            .readText().replace("\r\n", "\n").substringBefore("class PersonalizedProgramBuilder(")
        assertEquals("Continuity, GapCandidateSelector, PersonalizedPrescriptionPlanner, validation and repair stay frozen",
            "27e9410a3b2663d7cd977f4965c5f70e4871d64becf25f8b4135929ac2ead017",
            MessageDigest.getInstance("SHA-256").digest(builderAuthorities.toByteArray()).joinToString("") { "%02x".format(it) })
    }
}
