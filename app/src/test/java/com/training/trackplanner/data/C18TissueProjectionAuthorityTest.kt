package com.training.trackplanner.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.training.trackplanner.analysis.features.BodyweightLoadProfileAuthority
import com.training.trackplanner.analysis.tissue.TissueRcvAssetRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class C18TissueProjectionAuthorityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exactKeys = listOf(
        "ex_28347c1f",
        "barbell_romanian_deadlift",
        "dumbbell_chest_supported_row",
        "barbell_reverse_curl",
        "dumbbell_lying_triceps_extension"
    )

    @Test fun allFiveHaveExactCanonicalIdentityRuntimeProtocolAndTissueRelations() {
        val metadata = CanonicalExerciseMetadataRepository(context)
        val runtime = metadata.runtimeMetadataCatalog()
        val tissue = TissueRcvAssetRepository.fromAssets(context).catalog
        exactKeys.forEach { key ->
            assertNotNull("identity $key", metadata.identity(key))
            assertEquals("$key exact runtime key", key, runtime.resolveByStableKey(key)?.stableKey)
            assertTrue("$key planning selectable", runtime.resolveByStableKey(key)?.planningEligibility == "PROGRAM_SELECTABLE")
            assertTrue("$key exact tissue index", key in tissue.exerciseStableKeys)
            assertEquals("$key complete reviewed protocol", "COMPLETE", tissue.protocols[key]?.mappingStatus)
            val authority = tissue.authorityRows.filter { it.exerciseStableKey == key }
            assertTrue("$key needs exact authority rows", authority.isNotEmpty())
            assertEquals("$key one exact dose basis", 1, authority.map { it.doseBasis }.distinct().size)
            authority.forEach { row ->
                val unit = tissue.loadUnits[row.loadUnitStableKey]
                assertNotNull("${row.loadUnitStableKey} exact load unit for $key", unit)
                assertTrue("${row.loadUnitStableKey} exact joint relation for $key", unit!!.jointComplexStableKey in tissue.jointComplexes)
                assertTrue("${row.loadUnitStableKey} recovery route for $key", unit.recoveryClass in tissue.routing)
            }
        }
    }

    @Test fun resolvedNegativeControlKeysUseTheSameExactAuthorityPath() {
        val metadata = CanonicalExerciseMetadataRepository(context)
        val tissue = metadata.tissueRepository().catalog
        listOf("barbell_back_squat", "cable_rear_delt_fly", "ex_5ca7133f").forEach { key ->
            assertNotNull(metadata.identity(key))
            assertNotNull(metadata.runtimeMetadataCatalog().resolveByStableKey(key))
            assertEquals("COMPLETE", tissue.protocols[key]?.mappingStatus)
            assertTrue(tissue.authorityRows.any { it.exerciseStableKey == key })
        }
    }

    @Test fun optionalDoseProfilesAreNotMistakenForMissingCanonicalTissueRelations() {
        val tissue = TissueRcvAssetRepository.fromAssets(context).catalog
        exactKeys.forEach { key ->
            assertTrue(tissue.authorityRows.any { it.exerciseStableKey == key })
            assertTrue(tissue.protocols.containsKey(key))
            if (key !in tissue.exerciseDoseProfiles) {
                val basis = tissue.authorityRows.filter { it.exerciseStableKey == key }.map { it.doseBasis }.distinct().single()
                assertTrue("$key uses supported exact default basis $basis", basis in setOf("WEIGHTED_REPETITION", "BODYWEIGHT_REPETITION"))
            }
        }
    }

    @Test fun birdDogRcvBodyweightCoefficientIsAvailableAsExactRuntimeAuthority() {
        val tissue = TissueRcvAssetRepository.fromAssets(context).catalog
        val rows = tissue.authorityRows.filter { it.exerciseStableKey == "ex_28347c1f" }
        val coefficients = rows.map { it.bodyWeightCoefficient }.distinct()
        assertEquals(listOf(0.25), coefficients)
        assertTrue(rows.all { it.doseBasis == "BODYWEIGHT_REPETITION" })
        assertNull(BodyweightLoadProfileAuthority.resolve("ex_28347c1f"))
    }

    @Test fun unknownStableKeyHasNoExactTissueAuthority() {
        val tissue = TissueRcvAssetRepository.fromAssets(context).catalog
        val key = "fake_unknown_exercise"
        assertFalse(key in tissue.exerciseStableKeys)
        assertFalse(tissue.protocols.containsKey(key))
        assertTrue(tissue.authorityRows.none { it.exerciseStableKey == key })
    }
}
