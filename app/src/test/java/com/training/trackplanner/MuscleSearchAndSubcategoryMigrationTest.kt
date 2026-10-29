package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.EffectiveMuscleMetadataResolver
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseMetadataFieldScope
import com.training.trackplanner.data.ExerciseMetadataOverrideSource
import com.training.trackplanner.data.ExerciseMetadataUserOverrideEntity
import com.training.trackplanner.data.ExerciseMetadataValueEncoding
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
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
class MuscleSearchAndSubcategoryMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val canonical = CanonicalExerciseMetadataRepository(context)
    private val resolver = EffectiveMuscleMetadataResolver(canonical.muscleRoleResolver())
    private val presenter = CanonicalMusclePresentation.from(context)
    private val translator = MetadataTranslator.from(context)

    private fun exercise(key: String) =
        canonical.exercises().first { it.stableKey == key }

    @Test
    fun builtInSearchUsesStableKeyNotLegacyExerciseFieldsEvenWithOverride() {
        val press = exercise("barbell_bench_press").copy(
            primaryMuscles = "BICEPS", secondaryMuscles = "FOREARM"
        )
        val override = ExerciseMetadataUserOverrideEntity(
            stableKey = press.stableKey,
            fieldScope = ExerciseMetadataFieldScope.EXERCISE.name,
            fieldKey = "exercise.primaryMuscles",
            valueEncoding = ExerciseMetadataValueEncoding.TOKEN_SET.name,
            value = "BICEPS",
            isExplicitEmpty = false,
            source = ExerciseMetadataOverrideSource.USER_EDIT.name,
            semanticCanonicalRevisionAtEdit = "test"
        )
        val effective = resolver.resolve(press, listOf(override))
        assertFalse(effective.usesCanonicalRoles) // MUS-00.5's interim editor projection.
        assertTrue(press.matchesMetadataQuery("CHEST", translator, effective, presenter))
        assertFalse(press.matchesMetadataQuery("BICEPS", translator, effective, presenter))
        assertFalse(press.matchesMetadataQuery("FOREARM", translator, effective, presenter))
    }

    @Test
    fun muscleSearchUnderstandsKoreanEnglishAndCanonicalCode() {
        val press = exercise("barbell_bench_press")
        val effective = resolver.resolve(press, emptyList())
        val koreanContext = localized(Locale.KOREAN)
        val englishContext = localized(Locale.ENGLISH)
        val ko = CanonicalMusclePresentation.from(koreanContext)
        val en = CanonicalMusclePresentation.from(englishContext)
        val kt = MetadataTranslator.from(koreanContext)
        val et = MetadataTranslator.from(englishContext)

        assertTrue(press.matchesMetadataQuery("가슴", kt, effective, ko))
        assertTrue(press.matchesMetadataQuery("Chest", et, effective, en))
        assertTrue(press.matchesMetadataQuery("CHEST", kt, effective, ko))
        assertTrue(press.matchesMetadataQuery("TRICEPS", kt, effective, ko)) // SECONDARY also searchable
    }

    @Test
    fun stabilizationIsSearchableButUnsupportedMuscleIsNot() {
        val deadlift = exercise("barbell_deadlift")
        val effective = resolver.resolve(deadlift, emptyList())
        assertTrue(deadlift.matchesMetadataQuery("BACK_LATS", translator, effective, presenter))
        val reverseCurl = exercise("barbell_reverse_curl")
        val reverse = resolver.resolve(reverseCurl, emptyList())
        assertFalse(reverseCurl.matchesMetadataQuery("BACK_LATS", translator, reverse, presenter))
    }

    @Test
    fun canonicalPrimaryDeterminesStrengthTargetsNotStabilizersOrSecondaries() {
        val squat = exercise("barbell_back_squat").copy(
            primaryMuscles = "CORE", secondaryMuscles = "BACK_LATS"
        )
        val squatRole = resolver.resolve(squat, emptyList())
        val squatCategories = ExerciseSubcategoryMapper.categoriesFor(squat, null, squatRole)
        assertTrue(ExerciseSubcategory.STRENGTH_SQUAT_QUAD in squatCategories)
        assertFalse(ExerciseSubcategory.STRENGTH_CORE in squatCategories)
        assertFalse(ExerciseSubcategory.STRENGTH_BACK_PULL in squatCategories)

        val deadlift = exercise("barbell_deadlift")
        val deadliftCategories = ExerciseSubcategoryMapper.categoriesFor(
            deadlift, null, resolver.resolve(deadlift, emptyList())
        )
        assertTrue(ExerciseSubcategory.STRENGTH_HINGE_POSTERIOR in deadliftCategories)
        assertFalse(ExerciseSubcategory.STRENGTH_BACK_PULL in deadliftCategories)
        assertFalse(ExerciseSubcategory.STRENGTH_CORE in deadliftCategories)

        val press = exercise("barbell_bench_press")
        val benchCategories = ExerciseSubcategoryMapper.categoriesFor(
            press, null, resolver.resolve(press, emptyList())
        )
        assertTrue(ExerciseSubcategory.STRENGTH_CHEST_PUSH in benchCategories)
        assertFalse(ExerciseSubcategory.STRENGTH_SHOULDERS in benchCategories)
        assertFalse(ExerciseSubcategory.STRENGTH_ARMS in benchCategories)
    }

    @Test
    fun searchFilteredKeysAlsoDriveSubcategoryCounts() {
        val squat = exercise("barbell_back_squat")
        val bench = exercise("barbell_bench_press")
        val rows = listOf(squat, bench)
        val effective = rows.associate { it.stableKey to resolver.resolve(it, emptyList()) }
        val searched = rows.filter { row ->
            row.matchesMetadataQuery("가슴", translator, effective[row.stableKey], presenter)
        }
        val count = ExerciseSubcategoryMapper.countsFor(
            searched, "근력운동", emptyMap(), effective
        )
        assertEquals(listOf("barbell_bench_press"), searched.map { it.stableKey })
        assertEquals(1, count[ExerciseSubcategory.STRENGTH_CHEST_PUSH])
        assertEquals(0, count[ExerciseSubcategory.STRENGTH_SQUAT_QUAD])
    }

    @Test
    fun customExerciseKeepsExplicitLegacyCompatibilityUntilImport() {
        val custom = Exercise(
            stableKey = "user_custom_row",
            name = "내가 만든 운동",
            category = "근력운동",
            movementPattern = "HORIZONTAL_PULL",
            isCustom = true,
            primaryMuscles = "LAT",
            secondaryMuscles = "BICEPS"
        )
        val effective = resolver.resolve(custom, emptyList())
        assertTrue(custom.matchesMetadataQuery("광배근", translator, effective, presenter))
        assertTrue(custom.matchesMetadataQuery("BICEPS", translator, effective, presenter))
        assertTrue(ExerciseSubcategory.STRENGTH_BACK_PULL in ExerciseSubcategoryMapper.categoriesFor(
            custom, RuntimeExerciseMetadataDefaults.forExercise(custom), effective
        ))
    }

    @Test
    fun blankOrUnmappedMuscleMetadataDoesNotInventCanonicalTargets() {
        val unknown = Exercise(stableKey = "unknown_user_exercise", name = "테스트", category = "근력운동")
        val effective = resolver.resolve(unknown, emptyList())
        assertFalse(unknown.matchesMetadataQuery("가슴", translator, effective, presenter))
        assertEquals(
            setOf(ExerciseSubcategory.STRENGTH_OTHER),
            ExerciseSubcategoryMapper.categoriesFor(unknown, null, effective)
        )
    }

    private fun localized(locale: Locale): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(locale)
        })
}
