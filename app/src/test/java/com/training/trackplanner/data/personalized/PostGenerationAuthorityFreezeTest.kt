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
        // C38.1 updates ExposureRepresentation's movement-domain projection to the shared
        // H/V/UPPER_PULL relation and GapCandidateSelector's pull-coverage lookup to consume
        // that same relation; historical dose, ranking, and prescription authorities remain frozen.
        // C38.2 replaces broad runtime accessory-slot movement promotion with a canonical
        // movement-pattern/family resolver and supplies canonical relations to the snapshot;
        // historical dose and prescription authorities remain frozen.
        // C38.3 allows the user's requested 2-day schedule and 1-week horizon without silently
        // expanding either request; the reviewed scheduling map records those exact bounds.
        // All unrelated historical, style, numerical and prescription authorities remain frozen.
        // C28 lets the legacy selector report candidate identities without set-unit demand in
        // canonical EXP generation; it does not change candidate ranking or create a dose.
        // C32 narrows observed Strength exposure to the exact approved capability and 1-6 reps;
        // unrelated state construction and history authority remain frozen.
        // C32.1 preserves uncertain approved-key exposure as UNKNOWN rather than converting it
        // to absent, while keeping the canonical Strength identity boundary intact.
        // C32.1 also carries confirmed source IDs and the persisted planned-set link into history
        // so Strength intent is accepted only from the exact saved Strength owner and set.
        // C35 orders the complete candidate list by canonical priority before the frozen finite
        // kernel and records the exact order/funding trace; allocation math and reservation
        // semantics remain unchanged.
        // C38.4 adds trace sinks around existing allocation results; the finite kernel remains
        // separately byte-frozen below and the allocation math/authority is unchanged.
        // C38.4's builder observer also adds JSON imports before the protected builder class;
        // no protected selector or prescription policy was edited.
        val frozen = mapOf(
            // Domain-separated volume and court-deviation trace are the approved
            // changes in this follow-up; keep the remaining authorities frozen.
            "AthletePlanningStateBuilder.kt" to "4a1c4df0a5089b4e49668713d365b549c6b48eab0f3953c1a3fafd2a63df6a70",
            // Approved Phase A closeout correction: the snapshot resolver delegates to the
            // explicit-argument resolver; classification branch order/predicates remain
            // unchanged, and production uses the explicit overload only in the shadow ledger.
            "ExposureRepresentation.kt" to "95f0889987f81c5b5d4002398b98989dc2d4e3803add6cc8947b1b4ca39d0638",
            "PersonalizedDecisionComponents.kt" to "66c6acc65bc46f5bc957d8f4f8b57f072b44d2a621f060edabe5d047aed89958",
            // C20 extracts the exact session-time predicate so the live incumbent feasibility
            // evaluator calls the production placement bound instead of maintaining a duplicate.
            // C28 adds candidate-origin and unresolved-need diagnostics to MaterialDemand; C35
            // adds priority-order trace fields while leaving the finite allocation kernel frozen.
            "ExecutionAllocationPlanner.kt" to "95ba3e009b5a85f9083c6222c696817a04b1d1f336330d72a142a6701d45e9d7",
            "PerformancePrescriptionResolver.kt" to "48eda34c9e390ca109bbb1e19e7a8ac79802f01f60c25470f92c8eb7f87063be",
            "RecordBasedReviewedPolicy.kt" to "5dec65540c0602a923272caf347b6ec30e55640e4ed32182efb6c5b8ea609da6",
            "PlanningHistorySnapshotBuilder.kt" to "856201038ff76599ba4928ffae9d10080fc6c46ba670096fb9036387fb6dfbd3",
            "TrainingStateAssessment.kt" to "9027cb6d45422fc2cdecafa19fbec34a8b8fcba8c14cc594058d15e802e53bf2",
            "TrainingStateRouting.kt" to "bcbc769d004c80cf76a8b2ea1d69344be69b22892d769c591a58d0edecd042e0"
        )
        frozen.forEach { (name, expected) ->
            val source = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/$name").readText().replace("\r\n", "\n")
            val actual = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
            assertEquals(name, expected, actual)
        }
        val allocationPlanner = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/ExecutionAllocationPlanner.kt")
            .readText().replace("\r\n", "\n")
        val finiteKernel = allocationPlanner.substringAfter("/** Pure finite allocation kernel")
            .substringBefore("\nclass ExecutionCapacityPlanner")
        assertEquals("C35 changes candidate ordering outside the shared finite allocation kernel",
            "5b8ce2ca5ae9571c49be0304424eef8cff58d6786fd58bf9e2f778fcf3abbeb5",
            MessageDigest.getInstance("SHA-256").digest(finiteKernel.toByteArray()).joinToString("") { "%02x".format(it) })
        val builderAuthorities = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt")
            .readText().replace("\r\n", "\n").substringBefore("class PersonalizedProgramBuilder(")
        assertEquals("Continuity, GapCandidateSelector, PersonalizedPrescriptionPlanner, validation and repair stay frozen",
            "e327a19bfb52429138e2e873b03d7977222fed46e50e3e7c6443dabbc147fb79",
            MessageDigest.getInstance("SHA-256").digest(builderAuthorities.toByteArray()).joinToString("") { "%02x".format(it) })
    }
}
