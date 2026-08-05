package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class RuntimeExerciseMetadataAssetLoaderTest {
    @Test
    fun canonicalAuthorityAssetHasExpectedRowsAndScopes() {
        val asset = sequenceOf(
            File("src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}"),
            File("app/src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}")
        ).firstOrNull(File::isFile) ?: error("Canonical metadata test asset not found.")

        val rows = RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(asset.readText(Charsets.UTF_8))

        assertEquals(257, rows.size)
        assertEquals(257, rows.map { it.stableKey.lowercase() }.distinct().size)
        assertFalse(rows.any { it.stableKey.isBlank() })
        assertEquals(18, rows.count { it.badmintonTransferLevel == "DIRECT" })
        assertEquals(101, rows.count { it.badmintonTransferLevel == "SUPPORTIVE" })
        assertEquals(105, rows.count { it.badmintonTransferLevel == "GENERAL" })
        assertEquals(33, rows.count { it.badmintonTransferLevel == "NONE" })
        assertEquals(47, rows.count { it.stressMagnitudeHint == "HIGH" })
        assertTrue(rows.all { it.neuromuscularStressLevel.isNotBlank() })
        assertTrue(rows.all { it.systemicMuscularStressLevel.isNotBlank() })
        assertTrue(rows.all { it.localMuscularStressLevel.isNotBlank() })
        assertTrue(rows.all { it.jointTendonImpactStressLevel.isNotBlank() })
        assertTrue(rows.all { it.movementFocusDemandLevel.isNotBlank() })
        assertTrue(rows.all { it.recoveryDurationClass.isNotBlank() })
        assertTrue(rows.all { it.appCueProfile in RuntimeExerciseMetadataAssetLoader.APP_CUE_PROFILES })

        val randomBeepCueKeys = setOf(
            "ex_1c7f2342", "ex_33841b88", "ex_421ba24b", "ex_4255e429",
            "ex_64422511", "ex_8e69fc74", "ex_bc84eb7f", "ex_c5f4c242"
        )
        assertEquals(randomBeepCueKeys, rows.filter { it.appCueProfile == "RANDOM_BEEP_CUE" }.map { it.stableKey }.toSet())
        assertTrue(rows.filterNot { it.stableKey in randomBeepCueKeys }.all { it.appCueProfile == "NONE" })
        assertEquals(16, rows.count { it.planningEligibility == "HISTORY_ONLY" })
        assertEquals("HISTORY_ONLY", rows.single { it.stableKey == "single_leg_rdl" }.planningEligibility)
    }

    @Test
    fun missingAppCueProfileColumnDefaultsToNone() {
        val rows = RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(
            "stableKey,exerciseName\nlegacy_key,Legacy exercise"
        )

        assertEquals("NONE", rows.single().appCueProfile)
    }

    @Test
    fun canonicalHashValidationIsLineEndingSafe() {
        val lf = "first,second\n1,2\n"
        val crlfHash = MessageDigest.getInstance("SHA-256")
            .digest(lf.replace("\n", "\r\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        assertTrue(RuntimeExerciseMetadataAssetLoader.assetHashMatches(lf, crlfHash))
    }
}
