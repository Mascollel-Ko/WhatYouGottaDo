package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuntimeExerciseMetadataAssetLoaderTest {
    @Test
    fun canonicalPass31AssetHasExpectedRowsAndDecisions() {
        val asset = sequenceOf(
            File("src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}"),
            File("app/src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}")
        ).firstOrNull(File::isFile) ?: error("Canonical metadata test asset not found.")

        val rows = RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(asset.readText(Charsets.UTF_8))

        assertEquals(215, rows.size)
        assertEquals(215, rows.map { it.stableKey.lowercase() }.distinct().size)
        assertFalse(rows.any { it.stableKey.isBlank() })
        assertEquals(18, rows.count { it.badmintonTransferLevel == "DIRECT" })
        assertEquals(75, rows.count { it.badmintonTransferLevel == "SUPPORTIVE" })
        assertEquals(93, rows.count { it.badmintonTransferLevel == "GENERAL" })
        assertEquals(29, rows.count { it.badmintonTransferLevel == "NONE" })
        assertEquals(36, rows.count { it.stressMagnitudeHint == "HIGH" })
        assertTrue(rows.all { it.neuromuscularStressLevel.isNotBlank() })
        assertTrue(rows.all { it.systemicMuscularStressLevel.isNotBlank() })
        assertTrue(rows.all { it.localMuscularStressLevel.isNotBlank() })
        assertTrue(rows.all { it.jointTendonImpactStressLevel.isNotBlank() })
        assertTrue(rows.all { it.movementFocusDemandLevel.isNotBlank() })
        assertTrue(rows.all { it.recoveryDurationClass.isNotBlank() })

        val byName = rows.associateBy { it.exerciseName }
        assertEquals("GENERAL", byName.getValue("슈퍼맨").badmintonTransferLevel)
        assertEquals("LOW", byName.getValue("레그 익스텐션").stressMagnitudeHint)
        assertEquals("LOW", byName.getValue("레그 컬").stressMagnitudeHint)
        assertEquals("HIGH", byName.getValue("벤치프레스").stressMagnitudeHint)
        assertEquals("HIGH", byName.getValue("딥스").stressMagnitudeHint)
        assertEquals("HIGH", byName.getValue("벤치 딥스").stressMagnitudeHint)
        assertEquals("SUPPORTIVE", byName.getValue("케이블 원암 로우").badmintonTransferLevel)
    }
}
