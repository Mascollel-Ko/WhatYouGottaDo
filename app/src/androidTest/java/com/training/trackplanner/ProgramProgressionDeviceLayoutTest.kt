package com.training.trackplanner

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
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

/** Native device captures complement the text-layout assertions in ProgramProgressionLayoutTest.
 * Platform instrumentation avoids Espresso's removed InputManager.getInstance API on Android 17. */
@RunWith(AndroidJUnit4::class)
class ProgramProgressionDeviceLayoutTest {
    @Test fun captureCompactKoreanWidthsAndFonts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val korean = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.KOREAN) })
        val width = mutableStateOf(320)
        val font = mutableStateOf(1f)
        val settings = mutableStateOf(false)
        val frame = mutableStateOf(0)
        val ready = AtomicReference(CountDownLatch(1))
        val bounds = AtomicReference(Rect.Zero)
        val suggestion = ProgressionSuggestion(applicationId = "app", trackId = "track", sourceEntryId = 1, targetEntryId = 2,
            previousActualKg = 140.0, currentPlanKg = 145.0, suggestedKg = 142.5, judgmentRpe = 7.0,
            direction = ProgressionDirection.INCREASE, reasons = "TARGET_COMPLETED_MANAGEABLE_EFFORT", evidenceHash = "hash", rule = ProgressionRule())
        ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val density = LocalDensity.current
                    LaunchedEffect(frame.value) { repeat(3) { withFrameNanos { } }; ready.get().countDown() }
                    CompositionLocalProvider(LocalContext provides korean, LocalConfiguration provides korean.resources.configuration,
                        LocalDensity provides Density(density.density, font.value)) {
                        TrainingTrackPlannerTheme {
                            Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                                Box(Modifier.width(width.value.dp).onGloballyPositioned { bounds.set(it.boundsInWindow()) }) {
                                    if (settings.value) ProgressionSettingsContent(
                                        ProgramProgressionTrack(id = "track", programStableKey = "program", exerciseStableKey = "squat", label = "스쿼트 · 5회 · 메인", mode = ProgressionMode.CUSTOM),
                                        ProgramProgressionItem(1, trackId = "track", signature = ProgressionSignature("squat", 3, "5|5|5", 140.0, null, null)), emptyList()) { _, _, _, _, _ -> }
                                    else ProgressionSuggestionContent(suggestion) { _, _ -> }
                                }
                            }
                        }
                    }
                }
            }
            assertTrue(ready.get().await(15, TimeUnit.SECONDS))
            for (dp in listOf(320, 360, 411)) for (scale in listOf(1f, 1.3f)) {
                ready.set(CountDownLatch(1))
                instrumentation.runOnMainSync { width.value = dp; font.value = scale; frame.value++ }
                assertTrue(ready.get().await(15, TimeUnit.SECONDS))
                assertTrue(bounds.get().width / context.resources.displayMetrics.density <= dp + 1)
                screenshot("suggestion-$dp-$scale.png")
            }
            ready.set(CountDownLatch(1))
            instrumentation.runOnMainSync { width.value = 320; font.value = 1.3f; settings.value = true; frame.value++ }
            assertTrue(ready.get().await(15, TimeUnit.SECONDS))
            screenshot("settings-320-1.3-top.png")
            repeat(3) { swipeUp(bounds.get()) }
            instrumentation.waitForIdleSync()
            screenshot("settings-320-1.3-bottom.png")
        }
    }

    private fun swipeUp(bounds: Rect) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        val x = bounds.center.x
        for (i in 0..20) {
            val action = if (i == 0) MotionEvent.ACTION_DOWN else if (i == 20) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE
            val y = bounds.bottom - 60 - (bounds.height - 160) * i / 20
            MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).also {
                instrumentation.sendPointerSync(it); it.recycle()
            }
            SystemClock.sleep(16)
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(100, 5000)
        // Choreographer idle precedes SurfaceFlinger presentation on a cold first frame.
        SystemClock.sleep(250)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "progression-layout").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
