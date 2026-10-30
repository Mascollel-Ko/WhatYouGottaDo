package com.training.trackplanner.data

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.features.MetadataReadinessReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CanonicalMuscleEditorAndValidationTest {
    private val repo = CanonicalExerciseMetadataRepository(
        ApplicationProvider.getApplicationContext<android.content.Context>()
    )
    private val roles = repo.muscleRoleResolver()

    @Test
    fun builtInNoLongerRequiresOldPrimaryFieldForSanity() {
        val squat = repo.exercises().first { it.stableKey == "barbell_back_squat" }
            .copy(primaryMuscles = "", secondaryMuscles = "")
        val findings = MetadataSanityChecker.check(squat, roles).issues
        assertFalse(findings.any { it.field == "primaryMuscles" })
        assertFalse(findings.any { it.field == "muscleRelations" })
    }

    @Test
    fun builtInMissingCanonicalKeyIsReportedNotGuessedFromName() {
        val fake = Exercise(
            stableKey = "unknown_foo", name = "스쿼트", category = "근력운동",
            primaryMuscles = "QUADRICEPS"
        )
        val issues = MetadataSanityChecker.check(fake, roles).issues
        assertTrue(issues.any { it.field == "muscleRelations" })
    }

    @Test
    fun readinessUsesCanonicalRelationsEvenWhenOldColumnBlank() {
        val bench = repo.exercises().first { it.stableKey == "barbell_bench_press" }
            .copy(primaryMuscles = "")
        val report = MetadataReadinessReporter.generate(listOf(bench), roles)
        assertFalse("primaryMuscles" in report.rows.single().missingFields)
        assertFalse("muscleRelations" in report.rows.single().missingFields)
    }

    @Test
    fun importingCustomExerciseWithNoTypedRolesIsFlaggedForReviewNotByOldFields() {
        val custom = Exercise(
            stableKey = "user_ex_1", name = "직접 만든 운동", category = "근력운동",
            isCustom = true, primaryMuscles = ""
        )
        assertFalse(MetadataSanityChecker.check(custom, roles).issues.any { it.field == "primaryMuscles" })
        assertEquals(CanonicalMuscleLookupKind.USER_CUSTOM_UNMAPPED, roles.lookup(custom).kind)
    }

    @Test
    fun copyingIntoBuiltInDoesNotOverwriteItsMuscleFields() {
        val builtIn = repo.exercises().first { it.stableKey == "barbell_bench_press" }
        val other = Exercise(
            stableKey = "user_ex_2", name = "custom",
            category = "근력운동", primaryMuscles = "BICEPS",
            secondaryMuscles = "FOREARM"
        )
        val result = builtIn.copyEditableMetadataFrom(other)
        assertEquals(builtIn.stableKey, result.stableKey)
        assertEquals(builtIn.primaryMuscles, result.primaryMuscles)
        assertEquals(builtIn.secondaryMuscles, result.secondaryMuscles)
    }

    @Test
    fun copyingIntoCustomNeverCopiesOldMuscleFields() {
        val blank = Exercise(stableKey = "", name = "new", category = "근력운동", isCustom = true)
        val source = Exercise(stableKey = "barbell_back_squat", name = "squat", category = "근력운동",
            primaryMuscles = "QUADRICEPS", secondaryMuscles = "GLUTE"
        )
        val copied = blank.copyEditableMetadataFrom(source)
        assertEquals("", copied.primaryMuscles)
        assertEquals("", copied.secondaryMuscles)
    }
}
