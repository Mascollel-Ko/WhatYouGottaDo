package com.training.trackplanner

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.inspector.WindowInspector
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.training.trackplanner.data.*
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.io.File
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
    /** Actual Legacy preview and modal sheet; production semantic click actions, not a settings mock. */
    @Test fun legacyPreSaveSessionChoicesOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (language in listOf(Locale.KOREAN, Locale.ENGLISH)) {
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(language) })
            val frozen = previewLegacy(PreviewLayoutCase(4, 4, 0))
            val item = frozen.items.first()
            val overlay = mutableStateOf(LegacyProgressionDraft().reconcile(frozen, ProgressionDraftContext(setOf(item.exerciseStableKey), emptyMap())))
            val original = overlay.value.bindings.getValue(item.localId).sessionKey
            fun nodes() = WindowInspector.getGlobalWindowViews().filter { it.visibility == View.VISIBLE }
                .mapNotNull(::findOwner).flatMap { allNodes(it.unmergedRootSemanticsNode) }
            fun tagged(tag: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            fun awaitUi(condition: () -> Boolean) {
                val deadline = SystemClock.uptimeMillis() + 10000
                do {
                    instrumentation.waitForIdleSync()
                    var success = false
                    instrumentation.runOnMainSync { success = condition() }
                    if (success) return
                    SystemClock.sleep(20)
                } while (SystemClock.uptimeMillis() < deadline)
                fail("Legacy session UI timeout: ${language.language}")
            }
            fun click(tag: String) {
                awaitUi { tagged(tag) != null }
                instrumentation.runOnMainSync { assertTrue(tagged(tag)!!.config[SemanticsActions.OnClick].action!!.invoke()) }
            }
            fun openControl() {
                awaitUi { tagged("legacy-progression-${item.localId}") != null }
                instrumentation.runOnMainSync {
                    val control = allNodes(tagged("legacy-progression-${item.localId}")!!).single {
                        it.config.getOrNull(SemanticsProperties.TestTag) == "progression-session-control"
                    }
                    assertTrue(control.config[SemanticsActions.OnClick].action!!.invoke())
                }
                awaitUi { tagged("progression-choice-AUTO") != null }
            }
            ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    // Dialog windows inherit the Activity resources, not the preview's LocalContext.
                    @Suppress("DEPRECATION")
                    activity.resources.updateConfiguration(localized.resources.configuration, activity.resources.displayMetrics)
                    activity.setContent {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
                            LocalDensity provides Density(density.density, 1.3f)) {
                            TrainingTrackPlannerTheme {
                                Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(screenPadding())) {
                                        LegacyAutoSkeletonPreview(frozen, emptyList(), emptyMap(), overlay.value, { overlay.value = it }) {
                                            fail("Session selection changed the frozen output")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                fun choose(tag: String, mode: ProgressionLinkMode) {
                    openControl(); click(tag); click("progression-save")
                    awaitUi { overlay.value.bindings.getValue(item.localId).linkMode == mode && tagged("progression-save") == null }
                }
                choose("progression-choice-SEPARATE", ProgressionLinkMode.SEPARATE)
                assertNotEquals(original, overlay.value.bindings.getValue(item.localId).sessionKey)
                val compatible = overlay.value.sessions.first { it.key != overlay.value.bindings.getValue(item.localId).sessionKey }.key
                choose("progression-choice-$compatible", ProgressionLinkMode.EXISTING)
                assertEquals(compatible, overlay.value.bindings.getValue(item.localId).sessionKey)
                choose("progression-choice-AUTO", ProgressionLinkMode.AUTO)
                choose("progression-choice-OFF", ProgressionLinkMode.OFF)
                openControl()
                instrumentation.runOnMainSync {
                    val expected = localized.getString(R.string.progression_auto_connect)
                    assertTrue("Modal locale must match the preview", nodes().any { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == expected } == true
                    })
                }
                instrumentation.uiAutomation.waitForIdle(100, 5000)
                SystemClock.sleep(350) // Wait for the modal's SurfaceFlinger presentation, not just semantics.
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val target = File(context.getExternalFilesDir(null), "legacy-session-ui/${language.language}-320-1.3.png")
                target.parentFile!!.mkdirs()
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

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
                        val density = context.resources.displayMetrics.density
                        val dayLabels = if (language.language == "ko") listOf("월", "화", "수", "목", "금", "토", "일")
                            else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
                        checkRow(tagged, "program-week", (1..weeks).toList(), origin, dp * density, density)
                        checkRow(tagged, "program-day-toggle", (1..7).toList(), origin, dp * density, density)
                        for (week in 1..weeks) checkLabel(tagged, "program-week-$week",
                            if (language.language == "ko") "${week}주" else "W$week", origin, dp * density)
                        for (day in 1..7) checkLabel(tagged, "program-day-toggle-$day",
                            dayLabels[day - 1], origin, dp * density)
                        val active = if (kind == 0) previewLegacy(case).weekDaySchedule.getValue(1).sorted() else (1..days).toList()
                        checkRow(tagged, "program-day-view", active, origin, dp * density, density)
                        for (day in active) checkLabel(tagged, "program-day-view-$day",
                            dayLabels[day - 1], origin, dp * density)
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
    private fun checkRow(nodes: Map<String, SemanticsNode>, tag: String, values: List<Int>, origin: Float, width: Float, density: Float) {
        val row = nodes.getValue("$tag-row")
        assertEquals("$tag must be one line", 48f * density, row.size.height.toFloat(), 1f)
        assertTrue("$tag row bounds", row.positionInRoot.x >= origin - 1 && row.positionInRoot.x + row.size.width <= origin + width + 1)
        var right = row.positionInRoot.x
        val firstWidth = nodes.getValue("$tag-${values.first()}").size.width
        values.forEach { value ->
            val cell = nodes.getValue("$tag-$value")
            assertEquals("$tag cell wrapped", row.positionInRoot.y, cell.positionInRoot.y, 1f)
            assertEquals("$tag cell height", row.size.height, cell.size.height)
            assertTrue("$tag cell overlaps/exceeds row", cell.positionInRoot.x >= right - 1 &&
                cell.positionInRoot.x + cell.size.width <= row.positionInRoot.x + row.size.width + 1)
            assertTrue("$tag unequal widths", kotlin.math.abs(cell.size.width - firstWidth) <= 1)
            assertTrue("$tag excessive cell width", cell.size.width <= 40f * density + 1)
            right = cell.positionInRoot.x + cell.size.width
        }
    }
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
        val cell = nodes.getValue(tag)
        assertTrue("$tag label exceeds cell", node.positionInRoot.x >= cell.positionInRoot.x - 1 &&
            node.positionInRoot.x + node.size.width <= cell.positionInRoot.x + cell.size.width + 1)
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
