package com.training.trackplanner

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
class AppExplanationContentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun approvedCopyIsStoredInResources() {
        assertEquals("이 앱이 분석하는 것 보기", text(R.string.home_app_explanation_label))
        assertEquals(
            "이 앱이 분석하는 내용 설명 열기",
            text(R.string.home_app_explanation_description)
        )
        assertEquals("앱 설명", text(R.string.app_explanation_title))
        assertEquals("운동 기록을 분석과 계획으로", text(R.string.app_explanation_hero_title))
        assertEquals(
            "WhatYouGottaDo는 근력운동과 배드민턴 기록을 바탕으로, 현재 피로와 훈련의 균형을 분석하고 다음 운동 계획을 돕습니다.",
            text(R.string.app_explanation_intro)
        )

        assertEquals(
            listOf(
                "피로를 다섯 가지로 구분합니다",
                "피로한 근육을 보여줍니다",
                "연결조직의 부하를 따로 봅니다",
                "배드민턴 훈련을 분석합니다",
                "근력운동량을 계산합니다",
                "운동 프로그램 초안을 만듭니다"
            ),
            featureTitles()
        )
        assertEquals("점수는 진단이 아닙니다", text(R.string.app_explanation_limitation_title))
        assertEquals(
            "앱의 점수는 부상확률이나 조직 손상량이 아닙니다. 내 기록 안에서 피로와 부하가 어떻게 변하는지 확인하기 위한 상대적 지표입니다.",
            text(R.string.app_explanation_limitation_primary)
        )
        assertEquals(
            "통증이나 불안정감 같은 실제 증상을 앱 점수보다 우선하세요.",
            text(R.string.app_explanation_limitation_secondary)
        )
    }

    @Test
    fun guideAndCalculationCopyStayPlainLanguageAndNonMedical() {
        assertEquals(
            listOf(
                "전체 피로 상태",
                "다섯 가지 피로 축",
                "피로한 근육",
                "연결조직 분석",
                "훈련 분산",
                "점수가 높을 때"
            ),
            guideTitles()
        )
        assertEquals(
            "기록이 적은 초기에는 개인 기준이 충분하지 않아 결과가 제한적일 수 있습니다.",
            text(R.string.analysis_guide_calibration_note)
        )
        assertEquals("연구와 제품 정책을 함께 사용합니다", text(R.string.calculation_principles_research_title))
        assertEquals("계산 방법과 한계를 공개합니다", text(R.string.calculation_principles_transparency_title))
        assertEquals("의학적 정확성을 주장하지 않습니다", text(R.string.calculation_principles_medical_title))
        assertEquals(
            listOf(
                "전체 피로 상태 계산",
                "다섯 가지 피로 축 계산",
                "연결조직 부하와 회복 추정",
                "배드민턴 훈련 분류와 운동량",
                "근력운동 분류와 운동량",
                "자동 프로그램 생성 규칙"
            ),
            protocolFamilies()
        )

        val guideCopy = guideTitles().joinToString(" ") + " " +
            text(R.string.analysis_guide_intro) + " " +
            text(R.string.analysis_guide_calibration_note)
        listOf("OFI", "MSCP-DI", "PCHIP", "stableKey", "protocol ID").forEach {
            assertFalse(guideCopy.contains(it, ignoreCase = true))
        }
        assertTrue(text(R.string.calculation_principles_medical_body).contains("뜻하지 않습니다"))
    }

    @Test
    fun routesHaveStableNamesAndParentNavigation() {
        assertEquals("app_explanation", AppInfoRoute.AppExplanation.routeName)
        assertEquals("analysis_guide", AppInfoRoute.AnalysisGuide.routeName)
        assertEquals("calculation_principles", AppInfoRoute.CalculationPrinciples.routeName)
        assertEquals(null, AppInfoRoute.AppExplanation.parent)
        assertEquals(AppInfoRoute.AppExplanation, AppInfoRoute.AnalysisGuide.parent)
        assertEquals(AppInfoRoute.AppExplanation, AppInfoRoute.CalculationPrinciples.parent)
    }

    @Test
    fun publicProtocolIntentUsesOneConfiguredUrl() {
        var openedIntent: Intent? = null

        assertTrue(launchPublicProtocolIndex { openedIntent = it })
        assertEquals(Intent.ACTION_VIEW, openedIntent?.action)
        assertEquals(PUBLIC_PROTOCOL_INDEX_URL, openedIntent?.dataString)
        assertEquals(
            "https://github.com/Mascollel-Ko/WhatYouGottaDo/tree/main/docs/protocols",
            PUBLIC_PROTOCOL_INDEX_URL
        )
    }

    @Test
    fun missingBrowserDoesNotCrash() {
        assertFalse(
            launchPublicProtocolIndex {
                throw ActivityNotFoundException("No browser")
            }
        )
    }

    @Test
    fun homeEntryStaysSingleCompactAndOutsideProfileCard() {
        val homeSource = source("app/src/main/java/com/training/trackplanner/HomeScreen.kt")
        val explanationSource =
            source("app/src/main/java/com/training/trackplanner/AppExplanationScreens.kt")

        assertEquals(1, Regex("R\\.string\\.home_app_explanation_label").findAll(homeSource).count())
        assertTrue(
            homeSource.indexOf("CompactHomeActionGroup(") <
                homeSource.indexOf("TodaySummaryCard(summary)")
        )
        assertTrue(
            homeSource.indexOf("TodaySummaryCard(summary)") <
                homeSource.indexOf("InitialProfileCard(")
        )
        assertFalse(
            homeSource.substringAfter("private fun InitialProfileCard(")
                .contains("home_app_explanation_label")
        )
        assertFalse(homeSource.contains("WhatYouGottaDo는 근력운동과 배드민턴 기록"))
        assertFalse(explanationSource.contains("앱의 점수는 부상확률이나 조직 손상량"))
    }

    private fun text(id: Int): String = context.getString(id)

    private fun featureTitles(): List<String> = listOf(
        R.string.app_explanation_feature_fatigue_title,
        R.string.app_explanation_feature_muscles_title,
        R.string.app_explanation_feature_tissue_title,
        R.string.app_explanation_feature_badminton_title,
        R.string.app_explanation_feature_strength_title,
        R.string.app_explanation_feature_program_title
    ).map(::text)

    private fun guideTitles(): List<String> = listOf(
        R.string.analysis_guide_overall_title,
        R.string.analysis_guide_axes_title,
        R.string.analysis_guide_muscles_title,
        R.string.analysis_guide_tissue_title,
        R.string.analysis_guide_distribution_title,
        R.string.analysis_guide_high_score_title
    ).map(::text)

    private fun protocolFamilies(): List<String> = listOf(
        R.string.protocol_family_overall_fatigue,
        R.string.protocol_family_fatigue_axes,
        R.string.protocol_family_connective_tissue,
        R.string.protocol_family_badminton,
        R.string.protocol_family_strength,
        R.string.protocol_family_program_builder
    ).map(::text)

    private fun source(relativePath: String): String {
        return sequenceOf(File(relativePath), File("..", relativePath))
            .first(File::isFile)
            .readText()
    }
}
