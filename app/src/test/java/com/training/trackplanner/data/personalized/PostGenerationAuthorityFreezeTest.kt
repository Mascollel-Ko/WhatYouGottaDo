package com.training.trackplanner.data.personalized

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PostGenerationAuthorityFreezeTest {
    @Test fun `existing historical demand capacity timing and prescription authorities remain byte frozen`() {
        val root = generateSequence(File(System.getProperty("user.dir")), File::getParentFile).first { File(it, "settings.gradle.kts").isFile }
        // Baseline 3d3c016, except the explicitly approved full incumbent ranking in AthletePlanningStateBuilder.
        // ExecutionAllocationPlanner additionally permits the approved Stage 1 MAIN placement review.
        // TrainingStateAssessment additionally permits Stage 2 LOW_WEEK_RATIO .625 -> .85 only.
        // All unrelated historical, style, numerical and prescription authorities remain frozen.
        val frozen = mapOf(
            "AthletePlanningStateBuilder.kt" to "edc55032496651ad17488b20275f163f51f54c3b57b3aef5c3b64195b06585f7",
            "ExposureRepresentation.kt" to "c50938180f863a2316a8dff8eba21b49afce5cda295eb1ace7c7a7322408e4e8",
            "PersonalizedDecisionComponents.kt" to "c2364c349bd40028049d0028577df999f69efd33c2bd9d503a5499180dc2ff60",
            "ExecutionAllocationPlanner.kt" to "7bc2456c6bbfa7662bc27646d3bc51277730a299d72198d42eede412b778a0f0",
            "PerformancePrescriptionResolver.kt" to "48eda34c9e390ca109bbb1e19e7a8ac79802f01f60c25470f92c8eb7f87063be",
            "RecordBasedReviewedPolicy.kt" to "cc5b12bf40b47226d75256455aa9bc34908f47f78e9ea3ac0fa76016f97bc25d",
            "PlanningHistorySnapshotBuilder.kt" to "2df6c87925dcfc7c03a340e6a12a5158b2575e57330b13ba445bf36b62848828",
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
            "24a45f447d98757f5fdd0e8132aadbf1b894bb1ac1a053ddade80ad92a930343",
            MessageDigest.getInstance("SHA-256").digest(builderAuthorities.toByteArray()).joinToString("") { "%02x".format(it) })
    }
}
