package com.training.trackplanner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource

internal const val PUBLIC_PROTOCOL_INDEX_URL =
    "https://github.com/Mascollel-Ko/WhatYouGottaDo/tree/main/docs/protocols"

internal enum class AppInfoRoute(val routeName: String) {
    AppExplanation("app_explanation"),
    AnalysisGuide("analysis_guide"),
    CalculationPrinciples("calculation_principles");

    val parent: AppInfoRoute?
        get() = when (this) {
            AppExplanation -> null
            AnalysisGuide, CalculationPrinciples -> AppExplanation
        }
}

internal fun launchPublicProtocolIndex(startActivity: (Intent) -> Unit): Boolean {
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PUBLIC_PROTOCOL_INDEX_URL)))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

@Composable
internal fun AppExplanationScreen(
    onBack: () -> Unit,
    onOpenAnalysisGuide: () -> Unit,
    onOpenCalculationPrinciples: () -> Unit
) {
    ExplanationScaffold(
        title = stringResource(R.string.app_explanation_title),
        onBack = onBack
    ) {
        item {
            val introduction = stringResource(R.string.app_explanation_intro)
            val appName = stringResource(R.string.product_name)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(R.string.app_explanation_hero_title),
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = buildAnnotatedString {
                        append(introduction)
                        if (introduction.startsWith(appName)) {
                            addStyle(
                                SpanStyle(fontWeight = FontWeight.SemiBold),
                                0,
                                appName.length
                            )
                        }
                    },
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.sp
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SectionTitle(R.string.app_explanation_features_title)
                FeatureRow(
                    R.string.app_explanation_feature_fatigue_title,
                    R.string.app_explanation_feature_fatigue_body
                )
                FeatureRow(
                    R.string.app_explanation_feature_muscles_title,
                    R.string.app_explanation_feature_muscles_body
                )
                FeatureRow(
                    R.string.app_explanation_feature_tissue_title,
                    R.string.app_explanation_feature_tissue_body
                )
                FeatureRow(
                    R.string.app_explanation_feature_badminton_title,
                    R.string.app_explanation_feature_badminton_body
                )
                FeatureRow(
                    R.string.app_explanation_feature_strength_title,
                    R.string.app_explanation_feature_strength_body
                )
                FeatureRow(
                    R.string.app_explanation_feature_program_title,
                    R.string.app_explanation_feature_program_body
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item { LimitationCard() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactInfoNavigationRow(
                    label = stringResource(R.string.analysis_guide_title),
                    description = stringResource(R.string.analysis_guide_open_description),
                    onClick = onOpenAnalysisGuide
                )
                CompactInfoNavigationRow(
                    label = stringResource(R.string.calculation_principles_open_label),
                    description = stringResource(R.string.calculation_principles_open_description),
                    onClick = onOpenCalculationPrinciples
                )
            }
        }
    }
}

@Composable
internal fun AnalysisGuideScreen(onBack: () -> Unit) {
    ExplanationScaffold(
        title = stringResource(R.string.analysis_guide_title),
        onBack = onBack
    ) {
        item {
            Text(
                text = stringResource(R.string.analysis_guide_intro),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                GuideSection(R.string.analysis_guide_overall_title, R.string.analysis_guide_overall_body)
                GuideSection(R.string.analysis_guide_axes_title, R.string.analysis_guide_axes_body)
                GuideSection(R.string.analysis_guide_muscles_title, R.string.analysis_guide_muscles_body)
                GuideSection(R.string.analysis_guide_tissue_title, R.string.analysis_guide_tissue_body)
                GuideSection(
                    R.string.analysis_guide_distribution_title,
                    R.string.analysis_guide_distribution_body
                )
                GuideSection(
                    R.string.analysis_guide_high_score_title,
                    R.string.analysis_guide_high_score_body
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.analysis_guide_calibration_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
internal fun CalculationPrinciplesScreen(
    onBack: () -> Unit,
    onOpenPublicProtocols: () -> Boolean
) {
    var browserUnavailable by rememberSaveable { mutableStateOf(false) }
    ExplanationScaffold(
        title = stringResource(R.string.calculation_principles_title),
        onBack = onBack
    ) {
        item {
            Text(
                text = stringResource(R.string.calculation_principles_intro),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                GuideSection(
                    R.string.calculation_principles_research_title,
                    R.string.calculation_principles_research_body
                )
                GuideSection(
                    R.string.calculation_principles_transparency_title,
                    R.string.calculation_principles_transparency_body
                )
                GuideSection(
                    R.string.calculation_principles_medical_title,
                    R.string.calculation_principles_medical_body
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(R.string.calculation_principles_families_title)
                ProtocolFamilyRow(R.string.protocol_family_overall_fatigue)
                ProtocolFamilyRow(R.string.protocol_family_fatigue_axes)
                ProtocolFamilyRow(R.string.protocol_family_connective_tissue)
                ProtocolFamilyRow(R.string.protocol_family_badminton)
                ProtocolFamilyRow(R.string.protocol_family_strength)
                ProtocolFamilyRow(R.string.protocol_family_program_builder)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val description = stringResource(R.string.public_protocol_action_description)
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = description },
                    onClick = {
                        browserUnavailable = !onOpenPublicProtocols()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.public_protocol_action))
                }
                if (browserUnavailable) {
                    Text(
                        text = stringResource(R.string.public_protocol_browser_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplanationScaffold(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigation_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 12.dp,
                end = 20.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        modifier = Modifier.semantics { heading() },
        text = stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun FeatureRow(@StringRes title: Int, @StringRes body: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(title),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )
            Text(
                text = stringResource(body),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LimitationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(R.string.app_explanation_limitation_title),
                fontSize = 17.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.app_explanation_limitation_primary),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.sp
            )
            Text(
                text = stringResource(R.string.app_explanation_limitation_secondary),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuideSection(@StringRes title: Int, @StringRes body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(title),
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(body),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProtocolFamilyRow(@StringRes label: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(label),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        )
    }
}

@Composable
internal fun CompactInfoNavigationRow(
    label: String,
    description: String,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (leading != null) {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
