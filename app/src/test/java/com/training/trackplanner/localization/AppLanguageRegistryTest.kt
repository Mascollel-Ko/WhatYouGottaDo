package com.training.trackplanner.localization

import androidx.core.os.LocaleListCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageRegistryTest {
    @Test
    fun emptyAndUnsupportedLocalesFallBackToKoreanBaseResources() {
        assertEquals(
            AppLanguage.KOREAN,
            AppLanguageRegistry.effectiveLanguage(LocaleListCompat.getEmptyLocaleList())
        )
        assertEquals(
            AppLanguage.KOREAN,
            AppLanguageRegistry.effectiveLanguage(LocaleListCompat.forLanguageTags("ja"))
        )
    }

    @Test
    fun localePriorityListUsesFirstSupportedLocale() {
        assertEquals(
            AppLanguage.ENGLISH,
            AppLanguageRegistry.effectiveLanguage(LocaleListCompat.forLanguageTags("ja,en,ko"))
        )
        assertEquals(
            AppLanguage.KOREAN,
            AppLanguageRegistry.effectiveLanguage(LocaleListCompat.forLanguageTags("ko,en"))
        )
    }
}
