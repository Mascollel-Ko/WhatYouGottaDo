package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.data.personalized.*
import com.training.trackplanner.localization.localizedExerciseName
import java.text.NumberFormat

@Composable
internal fun PlanningSummaryCard(model: PlanningSummaryUiModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("planning-summary")) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryText(stringResource(R.string.planning_summary_title), fontWeight = FontWeight.Bold)
            SummaryText(stringResource(behaviorLabel(model.observedState.behavior)))
            SummaryText(stringResource(compactResponse(model.planResponse)))
            SummaryText(stringResource(R.string.planning_summary_confidence, confidenceText(model.observedState.confidence)))
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("summary-toggle")) {
                SummaryText(stringResource(if (expanded) R.string.planning_summary_collapse else R.string.planning_summary_details))
            }
            if (expanded) {
                SummarySection(PlanningSummarySection.OBSERVED_STATE, R.string.planning_summary_observed) {
                    SummaryText(stringResource(behaviorLabel(model.observedState.behavior)))
                    model.observedState.style?.takeUnless { it == StrengthProgrammingStyle.NONE }?.let { SummaryText(stringResource(styleLabel(it))) }
                    SummaryText(stringResource(R.string.planning_summary_confidence, confidenceText(model.observedState.confidence)))
                }
                SummarySection(PlanningSummarySection.IDENTIFIED_NEEDS, R.string.planning_summary_needs) {
                    if (model.identifiedNeeds.isEmpty()) EmptySummary()
                    model.identifiedNeeds.forEach { need ->
                        SummaryText(stringResource(R.string.planning_summary_need, stringResource(needLabel(need.key)),
                            stringResource(signalLabel(need.signals.maxBy { it.ordinal }))))
                    }
                }
                SummarySection(PlanningSummarySection.PLAN_RESPONSE, R.string.planning_summary_response) {
                    if (model.planResponse.isEmpty()) EmptySummary()
                    model.planResponse.forEach { response ->
                        when (response) {
                            is SummaryResponse.Transition -> {
                                val structures = response.structures.map { stringResource(structureLabel(it)) }.joinToString(" / ")
                                val doses = response.doses.map { stringResource(doseLabel(it)) }.joinToString(" / ")
                                SummaryText(stringResource(R.string.planning_summary_transition,
                                    localizedExerciseName(response.stableKey, stringResource(R.string.planning_summary_exercise)), structures, doses))
                            }
                            SummaryResponse.CapacityExpansion -> SummaryText(stringResource(R.string.planning_summary_capacity_note))
                        }
                    }
                }
                SummarySection(PlanningSummarySection.FINAL_ALLOCATION, R.string.planning_summary_allocation) {
                    val a = model.finalAllocation
                    SummaryText(stringResource(R.string.planning_summary_duration, a.weeks, a.days))
                    if (a.baselineSets != null && a.targetSets != null && a.plannedSets != null) {
                        SummaryText(stringResource(R.string.planning_summary_resistance,
                            NumberFormat.getNumberInstance().format(a.baselineSets), a.targetSets, a.plannedSets))
                    }
                    if (a.badmintonTarget != null && a.badmintonPlanned != null) SummaryText(stringResource(R.string.planning_summary_badminton, a.badmintonTarget, a.badmintonPlanned))
                    if (a.athleticTarget != null && a.athleticPlanned != null) SummaryText(stringResource(R.string.planning_summary_athletic, a.athleticTarget, a.athleticPlanned))
                    a.frequencyExpansionUnits?.let { SummaryText(stringResource(R.string.planning_summary_expansion, it)) }
                }
                SummarySection(PlanningSummarySection.LIMITATIONS, R.string.planning_summary_limitations) {
                    if (model.limitations.isEmpty()) SummaryText(stringResource(R.string.planning_summary_no_limits))
                    model.limitations.forEach { limit ->
                        if (limit.kind == SummaryLimitationKind.SESSION_TIME) SummaryText(stringResource(R.string.planning_summary_limit_session_time, requireNotNull(limit.amount)))
                        else SummaryText(stringResource(limitationLabel(limit.kind)))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySection(section: PlanningSummarySection, title: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("summary-section-${section.name}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SummaryText(stringResource(title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        content()
    }
}
@Composable private fun EmptySummary() { SummaryText(stringResource(R.string.planning_summary_empty), style = MaterialTheme.typography.bodySmall) }
@Composable private fun confidenceText(value: PlanningConfidence?) = stringResource(value?.let(::confidenceLabel) ?: R.string.planning_summary_unknown)

private fun compactResponse(rows: List<SummaryResponse>): Int {
    val transitions = rows.filterIsInstance<SummaryResponse.Transition>()
    return when {
        transitions.isNotEmpty() && transitions.all { it.structures == setOf(StructureTreatment.PRESERVE) && it.doses == setOf(DoseTreatment.MAINTAIN) } -> R.string.planning_summary_response_preserve
        transitions.isNotEmpty() -> R.string.planning_summary_response_adjust
        SummaryResponse.CapacityExpansion in rows -> R.string.planning_summary_response_capacity
        else -> R.string.planning_summary_response_review
    }
}
private fun needLabel(key: SummaryNeedKey): Int = when (key) {
    is SummaryNeedKey.Movement -> movementLabel(key.movement)
    is SummaryNeedKey.Badminton -> objectiveLabel(key.objective)
    is SummaryNeedKey.Foundation -> when (key.domain) {
        FoundationNeed.RESISTANCE -> R.string.planning_summary_foundation_resistance
        FoundationNeed.BADMINTON -> R.string.planning_summary_foundation_badminton
    }
}
private fun signalLabel(signal: NeedSignal): Int = when (signal) {
    NeedSignal.ATTENTION -> R.string.planning_summary_attention
    NeedSignal.OPTIONAL_DEVELOPMENT -> R.string.planning_summary_optional_development
    NeedSignal.UNDERREPRESENTED -> R.string.planning_summary_underrepresented
    NeedSignal.ABSENT -> R.string.planning_summary_absent
    NeedSignal.DIRECT_DROP -> R.string.planning_summary_direct_drop
}
private fun limitationLabel(kind: SummaryLimitationKind): Int = when (kind) {
    SummaryLimitationKind.RECOVERY -> R.string.planning_summary_limit_recovery
    SummaryLimitationKind.TISSUE -> R.string.planning_summary_limit_tissue
    SummaryLimitationKind.UNRESOLVED_STRENGTH -> R.string.planning_summary_limit_unresolved_strength
    SummaryLimitationKind.UNRESOLVED_BADMINTON -> R.string.planning_summary_limit_unresolved_badminton
    SummaryLimitationKind.STARTING_LOAD -> R.string.planning_summary_limit_starting_load
    SummaryLimitationKind.COURT_LOAD -> R.string.planning_summary_limit_court_load
    SummaryLimitationKind.SESSION_TIME -> R.string.planning_summary_limit_session_time
    SummaryLimitationKind.SCHEDULING -> R.string.planning_summary_limit_scheduling
    SummaryLimitationKind.FATIGUE_WARNING -> R.string.planning_summary_limit_fatigue_warning
}

private fun behaviorLabel(value: ObservedTrainingBehavior): Int = when (value) {
    ObservedTrainingBehavior.HYPERTROPHY_DOMINANT -> R.string.planning_summary_behavior_hypertrophy_dominant
    ObservedTrainingBehavior.STRENGTH_DOMINANT -> R.string.planning_summary_behavior_strength_dominant
    ObservedTrainingBehavior.MIXED_STRENGTH_HYPERTROPHY -> R.string.planning_summary_behavior_mixed_strength_hypertrophy
    ObservedTrainingBehavior.GENERAL_MIXED -> R.string.planning_summary_behavior_general_mixed
    ObservedTrainingBehavior.UNKNOWN -> R.string.planning_summary_behavior_unknown
}

private fun confidenceLabel(value: PlanningConfidence): Int = when (value) {
    PlanningConfidence.LOW -> R.string.planning_summary_confidence_low
    PlanningConfidence.MODERATE -> R.string.planning_summary_confidence_moderate
    PlanningConfidence.HIGH -> R.string.planning_summary_confidence_high
}

private fun styleLabel(value: StrengthProgrammingStyle): Int = when (value) {
    StrengthProgrammingStyle.NONE -> R.string.planning_summary_style_none
    StrengthProgrammingStyle.TOP_SET_HYPERTROPHY -> R.string.planning_summary_style_top_set_hypertrophy
    StrengthProgrammingStyle.TOP_SET_BACKOFF -> R.string.planning_summary_style_top_set_backoff
    StrengthProgrammingStyle.STRAIGHT_5X5 -> R.string.planning_summary_style_straight_5x5
    StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS -> R.string.planning_summary_style_straight_strength_sets
    StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING -> R.string.planning_summary_style_madcow_like_hlm_ramping
    StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM -> R.string.planning_summary_style_heavy_light_medium
    StrengthProgrammingStyle.DUP_LIKE_UNDULATING -> R.string.planning_summary_style_dup_like_undulating
    StrengthProgrammingStyle.UNRESOLVED -> R.string.planning_summary_style_unresolved
}

private fun movementLabel(value: MovementCoverage): Int = when (value) {
    MovementCoverage.LOWER_KNEE -> R.string.planning_summary_movement_lower_knee
    MovementCoverage.POSTERIOR_CHAIN -> R.string.planning_summary_movement_posterior_chain
    MovementCoverage.HORIZONTAL_PUSH -> R.string.planning_summary_movement_horizontal_push
    MovementCoverage.HORIZONTAL_PULL -> R.string.planning_summary_movement_horizontal_pull
    MovementCoverage.VERTICAL_PUSH -> R.string.planning_summary_movement_vertical_push
    MovementCoverage.VERTICAL_PULL -> R.string.planning_summary_movement_vertical_pull
    MovementCoverage.CORE_DIRECT -> R.string.planning_summary_movement_core_direct
    MovementCoverage.CALVES -> R.string.planning_summary_movement_calves
    MovementCoverage.ARMS_BICEPS -> R.string.planning_summary_movement_arms_biceps
    MovementCoverage.ARMS_TRICEPS -> R.string.planning_summary_movement_arms_triceps
    MovementCoverage.OTHER -> R.string.planning_summary_movement_other
}

private fun objectiveLabel(value: BadmintonObjective): Int = when (value) {
    BadmintonObjective.ACCELERATION -> R.string.planning_summary_objective_acceleration
    BadmintonObjective.DECELERATION -> R.string.planning_summary_objective_deceleration
    BadmintonObjective.FOOTWORK -> R.string.planning_summary_objective_footwork
    BadmintonObjective.JUMP_LANDING -> R.string.planning_summary_objective_jump_landing
    BadmintonObjective.LUNGE_REACH -> R.string.planning_summary_objective_lunge_reach
    BadmintonObjective.REACTION -> R.string.planning_summary_objective_reaction
    BadmintonObjective.CONDITIONING -> R.string.planning_summary_objective_conditioning
    BadmintonObjective.ROTATION_GENERATION -> R.string.planning_summary_objective_rotation_generation
    BadmintonObjective.ANTI_ROTATION -> R.string.planning_summary_objective_anti_rotation
}

private fun structureLabel(value: StructureTreatment): Int = when (value) {
    StructureTreatment.PRESERVE -> R.string.planning_summary_structure_preserve
    StructureTreatment.PRESERVE_CORE_REBALANCE -> R.string.planning_summary_structure_preserve_core_rebalance
    StructureTreatment.PARTIAL_CONTINUITY -> R.string.planning_summary_structure_partial_continuity
    StructureTreatment.ROTATE_EMPHASIS -> R.string.planning_summary_structure_rotate_emphasis
}

private fun doseLabel(value: DoseTreatment): Int = when (value) {
    DoseTreatment.MAINTAIN -> R.string.planning_summary_dose_maintain
    DoseTreatment.REDUCE_SLIGHTLY -> R.string.planning_summary_dose_reduce_slightly
    DoseTreatment.REDUCE_MODERATELY -> R.string.planning_summary_dose_reduce_moderately
}

/** Full-width text rows avoid tight intrinsic sizing and keep natural wrapping at large font scales. */
@Composable
private fun SummaryText(text: String, fontWeight: FontWeight? = null, style: TextStyle = LocalTextStyle.current) {
    MaterialText(text, modifier = Modifier.fillMaxWidth(), fontWeight = fontWeight, style = style)
}
