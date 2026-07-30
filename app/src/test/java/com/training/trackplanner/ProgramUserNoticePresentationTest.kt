package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.ProgramOptimizationTrace
import com.training.trackplanner.data.ProgramUserNotice
import com.training.trackplanner.data.ProgramUserNoticeCode
import com.training.trackplanner.data.programNoticeForOptimizationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramUserNoticePresentationTest {
    @Test
    fun selectedMainRepairUsesCleanLocalizedTextAndKeepsDiagnosticCodeInternal() {
        val action = "REOPEN_FILLER_SLOT_FOR_SELECTED_MAIN"
        val notice = programNoticeForOptimizationAction(action)
        val text = context(Locale.KOREAN).programUserNoticeText(notice)
        val trace = ProgramOptimizationTrace(1, 70, 78, true, listOf(action))

        assertEquals(
            "근력 메인 운동이 보조 운동에 밀리지 않도록 구성을 보정했습니다.",
            text
        )
        assertFalse(MOJIBAKE.containsMatchIn(text))
        assertFalse(text.contains(action))
        assertEquals(action, trace.actions.single())
    }

    @Test
    fun constraintNoticesPreserveCountsInKoreanAndEnglish() {
        val excluded = ProgramUserNotice(
            ProgramUserNoticeCode.EXCLUDED_EXERCISES_APPLIED,
            count = 3
        )
        val preferred = ProgramUserNotice(
            ProgramUserNoticeCode.PREFERRED_EXERCISES_INCLUDED,
            selectedCount = 2,
            totalCount = 4
        )

        assertEquals(
            "지정한 제외 운동 3개를 프로그램에서 제외했습니다.",
            context(Locale.KOREAN).programUserNoticeText(excluded)
        )
        assertEquals(
            "선호 운동 4개 중 2개를 프로그램에 포함했습니다.",
            context(Locale.KOREAN).programUserNoticeText(preferred)
        )
        assertEquals(
            "3 selected exercises were excluded from the program.",
            context(Locale.ENGLISH).programUserNoticeText(excluded)
        )
        assertEquals(
            "2 of 4 preferred exercises were included in the program.",
            context(Locale.ENGLISH).programUserNoticeText(preferred)
        )
    }

    @Test
    fun everyUserNoticeHasKoreanAndEnglishPresentationWithoutDiagnosticIdentifiers() {
        ProgramUserNoticeCode.entries.forEach { code ->
            val notice = ProgramUserNotice(code, count = 2, selectedCount = 1, totalCount = 2)
            listOf(Locale.KOREAN, Locale.ENGLISH).forEach { locale ->
                val text = context(locale).programUserNoticeText(notice)
                assertTrue("$code/$locale is blank", text.isNotBlank())
                assertFalse("$code leaked a diagnostic: $text", RAW_DIAGNOSTIC.containsMatchIn(text))
                assertFalse("$code contains mojibake: $text", MOJIBAKE.containsMatchIn(text))
            }
        }
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) }
        )
    }

    private companion object {
        val RAW_DIAGNOSTIC = Regex("\\b(?:PROGRAM|REOPEN|SOFTEN)_[A-Z0-9_]+\\b")
        val MOJIBAKE = Regex("洹|硫|\\?대|\\?꾩|蹂|덉")
    }
}
