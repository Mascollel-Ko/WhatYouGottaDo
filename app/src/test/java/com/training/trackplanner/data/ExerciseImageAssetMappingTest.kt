package com.training.trackplanner.data

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExerciseImageAssetMappingTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = CanonicalExerciseMetadataRepository(context)

    @Test
    fun presentationMappingsCoverEveryPreviouslyImagedExerciseAndDecode() {
        val exercises = repository.exercises(includeHistory = true)
        val images = exercises.associate { exercise ->
            exercise.stableKey to ExerciseImageAssetMapping.resolve(
                context,
                exercise.stableKey,
                exercise.imageAssetName
            )
        }
        val mapped = images.filterValues(String::isNotBlank)

        assertEquals(253, mapped.size)
        assertTrue(images.filterValues(String::isBlank).isEmpty())
        mapped.forEach { (stableKey, imageAssetName) ->
            val bitmap = context.assets.open(imageAssetName).use(BitmapFactory::decodeStream)
            assertNotNull("Image must decode for $stableKey: $imageAssetName", bitmap)
            assertTrue("Image is oversized for $stableKey", bitmap!!.width <= 384 && bitmap.height <= 384)
            bitmap.recycle()
        }
    }

    @Test
    fun approvedCollisionResolutionAndFallbacksAreStable() {
        val images = repository.exercises(includeHistory = true).associate { exercise ->
            exercise.stableKey to ExerciseImageAssetMapping.resolve(
                context,
                exercise.stableKey,
                exercise.imageAssetName
            )
        }

        assertEquals("exercise_images/stable_key/ex_f6703b06.png", images.getValue("ex_f6703b06"))
        assertEquals("exercise_images/stable_key/machine_chest_fly.png", images.getValue("machine_chest_fly"))
        assertEquals("exercise_images/stable_key/ex_e159d15a.png", images.getValue("ex_e159d15a"))
        assertEquals("exercise_images/stable_key/ex_d9084b5e.png", images.getValue("ex_d9084b5e"))
        assertEquals("exercise_images/stable_key/ex_281347da.png", images.getValue("ex_281347da"))
        assertEquals("exercise_images/stable_key/dumbbell_single_leg_rdl.png", images.getValue("dumbbell_single_leg_rdl"))
        assertEquals("exercise_images/stable_key/kettlebell_single_leg_rdl.png", images.getValue("kettlebell_single_leg_rdl"))
    }

    @Test
    fun detailImageUsesFitSoFullBodyAndEquipmentRemainVisible() {
        val source = sequenceOf(
            File("src/main/java/com/training/trackplanner/CommonUi.kt"),
            File("app/src/main/java/com/training/trackplanner/CommonUi.kt")
        ).first(File::exists).readText(Charsets.UTF_8)

        assertTrue(source.contains("contentScale = ContentScale.Fit"))
    }
}
