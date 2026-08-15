package com.training.trackplanner.analysis.strengthperformance

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.SeedData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrengthMetadataProxyFallbackImpactTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val registry = StrengthPerformanceRegistry.fromContext(context)

    @Test
    fun frozenImpactReportListsEveryRemovedMetadataOnlyProxy() {
        val report = reportRows().associateBy { it.getValue("stableKey") }
        val impacted = SeedData.exercises(context).mapNotNull { exercise ->
            legacyMetadataTarget(exercise)?.let { exercise.stableKey to it }
        }.toMap()

        assertEquals(36, report.size)
        assertEquals(report.keys, impacted.keys)
        report.forEach { (stableKey, row) ->
            assertEquals(row.getValue("legacyInferredTarget"), impacted.getValue(stableKey))
            assertEquals("NO", row.getValue("exactReviewedRow"))
            assertEquals("NO_PROXY", row.getValue("postRemoval"))
            assertTrue(registry.proxyLoadings(stableKey).isEmpty())
        }
    }

    @Test
    fun reviewedRegistryRowsRemainExactAndUnchanged() {
        val exercises = SeedData.exercises(context)
        val reviewed = exercises.flatMap { registry.proxyLoadings(it.stableKey) }

        assertEquals(21, reviewed.size)
        assertEquals(21, reviewed.map { it.exerciseStableKey }.distinct().size)
        assertTrue(reviewed.all { it.reviewedStatus == "REVIEWED" })
    }

    private fun legacyMetadataTarget(exercise: Exercise): String? {
        if (!exercise.estimated1RmEligible || exercise.needsReview || registry.proxyLoadings(exercise.stableKey).isNotEmpty()) {
            return null
        }
        val text = listOf(
            exercise.familyId,
            exercise.familyRole,
            exercise.movementPattern,
            exercise.movementCategory,
            exercise.strengthProgressionGroup,
            exercise.mainLiftGroup,
            exercise.analysisEligibility,
            exercise.equipment,
            exercise.equipmentTags
        ).joinToString("|").uppercase()
        return when {
            text.containsAny("SQUAT", "KNEE_DOMINANT", "LEG_PRESS", "LUNGE", "SPLIT_SQUAT", "STEP_UP") ->
                StrengthPerformanceRegistry.BACK_SQUAT.value
            text.containsAny("DEADLIFT", "HINGE", "ROMANIAN", "_RDL", "HIP_THRUST", "GLUTE_BRIDGE") ->
                StrengthPerformanceRegistry.CONVENTIONAL_DEADLIFT.value
            text.containsAny("BENCH", "HORIZONTAL_PUSH", "CHEST_PRESS", "DUMBBELL_PRESS", "DIP") ->
                StrengthPerformanceRegistry.BENCH_PRESS.value
            text.containsAny("VERTICAL_PULL", "PULL_UP", "CHIN_UP", "LAT_PULLDOWN") ->
                StrengthPerformanceRegistry.WEIGHTED_PULL_UP.value
            else -> null
        }
    }

    private fun reportRows(): List<Map<String, String>> {
        val lines = repoFile("docs/audits/strength_metadata_proxy_fallback_impact_v0.5.0.37.csv")
            .readLines().filter(String::isNotBlank)
        val header = lines.first().split(',')
        return lines.drop(1).map { header.zip(it.split(',')).toMap() }
    }

    private fun String.containsAny(vararg tokens: String): Boolean = tokens.any(::contains)

    private fun repoFile(path: String): File {
        val current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(current) { directory -> directory.parentFile?.takeUnless { it == directory } }
            .first { directory -> File(directory, "settings.gradle.kts").isFile }
        return File(root, path)
    }
}
