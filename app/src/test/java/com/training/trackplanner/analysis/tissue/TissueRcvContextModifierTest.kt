package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import java.io.File
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TissueRcvContextModifierTest {
    private val catalog by lazy {
        TissueRcvAssetRepository.fromCsv(
            TissueRcvAssetFiles.required.associateWith(::asset)
        ).catalog
    }

    @Test
    fun approvedCodContextChangesEligibleLowerLimbExposure() {
        val event = TissueRcvEventLedgerBuilder(catalog, ZoneOffset.UTC)
            .build(listOf(record("ex_ae9ecdbc", "배드민턴")))
            .events
            .single { it.key.loadUnitStableKey == "lu_ff22c06225" }

        assertEquals(1.06, event.contextModifier, 0.0)
        assertEquals(0.424, event.initialExposure, 1e-12)
        assertEquals(1.0, event.mappingRoleWeight, 0.0)
        assertEquals(
            TissueContextModifierStatus.APPLIED_APPROVED_COD_CONTEXT,
            event.contextModifierStatus
        )
        assertNotNull(event.contextModifierRuleId)
        assertEquals(TISSUE_COD_CONTEXT_POLICY_VERSION, event.contextPolicyVersion)
        assertTrue(event.scoreVersion.contains(TISSUE_RCV_CALCULATION_VERSION))
    }

    @Test
    fun approvedFactorsProduceExactBoundedNumericalResults() {
        assertEquals(0.5232, tissueRcvInitialExposure(5.0, 1.2, 0.8, 1.09), 1e-12)
        assertEquals(0.5088, tissueRcvInitialExposure(5.0, 1.2, 0.8, 1.06), 1e-12)
        assertEquals(0.4992, tissueRcvInitialExposure(5.0, 1.2, 0.8, 1.04), 1e-12)
        assertEquals(0.48, tissueRcvInitialExposure(5.0, 1.2, 0.8, 1.0), 1e-12)
    }

    @Test
    fun approvedExerciseKeepsUpperBodyAndSpinalEventsNeutral() {
        val events = TissueRcvEventLedgerBuilder(catalog, ZoneOffset.UTC)
            .build(listOf(record("ex_ae9ecdbc", "배드민턴")))
            .events

        listOf("lu_211533cb7a", "lu_d967354bce").forEach { loadUnitStableKey ->
            val event = events.single { it.key.loadUnitStableKey == loadUnitStableKey }
            assertEquals(1.0, event.contextModifier, 0.0)
            assertEquals(baseExposure(event), event.initialExposure, 1e-12)
            assertEquals(
                TissueContextModifierStatus.DEFAULT_LOAD_UNIT_NOT_ELIGIBLE,
                event.contextModifierStatus
            )
        }
    }

    @Test
    fun nonWhitelistedExerciseKeepsEveryEventNeutral() {
        val events = TissueRcvEventLedgerBuilder(catalog, ZoneOffset.UTC)
            .build(listOf(record("barbell_back_squat", "스쿼트")))
            .events

        assertTrue(events.isNotEmpty())
        events.forEach { event ->
            assertEquals(1.0, event.contextModifier, 0.0)
            assertEquals(baseExposure(event), event.initialExposure, 1e-12)
            assertEquals(
                TissueContextModifierStatus.DEFAULT_EXERCISE_NOT_WHITELISTED,
                event.contextModifierStatus
            )
        }
    }

    @Test
    fun duplicateAuthorityComponentRowsStillApplyContextExactlyOnce() {
        val duplicate = catalog.authorityRows.single {
            it.exerciseStableKey == "ex_ae9ecdbc" &&
                it.loadUnitStableKey == "lu_ff22c06225"
        }
        val duplicateCatalog = catalog.copy(authorityRows = catalog.authorityRows + duplicate)
        val events = TissueRcvEventLedgerBuilder(duplicateCatalog, ZoneOffset.UTC)
            .build(listOf(record("ex_ae9ecdbc", "배드민턴")))
            .events
        val event = events.single { it.key.loadUnitStableKey == "lu_ff22c06225" }

        assertEquals(1.06, event.contextModifier, 0.0)
        assertEquals(baseExposure(event) * 1.06, event.initialExposure, 1e-12)
        assertTrue(event.initialExposure != baseExposure(event) * 1.06 * 1.06)
    }

    private fun record(stableKey: String, name: String) = TissueWorkoutRecord(
        entry = WorkoutEntry(
            id = 1,
            date = "2026-07-17",
            exerciseId = 1,
            exerciseName = name,
            category = "스포츠",
            rpe = 8.0,
            performedAt = 1_000L
        ),
        sets = listOf(
            WorkoutSet(
                id = 1,
                entryId = 1,
                setIndex = 1,
                reps = 10,
                weightKg = 20.0,
                seconds = 60,
                confirmed = true
            )
        ),
        exercise = Exercise(
            id = 1,
            name = name,
            category = "스포츠",
            stableKey = stableKey
        ),
        bodyWeightKg = 80.0
    )

    private fun baseExposure(event: TissueExposureEvent): Double =
        (event.magnitudeM / 10.0) *
            event.normalizedDose *
            requireNotNull(event.selectedEffort.value)

    private fun asset(name: String): String = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
