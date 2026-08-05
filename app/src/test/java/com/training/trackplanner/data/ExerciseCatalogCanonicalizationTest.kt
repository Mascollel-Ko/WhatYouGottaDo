package com.training.trackplanner.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExerciseCatalogCanonicalizationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun activeBuiltInCatalogUsesExplicitUniqueCanonicalIdentity() {
        val exercises = SeedData.exercises(context)
        val keys = exercises.map(Exercise::stableKey)
        val names = exercises.map(Exercise::name)

        assertEquals(241, exercises.size)
        assertTrue(keys.all(String::isNotBlank))
        assertEquals(keys.size, keys.distinct().size)
        assertEquals(names.size, names.distinct().size)
        assertTrue(names.none { it.startsWith("CSV 복원 ") })
        assertTrue(names.none { name ->
            listOf("싱글 레그", "싱글레그", "한쪽 레그", "싱글 암", "싱글암", "한팔").any(name::contains)
        })
    }

    @Test
    fun mergeDeleteAndSplitDecisionsAreReflectedInFinalCatalog() {
        val byKey = SeedData.exercises(context).associateBy(Exercise::stableKey)
        val removed = setOf(
            "ex_201f6426",
            "ex_885b629",
            "imported_싱글_레그_rdl",
            "landmine_rainbow",
            "ex_e3487166",
            "imported_csv_복원_계획",
            "imported_csv_복원_근력운동",
            "imported_csv_복원_기능성운동",
            "imported_csv_복원_스포츠",
            "single_leg_rdl"
        )
        val required = setOf(
            "dumbbell_single_leg_rdl",
            "kettlebell_single_leg_rdl",
            "barbell_romanian_deadlift",
            "dumbbell_romanian_deadlift",
            "half_kneeling_single_arm_dumbbell_press",
            "half_kneeling_single_arm_kettlebell_press",
            "lateral_bound_continuous",
            "ex_34e7d21",
            "ex_eb636bac",
            "landmine_rotation"
        )

        assertTrue(required.all(byKey::containsKey))
        assertTrue(removed.none(byKey::containsKey))
        assertFalse(byKey.getValue("lateral_bound_continuous").name.contains("스틱"))
        assertTrue(byKey.getValue("ex_34e7d21").name.contains("스틱"))
    }
}
