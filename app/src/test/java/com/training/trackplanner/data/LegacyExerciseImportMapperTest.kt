package com.training.trackplanner.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyExerciseImportMapperTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mapper by lazy { LegacyExerciseImportMapper.fromAssets(context) }
    private val canonicalKeys by lazy { SeedData.exactExerciseMetadataByStableKey(context).keys }

    @Test
    fun everyReviewedLegacyMappingHasItsDeclaredDeterministicOutcome() {
        val mappings = mapper.mappings()

        assertEquals(37, mappings.size)
        mappings.forEach { mapping ->
            val result = resolve(mapping.oldStableKey, mapping.oldName)
            when (mapping.importRule) {
                "DIRECT", "CANONICAL", "CANONICAL_RENAME" ->
                    assertEquals(mapping.canonicalStableKey, (result as LegacyExerciseResolution.Resolved).canonicalStableKey)
                "DROP_PLACEHOLDER_WITH_WARNING", "DROP_DELETED_EXERCISE_WITH_WARNING" ->
                    assertTrue(result is LegacyExerciseResolution.Dropped)
                else -> error("Uncovered legacy import rule: ${mapping.importRule}")
            }
        }
    }

    @Test
    fun approvedNameNeverGrantsCanonicalIdentityWithoutItsReviewedKey() {
        val mapping = mapper.mappings().first { it.importRule == "DIRECT" }
        val blankKey = resolve("", mapping.oldName)
        val unknownKey = resolve("unknown_old_key", mapping.oldName)

        assertTrue(blankKey is LegacyExerciseResolution.Rejected)
        assertTrue(unknownKey is LegacyExerciseResolution.Rejected)
    }

    @Test
    fun reviewedGenericKeysResolveDirectlyWithoutEquipmentOrAmbiguity() {
        val expected = mapOf(
            "ex_d2bb7946" to "barbell_romanian_deadlift",
            "ex_8380d7fe" to "half_kneeling_single_arm_dumbbell_press",
            "ex_8e1b313e" to "half_kneeling_single_arm_dumbbell_press",
            "ex_66e8c8c2" to "half_kneeling_single_arm_dumbbell_press"
        )

        expected.forEach { (source, target) ->
            val name = mapper.mappings().single { it.oldStableKey == source }.oldName
            val result = resolve(source, name)

            assertEquals(target, (result as LegacyExerciseResolution.Resolved).canonicalStableKey)
            assertEquals("LEGACY_DIRECT", result.method)
            assertTrue(result.canonicalName.isNotBlank())
        }
    }

    @Test
    fun deletedGenericOneArmRowExerciseDefinitionIsDroppedWithWarning() {
        val result = resolve("ex_e3487166", "원암 로우")
        val diagnostic = (result as LegacyExerciseResolution.Dropped).diagnostic

        assertEquals(DataTransferDiagnosticCodes.LEGACY_DELETED_EXERCISE, diagnostic.code)
        assertEquals("DROP_DELETED_EXERCISE_WITH_WARNING", diagnostic.resolutionMethod)
    }

    @Test
    fun unknownRowsStillFailWithoutGuessing() {
        val unknown = resolve("missing_key", "알 수 없는 운동")
        assertTrue(unknown is LegacyExerciseResolution.Rejected)
    }

    private fun resolve(key: String, name: String): LegacyExerciseResolution =
        mapper.resolve(
            oldStableKey = key,
            oldName = name,
            equipment = "",
            canonicalStableKeys = canonicalKeys,
            stage = DataTransferStages.PLANNING,
            entityType = "Exercise"
        )
}
