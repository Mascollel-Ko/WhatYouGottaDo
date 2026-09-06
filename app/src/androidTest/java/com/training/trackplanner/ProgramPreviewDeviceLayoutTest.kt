package com.training.trackplanner

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.training.trackplanner.localization.LocalizedPresentation
import java.io.File
import java.time.DayOfWeek
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Full native preview matrix. No Espresso InputManager dependency (removed by Android 17). */
@RunWith(AndroidJUnit4::class)
class ProgramPreviewDeviceLayoutTest {
    @Test fun full1080ProgramPreviewMatrix() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val locales = listOf(Locale.KOREAN, Locale.ENGLISH).associateWith {
            context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(it) })
        }
        val current = mutableStateOf(PreviewLayoutCase(3, 3, 0))
        val width = mutableStateOf(320)
        val font = mutableStateOf(1f)
        val locale = mutableStateOf(Locale.KOREAN)
        val frame = mutableStateOf(0)
        val ready = AtomicReference(CountDownLatch(1))
        val decor = AtomicReference<View>()
        var count = 0
        ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                decor.set(activity.window.decorView)
                activity.setContent {
                    val density = LocalDensity.current
                    val localized = locales.getValue(locale.value)
                    LaunchedEffect(frame.value) { repeat(3) { withFrameNanos { } }; ready.get().countDown() }
                    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
                        LocalDensity provides Density(density.density, font.value)) {
                        Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                            key(current.value, width.value, font.value, locale.value) {
                                FullProgramPreviewUnderTest(current.value, width.value)
                            }
                        }
                    }
                }
            }
            assertTrue(ready.get().await(20, TimeUnit.SECONDS))
            for (language in locales.keys) for (dp in listOf(320, 360, 411)) for (scale in listOf(1f, 1.3f))
                for (weeks in 3..8) for (days in 3..7) for (kind in 0..2) {
                    val case = PreviewLayoutCase(weeks, days, kind)
                    ready.set(CountDownLatch(1))
                    instrumentation.runOnMainSync { current.value = case; width.value = dp; font.value = scale; locale.value = language; frame.value++ }
                    assertTrue("$language $dp $scale $case render timeout", ready.get().await(20, TimeUnit.SECONDS))
                    instrumentation.waitForIdleSync()
                    instrumentation.runOnMainSync {
                        val nodes = allNodes(requireNotNull(findOwner(decor.get())).unmergedRootSemanticsNode)
                        val tagged = nodes.mapNotNull { node -> node.config.getOrNull(SemanticsProperties.TestTag)?.let { it to node } }.toMap()
                        val origin = tagged.getValue("full-program-preview").positionInRoot.x
                        val localized = locales.getValue(language)
                        val density = context.resources.displayMetrics.density
                        for (week in 1..weeks) checkLabel(tagged, "program-week-$week",
                            localized.getString(R.string.program_week_number, week), origin, dp * density)
                        for (day in 1..7) checkLabel(tagged, "program-day-toggle-$day",
                            LocalizedPresentation.weekday(localized, DayOfWeek.of(day)), origin, dp * density)
                        val active = if (kind == 0) previewLegacy(case).weekDaySchedule.getValue(1).sorted() else (1..days).toList()
                        for (day in active) checkLabel(tagged, "program-day-view-$day",
                            LocalizedPresentation.weekday(localized, DayOfWeek.of(day)), origin, dp * density)
                    }
                    if (weeks == 8 && days == 7) {
                        instrumentation.uiAutomation.waitForIdle(100, 5000)
                        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        val target = File(context.getExternalFilesDir(null), "program-preview-isolation/${language.language}-$dp-$scale-$kind.png")
                        target.parentFile!!.mkdirs()
                        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                    count++
                }
        }
        assertEquals(1080, count)
    }

    private fun allNodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::allNodes)
    private fun findOwner(view: View): SemanticsOwner? {
        view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }?.let {
            return it.invoke(view) as SemanticsOwner
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) findOwner(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun checkLabel(nodes: Map<String, SemanticsNode>, tag: String, expected: String, origin: Float, width: Float) {
        val node = nodes.getValue("$tag-label")
        assertEquals(tag, expected, node.config[SemanticsProperties.Text].joinToString("") { it.text })
        val x = node.positionInRoot.x - origin
        assertTrue("$tag outside width", x >= -1 && x + node.size.width <= width + 1)
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        val layout = results.single()
        assertEquals("$tag wrapped", 1, layout.lineCount)
        assertFalse("$tag ellipsized", layout.isLineEllipsized(0))
        assertTrue("$tag horizontal glyph clipping", layout.getLineLeft(0) >= -1 && layout.getLineRight(0) <= layout.size.width + 1)
        assertTrue("$tag vertical glyph clipping", layout.getLineTop(0) >= -1 && layout.getLineBottom(0) <= layout.size.height + 1)
        assertTrue("$tag missing usable control", nodes.getValue(tag).size.height >= node.size.height)
    }
}
