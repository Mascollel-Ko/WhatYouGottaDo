package com.training.trackplanner.localization

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

internal enum class AppLanguage(val languageTag: String) {
    KOREAN("ko"),
    ENGLISH("en")
}

internal object AppLanguageRegistry {
    fun explicitLanguage(): AppLanguage? =
        AppCompatDelegate.getApplicationLocales().firstSupportedLanguage()

    fun effectiveLanguage(configuration: Configuration): AppLanguage =
        configuration.locales[0].toLanguageTag().toAppLanguage() ?: AppLanguage.KOREAN

    fun select(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.languageTag)
        )
    }

    internal fun effectiveLanguage(locales: LocaleListCompat): AppLanguage =
        locales.firstSupportedLanguage() ?: AppLanguage.KOREAN
}

private fun LocaleListCompat.firstSupportedLanguage(): AppLanguage? =
    (0 until size()).firstNotNullOfOrNull { index -> get(index)?.toLanguageTag()?.toAppLanguage() }

private fun String.toAppLanguage(): AppLanguage? = when (Locale.forLanguageTag(this).language) {
    Locale.KOREAN.language -> AppLanguage.KOREAN
    Locale.ENGLISH.language -> AppLanguage.ENGLISH
    else -> null
}
