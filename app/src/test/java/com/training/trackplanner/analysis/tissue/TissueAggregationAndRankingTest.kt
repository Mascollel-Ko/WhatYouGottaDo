package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TissueAggregationAndRankingTest {
    private val catalog by lazy { repository().catalog }

    @Test
    fun loadUnitDebtIsTheSumOfIndependentEventResiduals() {
        val key = TissueRcvLoadKey(catalog.loadUnits.keys.first(), "SMOOTH_CYCLE")
        val rows = listOf(residual("a", key, 2.0, 10L), residual("b", key, 3.5, 20L))

        val state = TissueCurrentStateAggregator(catalog).aggregate(rows, observationDays = 10)
        val loadUnit = state.loadUnits.single { it.key == key }

        assertEquals(5.5, loadUnit.rawResidual.upper, 1e-9)
        assertEquals(TissueCanonicalStatus.CALIBRATING, loadUnit.status)
    }

    @Test
    fun duplicateGroupEventCannotInflateDebt() {
        val key = TissueRcvLoadKey(catalog.loadUnits.keys.first(), "SMOOTH_CYCLE")
        val duplicate = residual("same", key, 4.0, 10L)

        val state = TissueCurrentStateAggregator(catalog).aggregate(
            listOf(duplicate, duplicate.copy(currentResidualRange = TissueResidualRange(0.0, 9.0))),
            observationDays = 10
        )

        assertEquals(9.0, state.loadUnits.single { it.key == key }.rawResidual.upper, 1e-9)
        assertTrue(state.diagnostics.single().contains("Duplicate"))
    }

    @Test
    fun rankingsArePermutationInvariantAndUseStableKeyTieBreakers() {
        val units = catalog.loadUnits.values.take(2).sortedBy { it.stableKey }
        val rows = units.map { unit ->
            residual(
                id = unit.stableKey,
                key = TissueRcvLoadKey(unit.stableKey, "SMOOTH_CYCLE"),
                value = 3.0,
                time = 10L,
                joint = unit.jointComplexStableKey
            )
        }
        val aggregator = TissueCurrentStateAggregator(catalog)
        val first = aggregator.aggregate(rows, 10)
        val reversed = aggregator.aggregate(rows.reversed(), 10)

        assertEquals(first.loadUnits.map { it.key }, reversed.loadUnits.map { it.key })
        assertEquals(first.jointComplexes.map { it.jointComplexStableKey }, reversed.jointComplexes.map { it.jointComplexStableKey })
        val observed = first.loadUnits.filter { it.key.loadDimension != "UNOBSERVED" }
        assertEquals(units.map { it.stableKey }, observed.map { it.key.loadUnitStableKey })
    }

    @Test
    fun contributorDominanceUsesExactOnePointFiveRule() {
        val key = TissueRcvLoadKey(catalog.loadUnits.keys.first(), "SMOOTH_CYCLE")
        val dominant = TissueContributorService.forLoadUnit(
            listOf(residual("a", key, 15.0, 10L), residual("b", key, 10.0, 20L))
        )
        val notDominant = TissueContributorService.forLoadUnit(
            listOf(residual("a", key, 14.999, 10L), residual("b", key, 10.0, 20L))
        )

        assertEquals(listOf("a"), dominant.map(TissueExerciseContribution::exerciseStableKey))
        assertEquals(listOf("a", "b"), notDominant.map(TissueExerciseContribution::exerciseStableKey))
    }

    @Test
    fun jointSummaryUsesWorstAndMaxChildWithoutSummingChildren() {
        val joint = catalog.jointComplexes.values.first { candidate ->
            catalog.loadUnits.values.count { it.jointComplexStableKey == candidate.stableKey } >= 2
        }
        val units = catalog.loadUnits.values.filter { it.jointComplexStableKey == joint.stableKey }.take(2)
        val rows = units.mapIndexed { index, unit ->
            residual(
                id = "event$index",
                key = TissueRcvLoadKey(unit.stableKey, "SMOOTH_CYCLE"),
                value = if (index == 0) 7.0 else 5.0,
                time = index.toLong(),
                joint = joint.stableKey
            )
        }
        val summary = TissueCurrentStateAggregator(catalog).aggregate(rows, 10).jointComplexes
            .single { it.jointComplexStableKey == joint.stableKey }

        assertEquals(7.0, summary.highestChild!!.rawResidual.upper, 1e-9)
        assertFalse(summary.highestChild.rawResidual.upper == 12.0)
    }

    @Test
    fun calibrationNeverFallsBackToLowAndSymptomOverrideAppliesImmediately() {
        val calibrating = TissueCalibrationPolicy.classify(10.0, listOf(1.0, 2.0), 55, TissueSymptomOverride.NONE)
        val overridden = TissueCalibrationPolicy.classify(1.0, emptyList(), 1, TissueSymptomOverride.BLOCK)
        val eligible = TissueCalibrationPolicy.classify(10.0, listOf(1.0, 2.0, 20.0), 56, TissueSymptomOverride.NONE)

        assertEquals(TissueCanonicalStatus.CALIBRATING, calibrating.status)
        assertNull(calibrating.normalizedScore)
        assertEquals(TissueCanonicalStatus.VERY_HIGH, overridden.status)
        assertTrue(eligible.normalizedScore!! in 0.0..100.0)
        assertTrue(eligible.diagnostics.single().contains("not injury probability"))
    }

    @Test
    fun allJointAndLoadUnitSummariesRemainVisibleAndUnsided() {
        val state = TissueCurrentStateAggregator(catalog).aggregate(emptyList(), observationDays = 0)

        assertEquals(15, state.jointComplexes.size)
        assertEquals(77, state.loadUnits.size)
        assertFalse(state.loadUnits.any {
            it.key.loadUnitStableKey.contains("LEFT") || it.key.loadUnitStableKey.contains("RIGHT")
        })
    }

    private fun residual(
        id: String,
        key: TissueRcvLoadKey,
        value: Double,
        time: Long,
        joint: String = catalog.loadUnits.getValue(key.loadUnitStableKey).jointComplexStableKey
    ): TissueEventResidual {
        val event = TissueExposureEvent(
            eventId = id,
            recordId = id.hashCode().toLong(),
            exerciseStableKey = id,
            exerciseName = id,
            key = key,
            jointComplexStableKey = joint,
            tissueClass = catalog.loadUnits.getValue(key.loadUnitStableKey).tissueClass,
            initialExposure = value,
            rawDose = value,
            doseReference56d = 1.0,
            normalizedDose = value,
            selectedEffort = TissueEffortSelection(1.0, TissueEffortSource.SET_RPE),
            magnitudeM = 10.0,
            rapidityS = 1.0,
            contextModifier = 1.0,
            mappingRoleWeight = 1.0,
            curveIds = emptyMap(),
            performedTime = TissueEventTimeRange(time, time, TissueTimestampPrecision.EXACT),
            scoreVersion = "test",
            protocolVersion = "test",
            curveVersion = "test",
            evidenceGrade = "test",
            sourceRefs = emptyList(),
            diagnostics = emptyList()
        )
        return TissueEventResidual(
            event,
            channelResiduals = emptyMap(),
            currentResidualRange = TissueResidualRange(value, value),
            biologicalActivityRange = null,
            diagnostics = emptyList()
        )
    }

    private fun repository(): TissueRcvAssetRepository =
        TissueRcvAssetRepository.fromCsv(TissueRcvAssetFiles.required.associateWith(::asset))

    private fun asset(name: String): String = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
