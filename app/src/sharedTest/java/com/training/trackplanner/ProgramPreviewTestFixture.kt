package com.training.trackplanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.*
import com.training.trackplanner.data.program.legacy.*
import com.training.trackplanner.data.personalized.PersonalizedPlanningDecision
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme

internal data class PreviewLayoutCase(val weeks: Int, val days: Int, val kind: Int)

internal fun previewLegacy(case: PreviewLayoutCase): LegacyAutoSkeleton {
    val specs = (LegacyAutoRuleTables.mainExercises.values.flatten() + LegacyAutoRuleTables.pairedAccessories.values.flatten() +
        LegacyAutoRuleTables.smallPartAccessories.values.flatten() + LegacyAutoRuleTables.badmintonAccessories.values.flatten()).distinctBy { it.stableKey }
    return LegacyAutoProgramBuilder().build(
        LegacyAutoRequest("layout", LegacyAutoGoal.BADMINTON_SUPPORT, case.days, 45, emptySet(), "", .5, "AUTO", LegacyAutoPeriodizationType.AUTO, case.weeks),
        specs.map { Exercise(stableKey = it.stableKey, name = it.displayName, category = it.category) }
    )
}

/** Layout fixture, not an algorithm oracle. Extended 7/8-week and 6/7-day shapes exercise editor capacity. */
internal fun previewRecordBased(case: PreviewLayoutCase): GeneratedProgramSkeleton {
    val request = ProgramSkeletonRequest("layout", ProgramGoal.STRENGTH, case.days, 45, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, case.weeks)
    val schedule = (1..case.weeks).associateWith { (1..case.days).toSet() }
    val empty = emptyProgramSkeleton(request, schedule)
    if (case.kind == 2) return empty // User intentionally keeps active, empty days.
    val items = schedule.flatMap { (week, days) -> days.map { day ->
        ProgramSkeletonItem("w${week}d$day", week, day, 1, "squat", "스쿼트", "근력운동", 120, "3×5", 3, 5, 100.0, 0, "fixture", "PLANNER_EXPLICIT")
    } }
    return empty.copy(items = items, personalizedDecision = PersonalizedPlanningDecision(
        decisionId = "layout", protocolVersion = "layout-only", generatedAtEpochMillis = 0,
        historyCutoff = "2026-09-06", historyWindowDays = 56, planningHorizonWeeks = case.weeks,
        adaptationIntentMinWeeks = 3, adaptationIntentMaxWeeks = 8, observedTrainingBehavior = "STRENGTH_DOMINANT",
        strengthIntent = "STRENGTH", strengthIntentProvenance = "USER_EXPLICIT",
        badmintonIntent = "DISABLED", badmintonIntentProvenance = "USER_EXPLICIT",
        primaryAdaptation = "STRENGTH_SUPPORT", secondaryTargets = emptyList(),
        strengthStyle = "NONE", strengthStyleProvenance = "USER_EXPLICIT", weeklyFrequency = case.days,
        confidence = "HIGH", reasonCodes = emptyList(), reasons = emptyList(), constraints = emptyList(), metadataAuthorityVersion = "fixture"
    )).reconcileProgression(setOf("squat"))
}

@Composable
internal fun FullProgramPreviewUnderTest(case: PreviewLayoutCase, width: Int) {
    var legacy by remember(case) { mutableStateOf(if (case.kind == 0) previewLegacy(case) else null) }
    var record by remember(case) { mutableStateOf(if (case.kind != 0) previewRecordBased(case) else null) }
    val legacyContext = remember(case) { ProgressionDraftContext(legacy?.items?.firstOrNull()?.let { setOf(it.exerciseStableKey) }.orEmpty(), emptyMap()) }
    var legacyProgression by remember(case) { mutableStateOf(legacy?.let { LegacyProgressionDraft().reconcile(it, legacyContext) } ?: LegacyProgressionDraft()) }
    TrainingTrackPlannerTheme {
        Column(Modifier.width(width.dp).verticalScroll(rememberScrollState()).testTag("full-program-preview").padding(screenPadding())) {
            if (case.kind == 0) LegacyAutoSkeletonPreview(legacy!!, emptyList(), emptyMap(),
                legacyProgression, { legacyProgression = it }) { legacy = it; legacyProgression = legacyProgression.reconcile(it, legacyContext) }
            else ProgramSkeletonPreview(record!!, emptyList(), emptyMap(), setOf("squat")) { record = it }
        }
    }
}
