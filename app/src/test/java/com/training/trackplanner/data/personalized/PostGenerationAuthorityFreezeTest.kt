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
        // C24 adds an exact approved-task prescription branch and task exposure fields before
        // the builder; legacy prescription branches remain protected by this updated source hash.
        // C30 adds typed movement gap priority to the shared representation analysis and extracts
        // the existing hard movement restriction mapping for B3; historical dose predicates remain frozen.
        // All unrelated historical, style, numerical and prescription authorities remain frozen.
        // C28 lets the legacy selector report candidate identities without set-unit demand in
        // canonical EXP generation; it does not change candidate ranking or create a dose.
        val frozen = mapOf(
            // Domain-separated volume and court-deviation trace are the approved
            // changes in this follow-up; keep the remaining authorities frozen.
            "AthletePlanningStateBuilder.kt" to "1e2f9e50cf77b92cb111bd2a6a2e59fc41e620be5e6cdfaa198c3777d8a9ad8d",
            // Approved Phase A closeout correction: the snapshot resolver delegates to the
            // explicit-argument resolver; classification branch order/predicates remain
            // unchanged, and production uses the explicit overload only in the shadow ledger.
            "ExposureRepresentation.kt" to "d6a417b8bdb86ca8cfdefaac0fa701d6d2ec0d975418781f48ecd3f607d22c1b",
            "PersonalizedDecisionComponents.kt" to "66c6acc65bc46f5bc957d8f4f8b57f072b44d2a621f060edabe5d047aed89958",
            // C20 extracts the exact session-time predicate so the live incumbent feasibility
            // evaluator calls the production placement bound instead of maintaining a duplicate.
            // C28 adds candidate-origin and unresolved-need diagnostics to MaterialDemand; the
            // allocation, timing, and prescription authority predicates remain frozen.
            "ExecutionAllocationPlanner.kt" to "ac63cbefadd6a3a5413c4dd65b3b84ee6ed4fd9cffa6c16836fda07b33737ed4",
            "PerformancePrescriptionResolver.kt" to "48eda34c9e390ca109bbb1e19e7a8ac79802f01f60c25470f92c8eb7f87063be",
            "RecordBasedReviewedPolicy.kt" to "915be5354ac988774f740a366655d26f74cb3c8e2f820eecf63e8914b671ed08",
            "PlanningHistorySnapshotBuilder.kt" to "8cb9bd8c1be966e4878d2fa7cb3bf72ea0f722c7a565ac38a728323da755ddc3",
            "TrainingStateAssessment.kt" to "9027cb6d45422fc2cdecafa19fbec34a8b8fcba8c14cc594058d15e802e53bf2",
            "TrainingStateRouting.kt" to "bcbc769d004c80cf76a8b2ea1d69344be69b22892d769c591a58d0edecd042e0"
        )
        frozen.forEach { (name, expected) ->
            val source = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/$name").readText().replace("\r\n", "\n")
            val actual = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
            assertEquals(name, expected, actual)
        }
        val builderAuthorities = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt")
            .readText().replace("\r\n", "\n").substringBefore("class PersonalizedProgramBuilder(")
        assertEquals("Continuity, GapCandidateSelector, PersonalizedPrescriptionPlanner, validation and repair stay frozen",
            "9f7a436d8db9942209add11e38130f9bfe19815e8c5b51a00296179117ccd1b4",
            MessageDigest.getInstance("SHA-256").digest(builderAuthorities.toByteArray()).joinToString("") { "%02x".format(it) })
    }
}
