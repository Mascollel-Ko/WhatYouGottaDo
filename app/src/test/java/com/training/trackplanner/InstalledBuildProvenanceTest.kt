package com.training.trackplanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalledBuildProvenanceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun isoUtcIsAlwaysDisplayedInKst() {
        assertEquals("2026.10.29 15:57", formatBuildTimestampKst("2026-10-29T06:57:02Z"))
    }

    @Test
    fun offsetCommitTimeResolvesToSameKst() {
        assertEquals("2026.10.29 15:57", formatBuildTimestampKst("2026-10-29T08:57:02+02:00"))
    }

    @Test
    fun unknownTimeDoesNotFabricateBuildDate() {
        assertNull(formatBuildTimestampKst(""))
        assertNull(formatBuildTimestampKst("unknown"))
        assertNull(formatBuildTimestampKst("2026-99-99T00:00:00Z"))
    }

    @Test
    fun installedBuildHasReadableLabelsInBothLanguages() {
        val ko = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply { setLocale(Locale.KOREAN) }
        )
        val en = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        )
        assertTrue(ko.getString(R.string.home_build_ci_timestamp, "2026.10.29 15:57").contains("CI"))
        assertTrue(en.getString(R.string.home_build_ci_timestamp, "2026.10.29 15:57").contains("KST"))
        assertTrue(ko.getString(R.string.home_build_version_revision, "0.5.1.5", "abcd1234").contains("abcd1234"))
    }

    @Test
    fun footerIsLastHomeListItemAndCiTimestampIsCapturedBeforeTests() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { it.resolve("app/build.gradle.kts").isFile }
            ?: error("repository root not found")
        val home = root.resolve("app/src/main/java/com/training/trackplanner/HomeScreen.kt").readText()
        val workflow = root.resolve(".github/workflows/android-debug-build.yml").readText()
        assertTrue(home.indexOf("InitialProfileCard(") < home.indexOf("HomeBuildProvenanceFooter()"))
        assertTrue(workflow.indexOf("Stamp CI build start for installed APK") <
            workflow.indexOf("Run debug unit tests"))
        assertTrue(workflow.contains("APP_BUILD_TIMESTAMP_UTC="))
        assertTrue(root.resolve("app/build.gradle.kts").readText().contains("BUILD_TIMESTAMP_UTC"))
    }
}
