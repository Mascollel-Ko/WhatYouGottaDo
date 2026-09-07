package com.training.trackplanner.data.personalized

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PostGenerationAuthorityFreezeTest {
    @Test fun `existing historical demand capacity timing and prescription authorities remain byte frozen`() {
        val root = generateSequence(File(System.getProperty("user.dir")), File::getParentFile).first { File(it, "settings.gradle.kts").isFile }
        // SHA-256 of LF-normalized sources at baseline 3d3c016; never regenerate for a post-process edit.
        val frozen = mapOf(
            "AthletePlanningStateBuilder.kt" to "873b1ea64d2ae6a1e554704edaacb457311120b65d0ad0f8209ef1c9d8aabcd0",
            "ExposureRepresentation.kt" to "c50938180f863a2316a8dff8eba21b49afce5cda295eb1ace7c7a7322408e4e8",
            "PersonalizedDecisionComponents.kt" to "c2364c349bd40028049d0028577df999f69efd33c2bd9d503a5499180dc2ff60",
            "ExecutionAllocationPlanner.kt" to "335e1541515bdc706bddb5232956abeff6e4712a821245e6c9f19c76b5479e61",
            "PerformancePrescriptionResolver.kt" to "48eda34c9e390ca109bbb1e19e7a8ac79802f01f60c25470f92c8eb7f87063be",
            "RecordBasedReviewedPolicy.kt" to "cc5b12bf40b47226d75256455aa9bc34908f47f78e9ea3ac0fa76016f97bc25d",
            "PlanningHistorySnapshotBuilder.kt" to "2df6c87925dcfc7c03a340e6a12a5158b2575e57330b13ba445bf36b62848828",
            "TrainingStateAssessment.kt" to "0c9aafc8216cd4b389dfcaaba7c4803ad19a5b135fdb537cc4faff1c06e95623",
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
