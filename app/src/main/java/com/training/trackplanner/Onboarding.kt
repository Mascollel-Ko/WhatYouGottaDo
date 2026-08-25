package com.training.trackplanner

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal enum class OnboardingStep(
    @StringRes val title: Int,
    @StringRes val body: Int
) {
    HOME_PROGRAM(R.string.onboarding_home_title, R.string.onboarding_home_body),
    PLAN_APPLY(R.string.onboarding_plan_title, R.string.onboarding_plan_body),
    RECORD_OVERVIEW(R.string.onboarding_record_title, R.string.onboarding_record_body),
    ANALYSIS_TAB(R.string.onboarding_analysis_tab_title, R.string.onboarding_analysis_tab_body),
    ANALYSIS_OVERVIEW(R.string.onboarding_complete_title, R.string.onboarding_complete_body)
}

internal enum class OnboardingDecision {
    NONE,
    COMPLETED,
    SKIPPED,
    UPGRADE_SUPPRESSED
}

internal object OnboardingEligibility {
    fun shouldAutoStart(
        firstInstallTime: Long,
        lastUpdateTime: Long,
        decision: OnboardingDecision
    ): Boolean = decision == OnboardingDecision.NONE && firstInstallTime == lastUpdateTime
}

internal class OnboardingStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun shouldAutoStart(): Boolean {
        val decision = runCatching {
            OnboardingDecision.valueOf(
                preferences.getString(KEY_DECISION, OnboardingDecision.NONE.name).orEmpty()
            )
        }.getOrDefault(OnboardingDecision.NONE)
        val packageInfo = @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val eligible = OnboardingEligibility.shouldAutoStart(
            firstInstallTime = packageInfo.firstInstallTime,
            lastUpdateTime = packageInfo.lastUpdateTime,
            decision = decision
        )
        if (!eligible && decision == OnboardingDecision.NONE) {
            save(OnboardingDecision.UPGRADE_SUPPRESSED)
        }
        return eligible
    }

    fun complete() = save(OnboardingDecision.COMPLETED)

    fun skip() = save(OnboardingDecision.SKIPPED)

    private fun save(decision: OnboardingDecision) {
        preferences.edit().putString(KEY_DECISION, decision.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "onboarding"
        const val KEY_DECISION = "decision"
    }
}

@Composable
internal fun OnboardingSpotlight(
    step: OnboardingStep,
    targetBounds: Rect?,
    onTargetClick: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    if (step != OnboardingStep.ANALYSIS_OVERVIEW && targetBounds == null) return

    val density = LocalDensity.current
    val spotlightPadding = with(density) { 6.dp.toPx() }
    val gap = with(density) { 12.dp.toPx() }
    val margin = with(density) { 20.dp.toPx() }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val localTarget = targetBounds?.let { bounds ->
        Rect(
            left = bounds.left - overlayOrigin.x - spotlightPadding,
            top = bounds.top - overlayOrigin.y - spotlightPadding,
            right = bounds.right - overlayOrigin.x + spotlightPadding,
            bottom = bounds.bottom - overlayOrigin.y + spotlightPadding
        )
    }
    val title = stringResource(step.title)
    val body = stringResource(step.body)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                overlayOrigin = coordinates.positionInRoot()
                overlaySize = coordinates.size
            }
            .pointerInput(step, localTarget) {
                detectTapGestures { position ->
                    if (localTarget?.contains(position) == true) onTargetClick()
                }
            }
            .semantics { contentDescription = "$title. $body" }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.68f))
            localTarget?.let { target ->
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(target.left, target.top),
                    size = Size(target.width, target.height),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
            }
        }

        Card(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.9f)
                .onGloballyPositioned { cardSize = it.size }
                .then(
                    if (step == OnboardingStep.ANALYSIS_OVERVIEW) {
                        Modifier.align(Alignment.Center)
                    } else {
                        Modifier.offsetForCoachMark(localTarget, overlaySize, cardSize, gap, margin)
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                    when (step) {
                        OnboardingStep.RECORD_OVERVIEW -> Button(onClick = onNext) {
                            Text(stringResource(R.string.onboarding_next))
                        }
                        OnboardingStep.ANALYSIS_OVERVIEW -> Button(onClick = onNext) {
                            Text(stringResource(R.string.onboarding_get_started))
                        }
                        else -> TextButton(onClick = onTargetClick) {
                            Text(stringResource(R.string.onboarding_tap_target))
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.offsetForCoachMark(
    target: Rect?,
    overlaySize: IntSize,
    cardSize: IntSize,
    gap: Float,
    margin: Float
): Modifier = offset {
    if (target == null || overlaySize == IntSize.Zero || cardSize == IntSize.Zero) {
        IntOffset.Zero
    } else {
        val x = (target.center.x - cardSize.width / 2f)
            .coerceIn(margin, (overlaySize.width - cardSize.width - margin).coerceAtLeast(margin))
        val below = target.bottom + gap
        val y = if (below + cardSize.height <= overlaySize.height - margin) {
            below
        } else {
            (target.top - cardSize.height - gap).coerceAtLeast(margin)
        }
        IntOffset(x.roundToInt(), y.roundToInt())
    }
}
