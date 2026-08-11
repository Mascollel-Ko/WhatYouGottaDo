package com.training.trackplanner.data

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
            "ex_bd072cd", "ex_d20b7487", "ex_d9084b5e", "ex_dd2f732e",
            "ex_e159d15a", "ex_e994008a", "ex_eaea872c", "single_leg_rdl"
        )

        assertEquals(257, repository.identities().size)
        assertEquals(241, repository.selectableIdentities().size)
        assertEquals(historyKeys, repository.identities().filter(CanonicalExerciseIdentity::historyOnly).mapTo(mutableSetOf(), CanonicalExerciseIdentity::stableKey))
        assertEquals(241, repository.exercises().size)
        assertEquals(257, repository.exercises(includeHistory = true).size)
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

        val catalog = ExerciseRoleRelationCatalog.of(repository.trainingRoleRelations(), repository.programSlotCapabilityRelations())
        val inventory = ProgramCandidateInventory().collect(
            exercises = repository.exercises(includeHistory = true),
            runtimeMetadataCatalog = repository.runtimeMetadataCatalog(),
            availableEquipment = emptySet(),
            roleRelationCatalog = catalog
        )
        assertTrue(inventory.candidates.none { candidate -> repository.identity(candidate.exercise.stableKey)?.historyOnly == true })
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
        assertEquals(257, SeedData.exactExerciseMetadataByStableKey(context).size)
    }

    @Test
    fun canonicalRepositoryExposesEveryProductionMetadataDomain() {
        assertEquals(2253, repository.movementRelations().size)
        assertEquals(797, repository.muscleRelations().size)
        assertEquals(3913, repository.ofiRelations().size)
        assertEquals(241, repository.recoveryProfiles().size)
        assertEquals(1865, repository.badmintonRelations().size)
        assertEquals(194, repository.progressionRelations().size)
        assertEquals(17, repository.strengthProxyRelations().size)
        assertEquals(3224, repository.tissueRepository().catalog.authorityRows.size)
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

    @Test
    fun legacyCurrentExerciseRowsMatchCanonicalBootstrapExceptHistoryGate() {
        fun rows(asset: String) = context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { reader ->
            val parsed = reader.lineSequence().filter(String::isNotBlank).map(SeedData::parseCsvLine).toList()
            val header = parsed.first().map { it.removePrefix("\uFEFF") }
            parsed.drop(1).map { values -> header.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap() }
        }
        val images = rows("exercise_image_mapping.csv").associate { row ->
            row.getValue("stable_key") to (row.getValue("image_asset_name") to (row.getValue("needs_review") == "1"))
        }
        val legacy = SeedData.exercisesFromParsedRows(rows("training_settings_seed.csv"))
            .map { exercise ->
                val image = images[exercise.stableKey]
                if (image == null) exercise else exercise.copy(
                    imageAssetName = image.first,
                    needsReview = exercise.needsReview || image.second
                )
            }
            .associateBy(Exercise::stableKey)
        val canonical = repository.exercises(includeHistory = true).associateBy(Exercise::stableKey)
        val getters = Exercise::class.java.methods.filter { method ->
            method.parameterCount == 0 &&
                (method.name.startsWith("get") || method.name.startsWith("is")) &&
                method.name != "getClass"
        }
        val differences = legacy.flatMap { (stableKey, old) ->
            val current = canonical[stableKey] ?: return@flatMap listOf("$stableKey missing")
            val normalizedOld = if (repository.identity(stableKey)?.historyOnly == true) {
                old.copy(planningEligibility = "HISTORY_ONLY", isActive = false)
            } else {
                old
            }
            getters.mapNotNull { method ->
                val oldValue = method.invoke(normalizedOld)
                val currentValue = method.invoke(current)
                "$stableKey.${method.name}: $oldValue != $currentValue".takeIf { oldValue != currentValue }
            }
        }

        assertTrue("Unexpected bootstrap parity differences:\n${differences.joinToString("\n")}", differences.isEmpty())
    }
}
