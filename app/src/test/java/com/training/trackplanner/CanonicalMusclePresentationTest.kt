package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.CanonicalMuscleRole
import com.training.trackplanner.data.EffectiveMuscleMetadataResolver
import com.training.trackplanner.data.ExerciseMetadataFieldScope
import com.training.trackplanner.data.ExerciseMetadataOverrideSource
import com.training.trackplanner.data.ExerciseMetadataUserOverrideEntity
import com.training.trackplanner.data.ExerciseMetadataValueEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CanonicalMusclePresentationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = CanonicalExerciseMetadataRepository(context)
    private val resolver = EffectiveMuscleMetadataResolver(repository.muscleRoleResolver())

    @Test
    fun allCanonicalMuscleCodesHaveRegisteredKoreanAndEnglishGroupLabels() {
        val catalogue = MetadataDisplayCatalogue.from(context)
        val missing = repository.muscleRelations()
            .mapTo(mutableSetOf()) { it.relationValue }
            .filterNot { code ->
                catalogue.hasRegisteredLabel(MetadataDisplayField.MUSCLE_GROUP, code, Locale.KOREAN) &&
                    catalogue.hasRegisteredLabel(MetadataDisplayField.MUSCLE_GROUP, code, Locale.ENGLISH)
            }
        assertTrue("Untranslated canonical muscle codes: $missing", missing.isEmpty())
    }

    @Test
    fun koreanAndEnglishLabelsUseMuscleGroupNotLegacyMuscleVocabulary() {
        val bench = repository.exercises().first { it.stableKey == "barbell_bench_press" }
        val effective = resolver.resolve(bench, emptyList())
        val korean = CanonicalMusclePresentation.from(localized(Locale.KOREAN))
        val english = CanonicalMusclePresentation.from(localized(Locale.ENGLISH))
        val koChest = korean.optionsForRole(effective, CanonicalMuscleRole.PRIMARY).single()
        val enChest = english.optionsForRole(effective, CanonicalMuscleRole.PRIMARY).single()
        assertEquals("가슴 근육", koChest.label)
        assertEquals("Chest", enChest.label)
        assertEquals("CHEST", koChest.code)
        assertTrue(koChest.matches("Chest"))
        assertTrue(koChest.matches("가슴"))
        assertTrue(korean.optionsForRole(effective, CanonicalMuscleRole.STABILIZER)
            .any { it.label == "전면 코어" })
        assertTrue(korean.optionsForRole(effective, CanonicalMuscleRole.NOT_SUPPORTED).isEmpty())
        assertTrue(korean.optionsForRole(effective, CanonicalMuscleRole.REVIEW_REQUIRED).isEmpty())
    }

    @Test
    fun oldMuscleOverrideRequiresReviewButUntilThenDisplaysCanonicalRoles() {
        val bench = repository.exercises().first { it.stableKey == "barbell_bench_press" }
        val row = ExerciseMetadataUserOverrideEntity(
            stableKey = bench.stableKey,
            fieldScope = ExerciseMetadataFieldScope.EXERCISE.name,
            fieldKey = "exercise.primaryMuscles",
            valueEncoding = ExerciseMetadataValueEncoding.TOKEN_SET.name,
            value = "",
            isExplicitEmpty = true,
            source = ExerciseMetadataOverrideSource.USER_EDIT.name,
            semanticCanonicalRevisionAtEdit = "test"
        )
        val effective = resolver.resolve(bench, listOf(row))
        val presenter = CanonicalMusclePresentation.from(localized(Locale.KOREAN))
        assertTrue(effective.usesCanonicalRoles)
        assertTrue(effective.needsMigrationReview)
        assertEquals("CHEST", presenter.optionsForRole(effective, CanonicalMuscleRole.PRIMARY).single().code)
    }

    private fun localized(locale: Locale): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(locale) })
}
