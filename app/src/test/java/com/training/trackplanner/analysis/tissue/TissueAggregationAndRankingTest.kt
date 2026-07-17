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

        val state = aggregate(rows)
        val loadUnit = state.loadUnits.single { it.key.loadUnitStableKey == key.loadUnitStableKey }

        assertEquals(5.5, loadUnit.rawResidual.upper, 1e-9)
        assertEquals(TissueCanonicalStatus.LOW, loadUnit.status)
    }

    @Test
    fun duplicateGroupEventCannotInflateDebt() {
        val key = TissueRcvLoadKey(catalog.loadUnits.keys.first(), "SMOOTH_CYCLE")
        val duplicate = residual("same", key, 4.0, 10L)

        val state = aggregate(listOf(duplicate, duplicate.copy(currentResidualRange = TissueResidualRange(0.0, 9.0))))

        assertEquals(9.0, state.loadUnits.single { it.key.loadUnitStableKey == key.loadUnitStableKey }.rawResidual.upper, 1e-9)
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
        val first = aggregate(rows)
        val reversed = aggregate(rows.reversed())

        assertEquals(first.loadUnits.map { it.key }, reversed.loadUnits.map { it.key })
        assertEquals(first.jointComplexes.map { it.jointComplexStableKey }, reversed.jointComplexes.map { it.jointComplexStableKey })
        assertEquals(units.map { it.stableKey }, first.loadUnits.take(2).map { it.key.loadUnitStableKey })
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
        val summary = aggregate(rows).jointComplexes
            .single { it.jointComplexStableKey == joint.stableKey }

        assertEquals(7.0, summary.highestChild!!.rawResidual.upper, 1e-9)
        assertFalse(summary.highestChild.rawResidual.upper == 12.0)
    }

    @Test
    fun validPriorClassifiesShortHistoryAndSymptomOverrideAppliesImmediately() {
        val unit = catalog.loadUnits.keys.first()
        val prior = baseline(unit)
        val shortHistory = TissueRelativeStateClassifier.classify(1.0, prior, TissueSymptomOverride.NONE)
        val overridden = TissueRelativeStateClassifier.classify(1.0, prior, TissueSymptomOverride.BLOCK)
        val unavailable = TissueRelativeStateClassifier.classify(1.0, null, TissueSymptomOverride.NONE)

        assertEquals(TissueCanonicalStatus.LOW, shortHistory.status)
        assertEquals(TissueCanonicalStatus.VERY_HIGH, overridden.status)
        assertEquals(TissueCanonicalStatus.UNAVAILABLE, unavailable.status)
        assertNull(unavailable.relativeBandPosition)
    }

    @Test
    fun allJointAndLoadUnitSummariesRemainVisibleAndUnsided() {
        val state = aggregate(emptyList())

        assertEquals(15, state.jointComplexes.size)
        assertEquals(77, state.loadUnits.size)
        assertFalse(state.loadUnits.any {
            it.key.loadUnitStableKey.contains("LEFT") || it.key.loadUnitStableKey.contains("RIGHT")
        })
    }

    @Test
    fun analysisUiShowsEveryUnsidedJointAndLoadUnit() {
        val state = aggregate(emptyList())
        val ui = TissueAnalysisUiMapper.map(state)
        val labels = ui.joints.flatMap { joint -> listOf(joint.name) + joint.children.map { it.name } }

        assertEquals(15, ui.joints.size)
        assertEquals(77, ui.joints.sumOf { it.children.size })
        assertFalse(labels.any { it.contains("왼쪽") || it.contains("오른쪽") })
        assertFalse(labels.any { it.contains("LEFT") || it.contains("RIGHT") })
    }

    @Test
    fun fatigueSummaryUsesASeparateConnectiveTissueNavigationContract() {
        val state = aggregate(emptyList())
        val summary = TissueAnalysisUiMapper.summary(state)

        assertEquals("연결조직 분석", summary.title)
        assertEquals("관절·건·인대 등 연결조직에 남아 있을 상대적인 운동 부하를 확인합니다.", summary.supportingText)
        assertEquals("낮은 편", summary.status)
        assertEquals("연결조직 분석 보기", summary.actionLabel)
    }

    @Test
    fun analysisUiUsesTheRankedTopThreeThenExpandsWithoutDuplicates() {
        val state = aggregate(emptyList())
        val ui = TissueAnalysisUiMapper.map(state)

        assertEquals(state.jointComplexes.take(3).map { it.jointComplexStableKey }, ui.visibleJoints(false).map { it.key })
        assertEquals(3, ui.visibleJoints(false).size)
        assertEquals(15, ui.visibleJoints(true).size)
        assertEquals(15, ui.visibleJoints(true).map { it.key }.distinct().size)
        assertEquals(2, ui.copy(joints = ui.joints.take(2)).visibleJoints(false).size)
    }

    @Test
    fun everyDisplayedNameResolvesItsEducationalInfoAndAccessibilityLabel() {
        val ui = TissueAnalysisUiMapper.map(
            aggregate(emptyList())
        )

        ui.joints.forEach { joint ->
            assertEquals(joint.key, joint.info.stableKey)
            assertEquals("${joint.name} 정보 보기", joint.infoContentDescription)
            assertEquals(joint.info, ui.info(joint.key))
            joint.children.forEach { child ->
                assertEquals(child.info, ui.info(child.info.stableKey))
                assertTrue(child.infoContentDescription.endsWith(" 정보 보기"))
            }
        }
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

    private fun aggregate(rows: List<TissueEventResidual>): TissueCurrentState =
        TissueCurrentStateAggregator(catalog).aggregate(
            residuals = rows,
            effectiveBaselinesByUnit = catalog.loadUnits.keys.associateWith(::baseline)
        )

    private fun baseline(stableKey: String): TissueEffectiveBaseline {
        val boundaries = TissuePriorBoundaries(0.01, 10.0, 20.0, 30.0)
        val adjusted = TissueAdjustedPriorBaseline(
            stableKey,
            "test",
            TissueAdjustedPriorResult(
                boundaries = boundaries,
                multiplier = 1.0,
                bodyMassContribution = 1.0,
                habitualIntensityContribution = 1.0,
                strengthExperienceContribution = 1.0,
                racketExperienceContribution = 1.0,
                combinedExperienceContribution = 1.0,
                combinedExperienceClampApplied = false,
                normalClampApplied = false,
                hardClampApplied = false,
                missingInputs = emptySet(),
                coefficientSources = emptyMap()
            )
        )
        val history = TissueCalibrationHistory(null, null, emptyList())
        val weight = TissuePerUnitWeightPolicy.calculate(stableKey, history)
        return TissueEffectiveBaselinePolicy.mix(adjusted, null, weight)
    }

    private fun repository(): TissueRcvAssetRepository =
        TissueRcvAssetRepository.fromCsv(TissueRcvAssetFiles.required.associateWith(::asset))

    private fun asset(name: String): String = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
