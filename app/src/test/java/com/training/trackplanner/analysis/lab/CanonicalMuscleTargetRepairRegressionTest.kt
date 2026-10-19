package com.training.trackplanner.analysis.lab

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PR #28 targeted corrections preserve other valid proposals instead of rolling them back.
 * A hypertrophy-capable exercise must have an explicit prime mover: "no PRIMARY" is NOT a
 * mechanically credible reclassification for goblet/pistol/box squats, curls or rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CanonicalMuscleTargetRepairRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = CanonicalExerciseMetadataRepository(context)
    private val byKey by lazy { repository.muscleRelations().groupBy { it.exerciseStableKey } }

    @Test
    fun confirmedCorrectionsOnlyAddMissingMoversOrFixKnownWrongCodes() {
        val expected = mapOf(
            "dumbbell_goblet_squat" to setOf("QUADS", "GLUTES"),
            "kettlebell_goblet_squat" to setOf("QUADS", "GLUTES"),
            "ex_85f12271" to setOf("QUADS", "GLUTES"),
            "ex_d67e5761" to setOf("QUADS", "GLUTES"),
            "ex_a611010d" to setOf("QUADS", "HIP_ADDUCTOR"),
            "ex_b69ab702" to setOf("BICEPS"),
            "ex_e159d15a" to setOf("BACK_LATS"),
            "ex_69a56484" to setOf("QUADS"),
            "ex_7404067c" to setOf("GLUTES", "HAMSTRINGS")
        )
        expected.forEach { (key, primary) ->
            val actual = byKey[key].orEmpty().filter { it.relationType == "PRIMARY" }
                .mapTo(mutableSetOf()) { it.relationValue }
            assertEquals("Missing prime mover for $key", primary, actual)
        }
        val cossack = byKey.getValue("ex_a611010d")
        assertFalse(cossack.any { it.relationValue == "ADDUCTOR_ABDUCTOR" })
        assertEquals("SECONDARY", cossack.single { it.relationValue == "GLUTES" }.relationType)
        val hammer = byKey.getValue("ex_b69ab702")
        assertEquals(1.0, hammer.single { it.relationValue == "BICEPS" }.coefficient!!, 0.0)
    }

    @Test
    fun allDirectHypertrophyExerciseIdentitiesHaveReviewedAnatomicalPrimeMovers() {
        val hypertrophyKeys = repository.physicalQualityCatalog().allRelations()
            .filter {
                it.qualityId == TrainableQuality.HYPERTROPHY &&
                    it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
            }.mapTo(mutableSetOf()) { it.exerciseStableKey }
        assertTrue(hypertrophyKeys.isNotEmpty())
        val missing = hypertrophyKeys.filter { stableKey ->
            byKey[stableKey].orEmpty().none { it.relationType == "PRIMARY" }
        }
        assertEquals("Never delete the last prime mover from a hypertrophy exercise", emptyList<String>(), missing)
    }

    @Test
    fun retainValidLatAndTrunkStabilizerCorrectionsFromThePriorAudit() {
        val triceps = byKey.getValue("ex_eb636bac")
        assertEquals("PRIMARY", triceps.single { it.relationValue == "TRICEPS" }.relationType)
        assertTrue(triceps.none {
            it.relationValue == "BACK_LATS" && it.relationType == "PRIMARY"
        })
        val shoulders = byKey.getValue("ex_bb4b4276")
        assertEquals("PRIMARY", shoulders.single { it.relationValue == "SHOULDERS" }.relationType)
        assertTrue(shoulders.none {
            it.relationValue == "BACK_LATS" && it.relationType == "PRIMARY"
        })
        assertEquals("STABILIZER",
            byKey.getValue("barbell_back_squat").single { it.relationValue == "ANTERIOR_CORE" }.relationType)
    }

    @Test
    fun repairedRelationsDoNotDuplicateMuscleTargets() {
        val relations = repository.muscleRelations()
        assertEquals(778, relations.size)
        assertEquals(relations.size,
            relations.map { it.exerciseStableKey to it.relationValue }.distinct().size)
        assertTrue(relations.none {
            it.relationType == "PRIMARY" && it.coefficient != 1.0
        })
    }
}
