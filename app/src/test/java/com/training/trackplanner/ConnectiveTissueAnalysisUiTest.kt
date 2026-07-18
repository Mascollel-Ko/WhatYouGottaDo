package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.tissue.TissueAdjustedPriorBaseline
import com.training.trackplanner.analysis.tissue.TissueAdjustedPriorResult
import com.training.trackplanner.analysis.tissue.TissueBaselineProvenance
import com.training.trackplanner.analysis.tissue.TissueCalibrationHistory
import com.training.trackplanner.analysis.tissue.TissueCanonicalStatus
import com.training.trackplanner.analysis.tissue.TissueCurrentState
import com.training.trackplanner.analysis.tissue.TissueCurrentStateAggregator
import com.training.trackplanner.analysis.tissue.TissueEffectiveBaseline
import com.training.trackplanner.analysis.tissue.TissueEffectiveBaselinePolicy
import com.training.trackplanner.analysis.tissue.TissueOfiSummary
import com.training.trackplanner.analysis.tissue.TissuePerUnitWeightPolicy
import com.training.trackplanner.analysis.tissue.TissuePriorBoundaries
import com.training.trackplanner.analysis.tissue.TissueRcvAssetFiles
import com.training.trackplanner.analysis.tissue.TissueRcvAssetRepository
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectiveTissueAnalysisUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun relativeStateResourcesUseExactVocabulary() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertEquals("낮은 편", context.getString(R.string.tissue_status_low))
        assertEquals("평소 범위", context.getString(R.string.tissue_status_moderate))
        assertEquals("높은 편", context.getString(R.string.tissue_status_high))
        assertEquals("매우 높은 편", context.getString(R.string.tissue_status_very_high))
        assertEquals("판단 불가", context.getString(R.string.tissue_status_unavailable))
    }

    @Test
    fun priorFooterAppearsOnceAfterDiagnosticsWithoutOldCalibrationCopy() {
        content(
            state(TissueBaselineProvenance.PRIOR_ONLY).copy(
                diagnostics = listOf("FINAL_DIAGNOSTIC")
            )
        )

        compose.onAllNodesWithText("기준 출처: 조직별 초기 기준").assertCountEquals(1)
        compose.onAllNodes(hasText("보정 중", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("56일", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("점수", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
        val diagnostic = compose.onNodeWithText("FINAL_DIAGNOSTIC").getUnclippedBoundsInRoot()
        val footer = compose.onNodeWithText("기준 출처: 조직별 초기 기준").getUnclippedBoundsInRoot()
        assertTrue(footer.top >= diagnostic.bottom)
    }

    @Test
    fun personalAndMixedFootersUseExactSingleSourcePresentation() {
        content(state(TissueBaselineProvenance.MIXED))

        compose.onAllNodesWithText("기준 출처: 조직별 초기 기준·개인 운동 기록 혼합").assertCountEquals(1)
        compose.onAllNodesWithText("조직마다 개인 기록의 반영 정도가 다를 수 있습니다.").assertCountEquals(1)
        compose.onAllNodesWithText("기준 출처: 조직별 초기 기준").assertCountEquals(0)
        compose.onAllNodesWithText("기준 출처: 개인 운동 기록").assertCountEquals(0)
    }

    @Test
    fun personalFooterAndUnavailableCompatibilityStateRenderExactly() {
        content(state(TissueBaselineProvenance.PERSONAL_ONLY, TissueCanonicalStatus.CALIBRATING))

        compose.onAllNodesWithText("기준 출처: 개인 운동 기록").assertCountEquals(1)
        compose.onNodeWithText("현재 연결조직 상태: 판단 불가").assertIsDisplayed()
    }

    @Test
    fun footerRemainsReadableAtLargeFontInDarkTheme() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                TrainingTrackPlannerTheme(darkTheme = true) {
                    Box(Modifier.width(320.dp)) {
                        ConnectiveTissueAnalysisContent(state(TissueBaselineProvenance.MIXED))
                    }
                }
            }
        }

        val bounds = compose.onNodeWithText(
            "기준 출처: 조직별 초기 기준·개인 운동 기록 혼합"
        ).getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left > 0.dp)
        assertTrue(bounds.bottom - bounds.top > 0.dp)
        assertTrue(bounds.right - bounds.left <= 320.dp)
    }

    @Test
    fun topThreeExpansionAndEducationalDialogRemainFunctional() {
        val state = completeState()
        val first = state.jointComplexes.first()
        content(state)

        compose.onAllNodesWithText("하위 조직 보기").assertCountEquals(3)
        compose.onNodeWithContentDescription("${first.nameKo} 정보 보기").performClick()
        compose.onNodeWithText(
            "이 설명은 운동 부하를 이해하기 위한 일반 정보이며 의학적 진단이 아닙니다."
        ).assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("나머지 부위 보기"))
        compose.onNodeWithText("나머지 부위 보기").performClick()
        compose.onNodeWithText("접기").assertExists()
    }

    @Test
    fun educationalDialogShowsExactlyThreeFieldsWithoutDuplicateCopy() {
        val state = completeState()
        val first = state.jointComplexes.first()
        content(state)

        compose.onNodeWithContentDescription("${first.nameKo} 정보 보기").performClick()
        compose.onAllNodesWithText("위치").assertCountEquals(1)
        compose.onAllNodesWithText("주요 기능").assertCountEquals(1)
        compose.onAllNodesWithText("주로 사용되는 동작").assertCountEquals(1)
        compose.onAllNodesWithText("표시명").assertCountEquals(0)
        compose.onAllNodesWithText("설명").assertCountEquals(0)
        compose.onNodeWithText(first.educationalInfo.anatomicalLocationKo).assertIsDisplayed()
        compose.onNodeWithText(first.educationalInfo.primaryFunctionsKo.joinToString(" ")).assertExists()
        compose.onNodeWithText(first.educationalInfo.commonLoadContextsKo.joinToString(" ")).assertExists()
        compose.onNodeWithText("닫기").performClick()
    }

    @Test
    fun parentAndChildInfoButtonsRemainAccessibleAndLongDialogScrolls() {
        val state = completeState()
        val first = state.jointComplexes.first()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                TrainingTrackPlannerTheme(darkTheme = true) {
                    Box(Modifier.width(320.dp)) {
                        ConnectiveTissueAnalysisContent(state)
                    }
                }
            }
        }

        val scrollNodesBeforeDialog = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        compose.onNodeWithContentDescription("${first.nameKo} 정보 보기").performClick()
        assertTrue(compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size > scrollNodesBeforeDialog)
        compose.onNodeWithText("닫기").performClick()

        compose.onAllNodesWithText("하위 조직 보기")[0].performClick()
        val child = first.childStates.first()
        compose.onNodeWithContentDescription("${child.loadUnitName} 정보 보기").performClick()
        assertTrue(compose.onAllNodesWithText(child.loadUnitName).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText(child.educationalInfo.anatomicalLocationKo).assertExists()
    }

    private fun content(state: TissueCurrentState) {
        compose.setContent {
            TrainingTrackPlannerTheme {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        ConnectiveTissueAnalysisContent(state)
                    }
                }
            }
        }
    }

    private fun state(
        provenance: TissueBaselineProvenance,
        status: TissueCanonicalStatus = TissueCanonicalStatus.LOW
    ) = TissueCurrentState(
        loadUnits = emptyList(),
        jointComplexes = emptyList(),
        ofiSummary = TissueOfiSummary(status, emptyList()),
        baselineProvenance = provenance,
        diagnostics = emptyList()
    )

    private fun completeState(): TissueCurrentState {
        val catalog = TissueRcvAssetRepository.fromCsv(
            TissueRcvAssetFiles.required.associateWith(::asset)
        ).catalog
        return TissueCurrentStateAggregator(catalog).aggregate(
            residuals = emptyList(),
            effectiveBaselinesByUnit = catalog.loadUnits.keys.associateWith(::baseline)
        )
    }

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
        val weight = TissuePerUnitWeightPolicy.calculate(
            stableKey,
            TissueCalibrationHistory(null, null, emptyList())
        )
        return TissueEffectiveBaselinePolicy.mix(adjusted, null, weight)
    }

    private fun asset(name: String): String = sequenceOf(
        File("src/main/assets/metadata/tissue_load_v1/$name"),
        File("app/src/main/assets/metadata/tissue_load_v1/$name")
    ).first(File::exists).readText(Charsets.UTF_8)
}
