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
    private val canonicalKeys by lazy { SeedData.exercises(context).mapTo(mutableSetOf(), Exercise::stableKey) }

    @Test
    fun everyReviewedLegacyMappingHasItsDeclaredDeterministicOutcome() {
        val mappings = mapper.mappings()

        assertEquals(33, mappings.size)
        mappings.forEach { mapping ->
            val equipment = when (mapping.oldStableKey) {
                "ex_d2bb7946" -> "BARBELL"
                "ex_8380d7fe", "ex_8e1b313e", "ex_66e8c8c2" -> "DUMBBELL"
                else -> ""
            }
            val result = resolve(mapping.oldStableKey, mapping.oldName, equipment)
            when (mapping.importRule) {
                "DIRECT", "CANONICAL", "CANONICAL_RENAME" ->
                    assertEquals(mapping.canonicalStableKey, (result as LegacyExerciseResolution.Resolved).canonicalStableKey)
                "REQUIRE_EQUIPMENT_DISAMBIGUATION" ->
                    assertTrue(result is LegacyExerciseResolution.Resolved)
                "DROP_PLACEHOLDER_WITH_WARNING" ->
                    assertTrue(result is LegacyExerciseResolution.Dropped)
                "MANUAL_RESOLUTION_REQUIRED" ->
                    assertTrue(result is LegacyExerciseResolution.Rejected)
                else -> error("Uncovered legacy import rule: ${mapping.importRule}")
            }
        }
    }

    @Test
    fun exactApprovedNameWorksOnlyWhenKeyIsBlank() {
        val mapping = mapper.mappings().first { it.importRule == "DIRECT" }
        val resolved = resolve("", mapping.oldName, "")
        val unknownKey = resolve("unknown_old_key", mapping.oldName, "")

        assertEquals(mapping.canonicalStableKey, (resolved as LegacyExerciseResolution.Resolved).canonicalStableKey)
        assertTrue(unknownKey is LegacyExerciseResolution.Rejected)
    }

    @Test
    fun ambiguousSplitsAndUnknownRowsFailWithoutGuessing() {
        val rdl = resolve("ex_d2bb7946", "루마니안 데드리프트", "")
        val press = resolve("ex_8380d7fe", "원암 하프 닐링 프레스", "")
        val unknown = resolve("missing_key", "알 수 없는 운동", "")

        assertEquals(
            DataTransferDiagnosticCodes.AMBIGUOUS_LEGACY_EXERCISE_SPLIT,
            (rdl as LegacyExerciseResolution.Rejected).diagnostic.code
        )
        assertEquals(
            DataTransferDiagnosticCodes.AMBIGUOUS_LEGACY_EXERCISE_SPLIT,
            (press as LegacyExerciseResolution.Rejected).diagnostic.code
        )
        assertTrue(unknown is LegacyExerciseResolution.Rejected)
    }

    private fun resolve(key: String, name: String, equipment: String): LegacyExerciseResolution =
        mapper.resolve(
            oldStableKey = key,
            oldName = name,
            equipment = equipment,
            canonicalStableKeys = canonicalKeys,
            stage = DataTransferStages.PLANNING,
            entityType = "test"
        )
}
