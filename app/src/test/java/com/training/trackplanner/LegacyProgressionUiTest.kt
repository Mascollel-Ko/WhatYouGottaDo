package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.*
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LegacyProgressionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun koreanLegacyPreviewCanChooseEverySessionBeforeSave() = choices(Locale.KOREAN)
    @Test fun englishLegacyPreviewCanChooseEverySessionBeforeSave() = choices(Locale.ENGLISH)

    private fun choices(locale: Locale) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
        // Actual frozen generator output, not a hand-created progression-bearing Legacy item.
        val frozen = previewLegacy(PreviewLayoutCase(4, 4, 0))
        val before = frozenFingerprint(frozen)
        val item = frozen.items.first()
        val progressionContext = ProgressionDraftContext(setOf(item.exerciseStableKey), emptyMap())
        val overlay = mutableStateOf(LegacyProgressionDraft().reconcile(frozen, progressionContext))
        val original = overlay.value.bindings.getValue(item.localId).sessionKey
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
                LocalDensity provides Density(density.density, 1.3f)) {
                TrainingTrackPlannerTheme {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(screenPadding())) {
                        LegacyAutoSkeletonPreview(frozen, emptyList(), emptyMap(), overlay.value, { overlay.value = it }) {
                            fail("Session configuration must not change the Legacy skeleton")
                        }
                    }
                }
            }
        }
        fun choose(tag: String) {
            compose.onNode(hasTestTag("progression-session-control") and hasAnyAncestor(hasTestTag("legacy-progression-${item.localId}")))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("progression-save").performScrollTo().performClick()
            compose.waitForIdle()
        }
        choose("progression-choice-SEPARATE")
        compose.runOnIdle {
            assertEquals(ProgressionLinkMode.SEPARATE, overlay.value.bindings.getValue(item.localId).linkMode)
            assertNotEquals(original, overlay.value.bindings.getValue(item.localId).sessionKey)
        }
        val compatible = overlay.value.sessions.first { it.key != overlay.value.bindings.getValue(item.localId).sessionKey }.key
        choose("progression-choice-$compatible")
        compose.runOnIdle {
            assertEquals(compatible, overlay.value.bindings.getValue(item.localId).sessionKey)
            assertEquals(ProgressionLinkMode.EXISTING, overlay.value.bindings.getValue(item.localId).linkMode)
        }
        choose("progression-choice-AUTO")
        compose.runOnIdle { assertEquals(ProgressionLinkMode.AUTO, overlay.value.bindings.getValue(item.localId).linkMode) }
        choose("progression-choice-OFF")
        compose.runOnIdle {
            assertEquals(ProgressionLinkMode.OFF, overlay.value.bindings.getValue(item.localId).linkMode)
            assertEquals(before, frozenFingerprint(frozen))
        }
    }
}
