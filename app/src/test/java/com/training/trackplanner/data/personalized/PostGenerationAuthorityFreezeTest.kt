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
        // change, while timing, score and prescription code remain unchanged.
        // TrainingStateAssessment additionally permits Stage 2 LOW_WEEK_RATIO .625 -> .85 only.
        // All unrelated historical, style, numerical and prescription authorities remain frozen.
        val frozen = mapOf(
            // Domain-separated volume and court-deviation trace are the approved
            // changes in this follow-up; keep the remaining authorities frozen.
            "AthletePlanningStateBuilder.kt" to "1e2f9e50cf77b92cb111bd2a6a2e59fc41e620be5e6cdfaa198c3777d8a9ad8d",
            "ExposureRepresentation.kt" to "c50938180f863a2316a8dff8eba21b49afce5cda295eb1ace7c7a7322408e4e8",
            "PersonalizedDecisionComponents.kt" to "66c6acc65bc46f5bc957d8f4f8b57f072b44d2a621f060edabe5d047aed89958",
            "ExecutionAllocationPlanner.kt" to "b1aae9692671a78a40dba4cb30cfd1bb274a1a533f2c4294852d61641fbe3e41",
            "PerformancePrescriptionResolver.kt" to "48eda34c9e390ca109bbb1e19e7a8ac79802f01f60c25470f92c8eb7f87063be",
            "RecordBasedReviewedPolicy.kt" to "cc5b12bf40b47226d75256455aa9bc34908f47f78e9ea3ac0fa76016f97bc25d",
            "PlanningHistorySnapshotBuilder.kt" to "f2d4fd0a1b46100acaa815947bfa70ebe7d309cf7c8bd72c60efa9353b419d79",
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
            "e5485c32ad5d1c9ef342a4e405cff2eb8108234d232242e9a426000f6b0bc4e8",
            MessageDigest.getInstance("SHA-256").digest(builderAuthorities.toByteArray()).joinToString("") { "%02x".format(it) })
    }
}
