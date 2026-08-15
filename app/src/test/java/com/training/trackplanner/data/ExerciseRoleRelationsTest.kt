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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseRoleRelationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun canonicalAssetsPreserveLegacyCapabilitiesExceptApprovedAuthorityDeltas() {
        val exercises = SeedData.exercises(context)
        val catalog = ExerciseRoleRelationAssetLoader(context).load(
            exercises.mapTo(mutableSetOf(), Exercise::stableKey)
        )
        val baseline = csvRows(repoFile("docs/audits/training_role_whitelist_reconstruction.csv"))
        val expected = baseline.associate { row ->
            row.getValue("exerciseStableKey") to LegacyTrainingRoleImportMapper
                .resolve(row.getValue("proposedLegacyRole"))
                .programSlotCapabilities
                .single()
        }.toMutableMap().apply {
            remove("single_leg_rdl")
            remove("ex_bd072cd")
            this["dumbbell_single_leg_rdl"] = ProgramSlotCapability.MAIN_STRENGTH_SLOT
            this["kettlebell_single_leg_rdl"] = ProgramSlotCapability.MAIN_STRENGTH_SLOT
            this["standing_bodyweight_calf_raise"] = ProgramSlotCapability.ACCESSORY_SLOT
            this["standing_calf_raise_machine"] = ProgramSlotCapability.ACCESSORY_SLOT
            this["standing_dumbbell_calf_raise"] = ProgramSlotCapability.ACCESSORY_SLOT
            this["ex_8824026f"] = ProgramSlotCapability.ACCESSORY_SLOT
        }
        val actual = exercises.associateNotNull { exercise ->
            catalog.programSlotCapabilities(exercise.stableKey)
                .singleOrNull()
                ?.let { exercise.stableKey to it }
        }

        assertEquals(26, baseline.size)
        assertEquals(29, expected.size)
        assertEquals(expected, actual)
        assertTrue(catalog.programSlotCapabilities("barbell_back_squat").isEmpty())
    }

    @Test
    fun trainingMeaningDoesNotGrantProgramPlacement() {
        val exercise = Exercise(
            name = "Isolation fixture",
            category = "strength",
            stableKey = "barbell_back_squat",
            movementPattern = "ISOLATION",
            movementCategory = "HYPERTROPHY",
            compoundType = "ISOLATION"
        )
        val emptyProfile = SlotCapabilityProfile(
            primary = emptySet(),
            secondary = emptySet(),
            weakMatches = emptySet(),
            source = SlotCapabilitySource.NONE
        )
        val meaningOnly = ProgramCandidate(
            exercise = exercise,
            metadata = null,
            canonical = false,
            slotCapabilities = emptyProfile,
            trainingRoles = setOf(TrainingRole.STRENGTH)
        )
        val placement = meaningOnly.copy(
            programSlotCapabilities = setOf(ProgramSlotCapability.MAIN_STRENGTH_SLOT)
        )

        assertFalse(meaningOnly.allowedForRole(ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT, ProgramExerciseRole.ANCHOR))
        assertTrue(placement.allowedForRole(ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT, ProgramExerciseRole.ANCHOR))

        val catalog = ExerciseRoleRelationCatalog.of(
            trainingRelations = listOf(
                ExerciseTrainingRoleRelation(exercise.stableKey, TrainingRole.STRENGTH.name, "test", "APPROVED")
            ),
            capabilityRelations = listOf(
                ExerciseProgramSlotCapabilityRelation(
                    exercise.stableKey,
                    ProgramSlotCapability.MAIN_STRENGTH_SLOT.name,
                    "test",
                    "APPROVED"
                )
            )
        )
        val collected = ProgramCandidateInventory().collect(
            exercises = listOf(exercise),
            runtimeMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
            availableEquipment = emptySet(),
            roleRelationCatalog = catalog
        ).candidates.single()

        assertEquals(setOf(TrainingRole.STRENGTH), collected.trainingRoles)
        assertEquals(setOf(ProgramSlotCapability.MAIN_STRENGTH_SLOT), collected.programSlotCapabilities)
        assertTrue(collected.allowedForRole(ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT, ProgramExerciseRole.ANCHOR))
    }

    @Test
    fun invalidCodesDuplicatesAndOrphansAreRejected() {
        assertTrue(runCatching {
            ExerciseRoleRelationCatalog.of(
                listOf(ExerciseTrainingRoleRelation("x", "MAIN_STRENGTH", "test", "APPROVED")),
                emptyList()
            )
        }.isFailure)
        assertTrue(runCatching {
            ExerciseRoleRelationCatalog.of(
                emptyList(),
                listOf(
                    ExerciseProgramSlotCapabilityRelation("x", "ACCESSORY_SLOT", "test", "APPROVED"),
                    ExerciseProgramSlotCapabilityRelation("x", "ACCESSORY_SLOT", "test", "APPROVED")
                )
            )
        }.isFailure)
        assertTrue(runCatching { ExerciseRoleRelationAssetLoader(context).load(emptySet()) }.isFailure)
    }

    @Test
    fun analysisDoesNotConsumeProgramPlacementAndLegacyFieldIsImportOnly() {
        val root = repoFile("app/src/main/java")
        val analysisReferences = root.resolve("com/training/trackplanner/analysis")
            .walkTopDown().filter(File::isFile).filter { it.extension == "kt" }
            .filter {
                val source = it.readText()
                source.contains("com.training.trackplanner.data.ProgramSlotCapability") ||
                    source.contains("ExerciseRoleRelationCatalog")
            }.toList()
        assertTrue(analysisReferences.isEmpty())

        val allowed = setOf("ExerciseStableKeyMigration.kt", "TrainingRoleSplitMigration.kt")
        val legacyReferences = root.walkTopDown().filter(File::isFile).filter { it.extension == "kt" }
            .filter { Regex("\\btrainingRole\\b").containsMatchIn(it.readText()) }
            .map(File::getName).filterNot(allowed::contains).toList()
        assertTrue("Unexpected production legacy field references: $legacyReferences", legacyReferences.isEmpty())
    }

    private fun csvRows(file: File): List<Map<String, String>> {
        val rows = file.readLines(Charsets.UTF_8).filter(String::isNotBlank).map(SeedData::parseCsvLine)
        val header = rows.first().map { it.removePrefix("\uFEFF") }
        return rows.drop(1).map { values ->
            header.mapIndexed { index, name -> name to values.getOrElse(index) { "" }.trim() }.toMap()
        }
    }

    private fun repoFile(path: String): File =
        listOf(File(path), File("../$path"), File("../../$path")).first(File::exists)

    private fun <K, V : Any> Iterable<K>.associateNotNull(transform: (K) -> Pair<String, V>?): Map<String, V> =
        mapNotNull(transform).toMap()
}
