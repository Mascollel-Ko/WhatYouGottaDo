package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.tissue.TissueAnalysisUiMapper
import com.training.trackplanner.analysis.tissue.TissueBaselineProvenance
import com.training.trackplanner.analysis.tissue.TissueBaselineProvenanceUi
import com.training.trackplanner.analysis.tissue.TissueCanonicalStatus
import com.training.trackplanner.analysis.tissue.TissueCurrentState
import com.training.trackplanner.analysis.tissue.TissueEducationalInfo
import com.training.trackplanner.analysis.tissue.TissueExerciseContribution
import com.training.trackplanner.localization.localizedExerciseName
import com.training.trackplanner.localization.localizedTissueEducation

@Composable
internal fun ConnectiveTissueSummaryCard(
    state: TissueCurrentState?,
    onClick: () -> Unit
) {
    val ui = TissueAnalysisUiMapper.summary(state)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.tissue_analysis_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.tissue_analysis_supporting_text),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ui.status?.let {
                Text(
                    stringResource(R.string.tissue_analysis_status_summary, tissueStatusLabel(it)),
                    fontWeight = FontWeight.SemiBold
                )
            }
            localizedTissueNames(ui.topAreas).takeIf(String::isNotBlank)?.let { names ->
                Text(stringResource(R.string.tissue_analysis_top_areas, names), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClick) {
                Text(stringResource(R.string.tissue_analysis_action))
            }
        }
    }
}

@Composable
internal fun ConnectiveTissueAnalysisContent(state: TissueCurrentState?) {
    if (state == null) {
        InfoCard(stringResource(R.string.tissue_analysis_calculating))
        return
    }
    val ui = TissueAnalysisUiMapper.map(state)
    var expandedJoint by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllJoints by rememberSaveable { mutableStateOf(false) }
    var selectedInfoKey by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.tissue_analysis_current_status, tissueStatusLabel(ui.status)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(
                        R.string.tissue_analysis_top_areas,
                        localizedTissueNames(ui.topAreas).ifBlank {
                            stringResource(R.string.tissue_analysis_no_area)
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.tissue_analysis_not_diagnosis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ui.visibleJoints(showAllJoints).forEach { joint ->
            val jointName = localizedTissueEducation(joint.info).name
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(jointName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            TissueInfoButton(
                                contentDescription = stringResource(
                                    R.string.tissue_analysis_info_description,
                                    jointName
                                ),
                                onClick = { selectedInfoKey = joint.info.stableKey }
                            )
                        }
                        Text(
                            tissueStatusLabel(joint.status),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        stringResource(
                            R.string.tissue_analysis_high_children,
                            joint.highChildCount,
                            joint.highestChild?.let { localizedTissueEducation(it).name }
                                ?: stringResource(R.string.tissue_analysis_observing)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(
                            R.string.tissue_analysis_primary_contributors,
                            localizedContributorNames(joint.contributors).ifBlank {
                                stringResource(R.string.tissue_analysis_no_contributor)
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = {
                            expandedJoint = if (expandedJoint == joint.key) null else joint.key
                        }
                    ) {
                        Text(
                            stringResource(
                                if (expandedJoint == joint.key) {
                                    R.string.tissue_analysis_collapse
                                } else {
                                    R.string.tissue_analysis_expand_children
                                }
                            )
                        )
                    }
                    if (expandedJoint == joint.key) {
                        joint.children.forEachIndexed { index, child ->
                            val childName = localizedTissueEducation(child.info).name
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        childName,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TissueInfoButton(
                                        contentDescription = stringResource(
                                            R.string.tissue_analysis_info_description,
                                            childName
                                        ),
                                        onClick = { selectedInfoKey = child.info.stableKey }
                                    )
                                }
                                Text(
                                    stringResource(
                                        R.string.tissue_analysis_model_range,
                                        tissueStatusLabel(child.status),
                                        child.recoveryRange
                                    )
                                )
                                Text(
                                    stringResource(
                                        R.string.tissue_analysis_contributing_exercises,
                                        localizedContributorNames(child.contributors).ifBlank {
                                            stringResource(R.string.tissue_analysis_no_contributor)
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
        if (ui.joints.size > 3) {
            TextButton(onClick = { showAllJoints = !showAllJoints }) {
                Text(
                    stringResource(
                        if (showAllJoints) R.string.tissue_analysis_collapse else R.string.tissue_analysis_show_remaining
                    )
                )
            }
        }
        TissueBaselineProvenanceFooter(ui.provenance)
    }
    selectedInfoKey?.let(ui::info)?.let { info ->
        TissueEducationalInfoDialog(
            info = info,
            onDismiss = { selectedInfoKey = null }
        )
    }
}

@Composable
private fun TissueInfoButton(
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = contentDescription
        )
    }
}

@Composable
private fun TissueEducationalInfoDialog(
    info: TissueEducationalInfo,
    onDismiss: () -> Unit
) {
    val localized = localizedTissueEducation(info)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TissueInfoField(stringResource(R.string.tissue_info_label_location), localized.location)
                TissueInfoField(
                    stringResource(R.string.tissue_info_label_functions),
                    localized.functions
                )
                TissueInfoField(
                    stringResource(R.string.tissue_info_label_contexts),
                    localized.contexts
                )
                Text(
                    stringResource(R.string.tissue_info_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun localizedTissueNames(values: List<TissueEducationalInfo>): String {
    val names = ArrayList<String>(values.size)
    for (info in values) names += localizedTissueEducation(info).name
    return names.joinToString()
}

@Composable
private fun localizedContributorNames(values: List<TissueExerciseContribution>): String {
    val names = ArrayList<String>(values.size)
    for (contributor in values) {
        names += localizedExerciseName(contributor.exerciseStableKey, contributor.exerciseName)
    }
    return names.joinToString()
}

@Composable
private fun TissueBaselineProvenanceFooter(provenance: TissueBaselineProvenanceUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(
                when (provenance.source) {
                    TissueBaselineProvenance.PRIOR_ONLY -> R.string.tissue_baseline_source_prior
                    TissueBaselineProvenance.PERSONAL_ONLY -> R.string.tissue_baseline_source_personal
                    TissueBaselineProvenance.MIXED -> R.string.tissue_baseline_source_mixed
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (provenance.source == TissueBaselineProvenance.MIXED) {
            Text(
                text = stringResource(R.string.tissue_baseline_source_mixed_explanation),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun tissueStatusLabel(status: TissueCanonicalStatus): String =
    stringResource(
        when (status) {
            TissueCanonicalStatus.LOW -> R.string.tissue_status_low
            TissueCanonicalStatus.MODERATE -> R.string.tissue_status_moderate
            TissueCanonicalStatus.HIGH -> R.string.tissue_status_high
            TissueCanonicalStatus.VERY_HIGH -> R.string.tissue_status_very_high
            TissueCanonicalStatus.CALIBRATING,
            TissueCanonicalStatus.UNAVAILABLE -> R.string.tissue_status_unavailable
        }
    )

@Composable
private fun TissueInfoField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
