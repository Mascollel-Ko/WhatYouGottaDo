package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataTranslatorTest {
    private val korean by lazy { MetadataTranslator.from(context(Locale.KOREAN)) }
    private val english by lazy { MetadataTranslator.from(context(Locale.ENGLISH)) }

    @Test
    fun fieldKeysRouteCanonicalTokensThroughExistingCatalogue() {
        assertEquals("힌지", korean.translate("exercise.movementPattern", "HINGE"))
        assertEquals("높음", korean.translate("exercise.axialLoadLevel", "HIGH"))
        assertEquals("양측", korean.translate("exercise.laterality", "BILATERAL"))
        assertEquals("근력", korean.translate("exercise.movementCategory", "STRENGTH"))
        assertEquals("밀기", korean.translate("exercise.forceType", "PUSH"))
        assertEquals("매우 김", korean.translate("runtime.recoveryDurationClass", "VERY_LONG"))
        assertEquals(
            "일반 근력 보조",
            korean.translate("runtime.badmintonTransferType", "GENERAL_STRENGTH_SUPPORTIVE")
        )
    }

    @Test
    fun tokenSetsTranslateEachCanonicalValue() {
        assertEquals(
            listOf("광배근", "상완삼두근"),
            korean.translateTokens("exercise.primaryMuscles", listOf("TRICEPS", "LAT"))
        )
        assertEquals(
            listOf("바벨", "덤벨"),
            korean.translateTokens("exercise.equipment", listOf("DUMBBELL", "BARBELL"))
        )
        assertEquals(
            "코어 브레이싱 부하 · 회전 파워 부하",
            korean.translate(
                "runtime.secondaryStressTags",
                "ROTATION_POWER_LOAD|CORE_BRACING_LOAD"
            )
        )
    }

    @Test
    fun hybridFieldsUseExactCatalogueMatchOrOriginalUserText() {
        assertEquals("근력운동", korean.translate("exercise.category", "근력운동"))
        assertEquals("근력운동", english.translate("exercise.category", "근력운동"))
        assertEquals("가슴/삼두근", korean.translate("exercise.detail1", "가슴/삼두근"))
        assertEquals("광배근", korean.translate("exercise.detail2", "광배근"))
        val custom = "내 커스텀 폭발력 운동"
        assertEquals(custom, korean.translate("exercise.category", custom))
        assertEquals("근력운동 추가", korean.translate("exercise.category", "근력운동 추가"))
    }

    @Test
    fun typedAndNonDisplayFieldsBypassMetadataTokenLookup() {
        assertEquals("1,234.5", english.translate("exercise.systemicLoadWeight", "1234.5"))
        assertEquals("120초", korean.translate("exercise.defaultRestSeconds", "120"))
        assertEquals("예", korean.translate("exercise.estimated1RmEligible", "true"))
        assertEquals("No", english.translate("exercise.estimated1RmEligible", "false"))
        assertNull(korean.translate("exercise.archivedAt", "1700000000"))
        assertNull(korean.translate("identity.stableKey", "ex_123"))
    }

    @Test
    fun editorOptionsReturnCanonicalCodesAndPreserveAliases() {
        val option = korean.option("runtime.planningEligibility", "PROGRAM_SELECTABLE")
        assertEquals("PROGRAM_SELECTABLE", option.code)
        assertEquals("프로그램에 사용 가능", option.label)
        assertTrue(option.matches("Available"))
        assertTrue(option.matches("프로그램"))
    }

    @Test
    fun unknownCanonicalCodeUsesSafeLabelWithoutRawCode() {
        val value = korean.translate("runtime.programSlot", "FUTURE_SLOT_CODE").orEmpty()
        assertEquals("등록되지 않은 값", value)
        assertFalse(value.contains("FUTURE_SLOT_CODE"))
    }

    @Test
    fun exerciseReferencesAndOrdinaryFreeTextRemainUnchanged() {
        assertEquals("벤치프레스", korean.translate("exercise.name", "벤치프레스"))
        assertEquals(
            "사용자가 작성한 설명",
            korean.translate("exercise.description", "사용자가 작성한 설명")
        )
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) }
        )
    }
}
