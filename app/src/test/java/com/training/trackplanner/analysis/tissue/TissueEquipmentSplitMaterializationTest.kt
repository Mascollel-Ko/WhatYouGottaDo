package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TissueEquipmentSplitMaterializationTest {
    private val catalog by lazy { repository().catalog }
    private val splitRows by lazy {
        TissueMetadataParser.table(file("outputs/final_closeout/equipment_variant_split_plan.csv").readText()).rows
    }

    @Test
    fun handledIdentitySetEqualsCurrentSelectablePlusApprovedHistorySources() {
        val inventory = TissueMetadataParser.table(
            file("outputs/final_closeout/canonical_exercise_inventory_final.csv").readText()
        ).rows
        val selectable = inventory.filter { it.getValue("selectable") == "YES" }
            .map { it.getValue("stableKey") }.toSet()
        val historySources = splitRows.map { it.getValue("sourceGenericStableKey") }.toSet()

        assertEquals(242, selectable.size)
        assertEquals(17, historySources.size)
        assertEquals(selectable + historySources + setOf("pull_up"), catalog.exerciseStableKeys)
        assertEquals(setOf("ex_dd16e07a"), catalog.exerciseStableKeys - catalog.authorityRows.map {
            it.exerciseStableKey
        }.toSet())
    }

    @Test
    fun everyTargetCopiesOnlyItsExplicitApprovedSourceProfile() {
        assertEquals(34, splitRows.size)
        splitRows.forEach { split ->
            val targetKey = split.getValue("variantStableKey")
            val sourceKey = split.getValue("sourceGenericStableKey")
            val sourceRows = catalog.authorityRows.filter { it.exerciseStableKey == sourceKey }
                .sortedBy(::profileIdentity)
            val targetRows = catalog.authorityRows.filter { it.exerciseStableKey == targetKey }
                .sortedBy(::profileIdentity)

            assertEquals(targetKey, sourceRows.size, targetRows.size)
            assertEquals(targetKey, sourceRows.map(::inheritedProfile), targetRows.map(::inheritedProfile))
            assertEquals(sourceKey, catalog.exerciseDoseProfiles.getValue(targetKey).sourceStableKey)

            val sourceProtocol = catalog.protocols.getValue(sourceKey)
            val targetProtocol = catalog.protocols.getValue(targetKey)
            assertEquals(sourceProtocol.defaultProtocolClass, targetProtocol.defaultProtocolClass)
            assertEquals(sourceProtocol.functionalCurveId, targetProtocol.functionalCurveId)
            assertEquals(sourceProtocol.jointProtectionCurveId, targetProtocol.jointProtectionCurveId)
            assertEquals(sourceProtocol.fastMechanicalCurveId, targetProtocol.fastMechanicalCurveId)
            assertEquals(sourceProtocol.biologicalCurveRouting, targetProtocol.biologicalCurveRouting)
        }
    }

    @Test
    fun exactDoseProfilesHaveApprovedClassificationAndArithmetic() {
        val grouped = catalog.exerciseDoseProfiles.values.groupingBy { it.doseKind }.eachCount()
        assertEquals(27, grouped[TissueExerciseDoseKind.WEIGHTED_REPETITION])
        assertEquals(5, grouped[TissueExerciseDoseKind.BODYWEIGHT_REPETITION])
        assertEquals(2, grouped[TissueExerciseDoseKind.LOAD_TIME])

        assertDose("standing_bodyweight_calf_raise", 800.0)
        assertDose("standing_dumbbell_calf_raise", 200.0)
        assertDose("standing_calf_raise_machine", 200.0)
        assertDose("suspension_trainer_inverted_row", 680.0)
        assertDose("gymnastic_ring_inverted_row", 680.0)
        assertDose("one_arm_suspension_trainer_row", 680.0)
        assertDose("one_arm_gymnastic_ring_row", 680.0)
        assertDose("dumbbell_farmer_carry", 600.0)
        assertDose("kettlebell_farmer_carry", 600.0)
    }

    @Test
    fun historicalGenericCarryAndGenericStretchingRemainUnchanged() {
        val genericCarry = TissueRcvDoseResolver.resolve(record("ex_a1fc4533"), "LOADED_CARRY")
        assertEquals(30.0, genericCarry.resolvedDose ?: -1.0, 0.001)

        val stretching = catalog.protocols.getValue("ex_dd16e07a")
        assertEquals("UNRESOLVED_GENERIC", stretching.mappingStatus)
        assertTrue(catalog.authorityRows.none { it.exerciseStableKey == "ex_dd16e07a" })
        val unresolved = TissueRcvDoseResolver.resolve(record("ex_dd16e07a"), "UNRESOLVED")
        assertNull(unresolved.resolvedDose)
    }

    @Test
    fun materializationContainsNoNameOrEquipmentDrivenFallbackContract() {
        catalog.exerciseDoseProfiles.values.forEach { profile ->
            assertEquals("EXACT_STABLE_KEY_ONLY", profile.compatibilityMode)
            assertEquals("MATERIALIZED_FROM_APPROVED_EQUIPMENT_SPLIT", profile.provenance)
            assertFalse(profile.loadSemantics.contains("distance", ignoreCase = true) && profile.doseKind != TissueExerciseDoseKind.LOAD_TIME)
        }
        catalog.exerciseDoseProfiles.filterKeys { it in setOf("dumbbell_farmer_carry", "kettlebell_farmer_carry") }
            .values.forEach { profile ->
                assertEquals(
                    "recordedWeightKg * confirmedSeconds; no distance or per-hand multiplier",
                    profile.loadSemantics
                )
            }
    }

    private fun assertDose(stableKey: String, expected: Double) {
        val profile = catalog.exerciseDoseProfiles.getValue(stableKey)
        val basis = catalog.authorityRows.first { it.exerciseStableKey == stableKey }.doseBasis
        val result = TissueRcvDoseResolver.resolve(record(stableKey), basis, profile)
        assertEquals(stableKey, expected, result.resolvedDose ?: -1.0, 0.001)
    }

    private fun record(stableKey: String): TissueWorkoutRecord = TissueWorkoutRecord(
        entry = WorkoutEntry(
            id = stableKey.hashCode().toLong(),
            date = "2026-08-15",
            exerciseStableKey = stableKey,
            exerciseName = "renamed",
            category = "fixture",
            rpe = 8.0
        ),
        sets = listOf(
            WorkoutSet(
                id = 1,
                entryId = stableKey.hashCode().toLong(),
                setIndex = 1,
                reps = 10,
                weightKg = 20.0,
                seconds = 30,
                confirmed = true,
                rpe = 8.0
            )
        ),
        exercise = Exercise(name = "renamed", category = "fixture", stableKey = stableKey),
        bodyWeightKg = 80.0
    )

    private fun profileIdentity(row: TissueRcvAuthorityRow): String =
        "${row.loadUnitStableKey}|${row.loadProfileP}|${row.mappingRoles.sorted()}"

    private fun inheritedProfile(row: TissueRcvAuthorityRow): List<Any> = listOf(
        row.bodyRegion,
        row.loadUnitStableKey,
        row.loadUnitCode,
        row.loadUnitName,
        row.tissueClass,
        row.jointComplexStableKey,
        row.jointComplexName,
        row.mappingRoles.sorted(),
        row.magnitudeM,
        row.rapidityS,
        row.contextC,
        row.loadProfileP,
        row.contextFlags.sorted(),
        row.scoreVersion,
        row.mappingConfidence,
        row.familyImportance,
        row.scoreStatus,
        row.sourceRefs
    )

    private fun repository(): TissueRcvAssetRepository = TissueRcvAssetRepository.fromCsv(
        TissueRcvAssetFiles.required.associateWith { asset(it).readText(Charsets.UTF_8) }
    )

    private fun asset(name: String): File = file("app/src/main/assets/metadata/tissue_load_v1/$name")

    private fun file(path: String): File = sequenceOf(
        File(path),
        File("../$path")
    ).first(File::exists)
}
