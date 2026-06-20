package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

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
        assertTrue(rows.all { it.appCueProfile in RuntimeExerciseMetadataAssetLoader.APP_CUE_PROFILES })

        val randomBeepCueNames = setOf(
            "랜덤 비프 풋워크",
            "스플릿 스텝 리액션",
            "랜덤 방향전환 드릴",
            "랜덤 풋워크",
            "6코너 섀도우 풋워크",
            "앞뒤 랜덤 콕줍기",
            "좌우 랜덤 콕줍기",
            "6방향 랜덤 콕줍기"
        )
        assertEquals(
            randomBeepCueNames,
            rows.filter { it.appCueProfile == "RANDOM_BEEP_CUE" }
                .map { it.exerciseName }
                .toSet()
        )
        assertTrue(rows.filterNot { it.exerciseName in randomBeepCueNames }
            .all { it.appCueProfile == "NONE" })

        val byName = rows.associateBy { it.exerciseName }
        assertEquals("GENERAL", byName.getValue("슈퍼맨").badmintonTransferLevel)
        assertEquals("LOW", byName.getValue("레그 익스텐션").stressMagnitudeHint)
        assertEquals("LOW", byName.getValue("레그 컬").stressMagnitudeHint)
        assertEquals("HIGH", byName.getValue("벤치프레스").stressMagnitudeHint)
        assertEquals("HIGH", byName.getValue("딥스").stressMagnitudeHint)
        assertEquals("HIGH", byName.getValue("벤치 딥스").stressMagnitudeHint)
        assertEquals("SUPPORTIVE", byName.getValue("케이블 원암 로우").badmintonTransferLevel)
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
