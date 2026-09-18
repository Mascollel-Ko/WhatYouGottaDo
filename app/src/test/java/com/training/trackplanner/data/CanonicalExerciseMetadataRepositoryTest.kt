package com.training.trackplanner.data

import com.training.trackplanner.data.program.legacy.LegacyAutoCandidateAuthority as ProgramCandidateAuthority
import com.training.trackplanner.data.program.legacy.LegacyAutoRuleTables as ProgramRuleTables

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
class CanonicalExerciseMetadataRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = CanonicalExerciseMetadataRepository(context)

    @Test
    fun authorityHasExactSelectableAndHistoryIdentitySets() {
        val historyKeys = setOf(
            "ex_516f4456", "ex_7176cbee", "ex_8e18b02a", "ex_8e51640a",
            "ex_99728d25", "ex_a1fc4533", "ex_a9e8859c", "ex_ac7df636",
            "ex_bd072cd", "ex_d20b7487", "ex_d9084b5e"
        )

        assertEquals(253, repository.identities().size)
        assertEquals(242, repository.selectableIdentities().size)
        assertEquals(historyKeys, repository.identities().filter(CanonicalExerciseIdentity::historyOnly).mapTo(mutableSetOf(), CanonicalExerciseIdentity::stableKey))
        assertEquals(242, repository.exercises().size)
        assertEquals(253, repository.exercises(includeHistory = true).size)
        assertTrue(repository.exercises().none { it.stableKey in historyKeys })
        assertTrue(repository.exercises(includeHistory = true).filter { it.stableKey in historyKeys }.all {
            !it.isActive && it.planningEligibility == "HISTORY_ONLY"
        })
    }

    @Test
    fun approvedProgramRelationsAreScopedToSelectableVariants() {
        val roles = repository.trainingRoleRelations().groupBy(ExerciseTrainingRoleRelation::exerciseStableKey)
        val slots = repository.programSlotCapabilityRelations().groupBy(ExerciseProgramSlotCapabilityRelation::exerciseStableKey)

        assertEquals(setOf("STRENGTH"), roles.getValue("ex_8824026f").mapTo(mutableSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode))
        assertEquals(setOf("ACCESSORY_SLOT"), slots.getValue("ex_8824026f").mapTo(mutableSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode))
        assertFalse("single_leg_rdl" in slots)
        assertFalse("ex_bd072cd" in slots)
        assertTrue(listOf("dumbbell_single_leg_rdl", "kettlebell_single_leg_rdl").all { key -> slots.getValue(key).any { it.capabilityCode == "MAIN_STRENGTH_SLOT" } })
        assertTrue(listOf("standing_bodyweight_calf_raise", "standing_calf_raise_machine", "standing_dumbbell_calf_raise").all { key -> slots.getValue(key).any { it.capabilityCode == "ACCESSORY_SLOT" } })

        val historyKeys = repository.identities()
            .filter(CanonicalExerciseIdentity::historyOnly)
            .map(CanonicalExerciseIdentity::stableKey)
        assertTrue(historyKeys.none(ProgramCandidateAuthority::allows))
        val ruleKeys = ProgramRuleTables.mainExercises.values.flatten() +
            ProgramRuleTables.pairedAccessories.values.flatten() +
            ProgramRuleTables.smallPartAccessories.values.flatten() +
            ProgramRuleTables.badmintonAccessories.values.flatten()
        assertTrue(ruleKeys.none { spec -> repository.identity(spec.stableKey)?.historyOnly == true })
    }

    @Test
    fun materializedTimingAndSeedBootstrapAreExact() {
        val exercises = repository.exercises()
        assertTrue(exercises.all { exercise -> repository.timing(exercise.stableKey)?.defaultRestSeconds == exercise.defaultRestSeconds })
        assertEquals(exercises, SeedData.exercises(context))
        assertEquals(253, SeedData.exactExerciseMetadataByStableKey(context).size)
    }

    @Test
    fun canonicalRepositoryExposesEveryProductionMetadataDomain() {
        assertEquals(2248, repository.movementRelations().size)
        assertEquals(797, repository.muscleRelations().size)
        assertEquals(3913, repository.ofiRelations().size)
        assertEquals(241, repository.recoveryProfiles().size)
        assertEquals(1864, repository.badmintonRelations().size)
        assertEquals(242, repository.coreCatalog().selectableProfiles().size)
        assertEquals(280, repository.badmintonObjectiveCatalog().allRelations().size)
        assertEquals(194, repository.progressionRelations().size)
        assertEquals(16, repository.strengthProxyRelations().size)
        assertEquals(3651, repository.tissueRepository().catalog.authorityRows.size)
    }

    @Test
    fun physicalQualityRelationsUseRuntimeAuthorityAndKeepLayersSeparate() {
        val catalog = repository.physicalQualityCatalog()
        assertEquals(43, catalog.allRelations().size)
        assertTrue(catalog.allRelations().all { it.prescriptionDependent })
        assertTrue(
            catalog.relations("barbell_back_squat").mapTo(mutableSetOf()) { it.qualityId }.containsAll(
                setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)
            )
        )
        assertTrue(
            catalog.relations("ex_1cf51b6b").mapTo(mutableSetOf()) { it.qualityId } ==
                setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY)
        )

        val bootstrapLeakageKeys = listOf(
            "cable_hip_adduction",
            "hip_adduction_machine",
            "ex_728da646",
            "ex_8824026f",
            "ex_1cf51b6b"
        )
        assertTrue(
            bootstrapLeakageKeys.flatMap(catalog::relations).none {
                it.qualityId == TrainableQuality.REACTIVE_STRENGTH_SSC
            }
        )
        assertTrue(catalog.relations("ex_7404067c").any { it.qualityId == TrainableQuality.POWER })
        assertTrue(catalog.relations("ex_d6726746").any { it.qualityId == TrainableQuality.REACTIVE_STRENGTH_SSC })
        assertTrue(catalog.relations("ex_4773b6ea").any { it.qualityId == TrainableQuality.CARDIORESPIRATORY_FITNESS })
        assertTrue(catalog.relations("ex_149730de").any { it.qualityId == TrainableQuality.MOBILITY_ROM })

        listOf("band_pallof_press", "cable_pallof_press", "ex_a44ae2ca", "ex_f6d43398", "band_woodchop")
            .forEach { stableKey -> assertTrue(catalog.relations(stableKey).isEmpty()) }
        listOf("ex_1c7f2342", "ex_33841b88", "ex_bc84eb7f", "ex_c5f4c242")
            .forEach { stableKey -> assertTrue(catalog.relations(stableKey).isEmpty()) }
        listOf("ex_91d8430b", "ex_c7977dfd").forEach { stableKey ->
            assertTrue(catalog.isAssessmentOnly(stableKey))
            assertTrue(catalog.relations(stableKey).isEmpty())
        }
        assertTrue(catalog.relations("kettlebell_halo").isEmpty())
    }

    @Test
    fun trunkControlRelationsAreDecomposedWithoutCollapsingRotationOrBracing() {
        val patterns = repository.movementRelations()
            .filter { it.relationType == "MOVEMENT_PATTERN" }
            .groupBy { it.exerciseStableKey }
            .mapValues { (_, rows) -> rows.mapTo(mutableSetOf()) { it.relationValue } }

        assertTrue(patterns.values.none { "TRUNK_BRACE" in it })
        assertTrue("AXIAL_BRACING" in patterns.getValue("barbell_back_squat"))
        assertTrue("AXIAL_BRACING" in patterns.getValue("barbell_deadlift"))
        assertTrue("AXIAL_BRACING" in patterns.getValue("dumbbell_farmer_carry"))
        assertTrue("ANTI_ROTATION" in patterns.getValue("band_pallof_press"))
        assertTrue("ANTI_ROTATION" in patterns.getValue("landmine_anti_rotation"))
        assertTrue("ANTI_LATERAL_FLEXION" in patterns.getValue("ex_f6d43398"))
        assertTrue("ANTI_EXTENSION" in patterns.getValue("ex_a44ae2ca"))
        assertEquals(
            setOf("DYNAMIC_TRUNK_STABILIZATION", "ANTI_EXTENSION"),
            patterns.getValue("ex_d5bdffe1")
        )
        assertTrue("TRUNK_ROTATION" in patterns.getValue("band_lift"))
        assertFalse("ANTI_ROTATION" in patterns.getValue("barbell_back_squat"))
    }

    @Test
    fun protectedCanonicalMetadataCannotBeReplacedByPersistedOverride() {
        val exercise = repository.exercises().first()
        val canonical = repository.runtimeMetadataCatalog().resolve(exercise)!!
        val persisted = canonical.copy(
            programSlot = "ROOM_OVERRIDE",
            recoveryDecayProfile = "ROOM_OVERRIDE"
        )
        val resolved = RuntimeExerciseMetadataResolver(
            repository.runtimeMetadataCatalog(),
            listOf(persisted)
        ).resolve(exercise)

        assertEquals("ROOM_OVERRIDE", resolved.programSlot)
        assertEquals(canonical.recoveryDecayProfile, resolved.recoveryDecayProfile)
    }

}
