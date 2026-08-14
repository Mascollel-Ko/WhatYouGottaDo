package com.training.trackplanner.data

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.analysis.core.CoreDirectTarget
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CanonicalAnalysisAuthorityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = CanonicalExerciseMetadataRepository(context)

    @Test
    fun approvedCoreCsvIsExactCanonicalAuthority() {
        val approved = projectFile("docs/metadata_authority/core_training_classification_review_2026-08-13.csv")
        assertEquals(
            "3c819568012cd17726486e7f3e21cac972c95eec1736e8ab038e9edc1c3fa954",
            MessageDigest.getInstance("SHA-256").digest(approved.readBytes()).joinToString("") { "%02x".format(it) }
        )
        val rows = approved.readLines().filter(String::isNotBlank).map(SeedData::parseCsvLine)
        val header = rows.first()
        val values = rows.drop(1).associate { row ->
            val fields = header.mapIndexed { index, name -> name.removePrefix("\uFEFF") to row[index] }.toMap()
            fields.getValue("stableKey") to (fields.getValue("coreClass") to fields.getValue("directTarget"))
        }
        val profiles = repository.coreCatalog().selectableProfiles()

        assertEquals(241, profiles.size)
        assertEquals(values.keys, profiles.mapTo(mutableSetOf()) { it.exerciseStableKey })
        profiles.forEach { profile ->
            val approvedValue = values.getValue(profile.exerciseStableKey)
            assertEquals(approvedValue.first, profile.coreClass.name)
            assertEquals(approvedValue.second, profile.directTarget?.name.orEmpty())
        }
    }

    @Test
    fun coreClassAndDirectTargetCountsMatchApprovedReview() {
        val profiles = repository.coreCatalog().selectableProfiles()

        assertEquals(mapOf(CoreClass.DIRECT to 31, CoreClass.HIDDEN_HIGH to 45, CoreClass.HIDDEN_MODERATE to 82, CoreClass.HIDDEN_LOW to 55, CoreClass.NONE to 28), profiles.groupingBy { it.coreClass }.eachCount())
        assertEquals(mapOf(CoreDirectTarget.ROTATION_GENERATION to 15, CoreDirectTarget.BRACING to 8, CoreDirectTarget.ANTI_ROTATION to 4, CoreDirectTarget.TRUNK_FLEXION to 3, CoreDirectTarget.TRUNK_EXTENSION to 1), profiles.mapNotNull { it.directTarget }.groupingBy { it }.eachCount())
        assertTrue(profiles.all { (it.coreClass == CoreClass.DIRECT) == (it.directTarget != null) })
    }

    @Test
    fun badmintonObjectiveAuthorityIsExplicitNineAxisAndObjectiveSpecific() {
        val relations = repository.badmintonObjectiveCatalog().allRelations()

        assertEquals(280, relations.size)
        assertEquals(BadmintonObjective.entries.toSet(), relations.mapTo(mutableSetOf()) { it.objective })
        assertFalse(relations.any { it.objective.name == "ROTATION_POWER" })
        val userApproved = relations.filter {
            it.provenance == "USER_APPROVED_BADMINTON_OBJECTIVE_2026_08_14"
        }
        assertEquals(2, userApproved.size)
        assertTrue(userApproved.all { it.evidenceRelationKeys.isEmpty() && it.reviewReason.isNotBlank() })
        assertTrue(
            relations.filterNot { it in userApproved }.all { it.evidenceRelationKeys.isNotEmpty() }
        )
        assertTrue(relations.any { it.exerciseStableKey == "landmine_anti_rotation" && it.objective == BadmintonObjective.ANTI_ROTATION })
        listOf("band_pallof_press", "cable_pallof_press").forEach { stableKey ->
            val objectives = relations.filter { it.exerciseStableKey == stableKey }.mapTo(mutableSetOf()) { it.objective }
            assertTrue(BadmintonObjective.ANTI_ROTATION in objectives)
            assertTrue(BadmintonObjective.DECELERATION in objectives)
            assertTrue(BadmintonObjective.FOOTWORK in objectives)
        }
        val copenhagen = relations.filter { it.exerciseStableKey == "ex_a8385c4a" }.mapTo(mutableSetOf()) { it.objective }
        assertEquals(setOf(BadmintonObjective.DECELERATION, BadmintonObjective.FOOTWORK), copenhagen)
    }

    @Test
    fun historyOnlyIdentityResolvesThroughSourceWithoutIdentityRewrite() {
        val history = repository.identities().first { identity ->
            identity.historyOnly && repository.coreCatalog().resolve(identity.stableKey) != null
        }

        assertNotNull(repository.coreCatalog().resolve(history.stableKey))
        assertEquals(
            repository.badmintonObjectiveCatalog().relations(history.sourceStableKey),
            repository.badmintonObjectiveCatalog().relations(history.stableKey)
        )
        assertTrue(history.historyOnly)
    }

    @Test
    fun productionAnalysisHasNoLegacyCoreOrObjectiveFallback() {
        val engine = projectFile(
            "app/src/main/java/com/training/trackplanner/analysis/trends/PerformanceTrendEngine.kt"
        ).readText()
        val badminton = projectFile(
            "app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingLoadIndexCalculator.kt"
        ).readText()
        val models = projectFile(
            "app/src/main/java/com/training/trackplanner/analysis/trends/PerformanceTrendModels.kt"
        ).readText()

        assertTrue(engine.contains("CoreStimulusCalculator(canonicalCoreCatalog)"))
        assertTrue(badminton.contains("BadmintonObjectiveStimulusCalculator"))
        assertFalse(badminton.contains("BadmintonTransferMetadataMapper"))
        assertFalse(badminton.contains("CoreDirectTarget"))
        assertFalse(models.contains("methodRaw"))
    }

    private fun projectFile(path: String): File = sequenceOf(File(path), File("../$path"))
        .firstOrNull(File::isFile)
        ?: error("Project test file not found: $path")
}
