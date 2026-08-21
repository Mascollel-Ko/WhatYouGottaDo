package com.training.trackplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.lab.StrictBayesianLabResult
import com.training.trackplanner.analysis.lab.StrictBayesianLabUiState
import com.training.trackplanner.analysis.lab.StrictLabAnalysisRequest
import com.training.trackplanner.analysis.lab.StrictLabBlocker
import com.training.trackplanner.analysis.lab.StrictLabBlockerCode
import com.training.trackplanner.analysis.lab.StrictLabExecutionStage
import com.training.trackplanner.analysis.lab.StrictLabFailureCode
import com.training.trackplanner.analysis.lab.StrictLabFeatureCatalog
import com.training.trackplanner.analysis.lab.StrictLabFeatureOption
import com.training.trackplanner.analysis.lab.StrictLabPreflight
import com.training.trackplanner.analysis.lab.StrictSamplingReliabilityMode
import com.training.trackplanner.analysis.lab.allowsRelaxedRetry
import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.util.Locale

@Composable
internal fun LaggedTimeSeriesAnalysisContent(
    featureCatalog: StrictLabFeatureCatalog,
    executionState: StrictBayesianLabUiState,
    onRequestChanged: (StrictLabAnalysisRequest) -> Unit,
    onAnalyze: (StrictLabAnalysisRequest) -> Unit,
    onRetry: () -> Unit,
    onRelaxedRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val enabledX = featureCatalog.xFeatures.filter { it.enabled }
    val enabledY = featureCatalog.responseFeatures.filter { it.enabled }
    val defaultX = enabledX.firstOrNull { it.key.value == TrendMetricId.BADMINTON_PRACTICE_LOAD.stableId }
        ?: enabledX.firstOrNull()
    val defaultY = enabledY.firstOrNull { it.key.value == TrendMetricId.FATIGUE_COMPOSITE.stableId }
        ?: enabledY.firstOrNull()
    var xId by rememberSaveable { mutableStateOf("") }
    var yIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var controlIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var horizon by rememberSaveable { mutableStateOf(2) }
    var showYPicker by rememberSaveable { mutableStateOf(false) }
    var showControlPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(featureCatalog.snapshotFingerprint) {
        val validX = enabledX.mapTo(mutableSetOf()) { it.key.value }
        val validY = enabledY.mapTo(mutableSetOf()) { it.key.value }
        val validControls = featureCatalog.controlFeatures.filter { it.enabled }.mapTo(mutableSetOf()) { it.key.value }
        if (xId !in validX) xId = defaultX?.key?.value.orEmpty()
        yIds = yIds.filter { it in validY && it != xId }.distinct()
        if (yIds.isEmpty()) defaultY?.takeIf { it.key.value != xId }?.let { yIds = listOf(it.key.value) }
        controlIds = controlIds.filter { it in validControls && it != xId && it !in yIds }.distinct()
    }

    val selectedX = enabledX.firstOrNull { it.key.value == xId }
    val selectedY = yIds.mapNotNull { id -> enabledY.firstOrNull { it.key.value == id } }
    val selectedControls = controlIds.mapNotNull { id ->
        featureCatalog.controlFeatures.firstOrNull { it.enabled && it.key.value == id }
    }
    val request = selectedX?.let { x ->
        selectedY.takeIf { it.isNotEmpty() }?.let { responses ->
            StrictLabAnalysisRequest(
                xFeature = x.key,
                yFeatures = responses.map { it.key },
                controls = selectedControls.map { it.key },
                requestedHorizon = horizon
            ).normalized()
        }
    }
    val running = executionState is StrictBayesianLabUiState.Running

    LaunchedEffect(request, featureCatalog.snapshotFingerprint) {
        request?.let(onRequestChanged)
    }
    DisposableEffect(Unit) {
        onDispose(onCancel)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StrictLabIntroductionCard()
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("엄격 Bayesian 시차 분석", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (enabledX.isEmpty() || enabledY.isEmpty()) {
                    InfoCard("전체 기록에서 분석 가능한 주간 지표를 준비하고 있습니다.")
                } else {
                    StrictFeatureDropdown(
                        label = "충격 변수 X",
                        selected = selectedX,
                        options = featureCatalog.xFeatures,
                        enabled = !running,
                        onSelect = { option ->
                            xId = option.key.value
                            yIds = yIds.filterNot { it == xId }
                            if (yIds.isEmpty()) {
                                enabledY.firstOrNull { it.key.value != xId }?.let { yIds = listOf(it.key.value) }
                            }
                            controlIds = controlIds.filterNot { it == xId || it in yIds }
                        }
                    )
                    StrictSelectionSurface(
                        label = "응답 변수 Y",
                        summary = strictFeatureSummary(selectedY),
                        enabled = !running,
                        onClick = { showYPicker = true }
                    )
                    StrictSelectionSurface(
                        label = "외생 통제 Z",
                        summary = strictFeatureSummary(selectedControls),
                        enabled = !running,
                        onClick = { showControlPicker = true }
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("반응 확인 기간", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { horizon-- }, enabled = !running && horizon > 1) { Text("-") }
                            Text("${horizon}주", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { horizon++ }, enabled = !running && horizon < 8) { Text("+") }
                        }
                    }
                    StrictPreflightContent(executionState)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = request != null &&
                            !running &&
                            (executionState as? StrictBayesianLabUiState.PreflightReady)?.preflight?.canAnalyze == true,
                        onClick = { request?.let(onAnalyze) }
                    ) {
                        Text("엄격 Bayesian 분석하기")
                    }
                    Text(
                        "전체 주간 기록으로 posterior를 표본추출합니다. 80% 구간이 넓으면 방향보다 불확실성을 우선해 해석하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        when (executionState) {
            is StrictBayesianLabUiState.Running -> StrictRunningCard(executionState.stage)
            is StrictBayesianLabUiState.Success -> StrictResultCard(executionState.result)
            is StrictBayesianLabUiState.Failed -> StrictFailureCard(executionState, onRetry, onRelaxedRetry)
            StrictBayesianLabUiState.Idle,
            is StrictBayesianLabUiState.DataPreparing,
            is StrictBayesianLabUiState.PreflightReady -> Unit
        }
    }

    if (showYPicker) {
        StrictFeatureMultiSelectDialog(
            title = "응답 변수 Y 선택",
            options = featureCatalog.responseFeatures.filterNot { it.key.value == xId },
            selectedIds = yIds.toSet(),
            requireSelection = true,
            onDismiss = { showYPicker = false },
            onApply = { selected ->
                yIds = selected
                controlIds = controlIds.filterNot { it in yIds }
                showYPicker = false
            }
        )
    }
    if (showControlPicker) {
        StrictFeatureMultiSelectDialog(
            title = "외생 통제 Z 선택",
            options = featureCatalog.controlFeatures.filterNot { it.key.value == xId || it.key.value in yIds },
            selectedIds = controlIds.toSet(),
            requireSelection = false,
            onDismiss = { showControlPicker = false },
            onApply = { selected ->
                controlIds = selected
                showControlPicker = false
            }
        )
    }
}

@Composable
private fun StrictLabIntroductionCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("시차 관계 분석", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("훈련 지표가 변한 뒤 회복과 수행 지표가 몇 주에 걸쳐 어떻게 달라졌는지 봅니다.")
            Text(
                "결과는 기록에 조건부인 posterior이며 인과관계를 확정하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StrictFeatureDropdown(
    label: String,
    selected: StrictLabFeatureOption?,
    options: List<StrictLabFeatureOption>,
    enabled: Boolean,
    onSelect: (StrictLabFeatureOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true },
                enabled = enabled
            ) {
                Text(selected?.displayName ?: "지표 선택")
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        enabled = option.enabled,
                        text = {
                            Column {
                                Text(option.displayName)
                                Text(
                                    option.disabledReason ?: "사용 가능 ${option.availableWeeks}주",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StrictSelectionSurface(
    label: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            text = "$label: $summary",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun StrictPreflightContent(state: StrictBayesianLabUiState) {
    if (state is StrictBayesianLabUiState.DataPreparing || state is StrictBayesianLabUiState.Idle) {
        Text("전체 주간 기록으로 분석 가능 여부를 확인하는 중입니다.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val preflight = when (state) {
        is StrictBayesianLabUiState.PreflightReady -> state.preflight
        is StrictBayesianLabUiState.Running -> state.preflight
        is StrictBayesianLabUiState.Success -> state.preflight
        is StrictBayesianLabUiState.Failed -> state.preflight
        StrictBayesianLabUiState.Idle,
        is StrictBayesianLabUiState.DataPreparing -> null
    } ?: return
    if (preflight.availableFrom != null && preflight.availableUntil != null) {
        Text("사용 가능한 기간: ${preflight.availableFrom}~${preflight.availableUntil}", style = MaterialTheme.typography.labelSmall)
    }
    Text("완료된 주간 기록: ${preflight.closedWeeks}주", style = MaterialTheme.typography.labelSmall)
    preflight.blockers.forEach { blocker ->
        Text(strictBlockerMessage(blocker), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    preflight.warnings.forEach { warning ->
        Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StrictRunningCard(stage: StrictLabExecutionStage) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("엄격 Bayesian 분석을 실행하고 있습니다.", fontWeight = FontWeight.SemiBold)
                Text(strictStageLabel(stage), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StrictResultCard(result: StrictBayesianLabResult) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("엄격 Bayesian 분석 결과", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (result.samplingReliabilityMode == StrictSamplingReliabilityMode.RELAXED) {
                Text(
                    "완화된 신뢰도 기준으로 계산된 결과입니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(result.summary)
            if (result.officialLagProbability.isNotEmpty()) {
                Text(
                    "시차 posterior: " + result.officialLagProbability.entries.sortedBy { it.key }
                        .joinToString(", ") { (lag, probability) -> "${lag}주 ${strictValue(probability * 100.0)}%" },
                    style = MaterialTheme.typography.labelSmall
                )
            }
            result.responses.forEach { response ->
                Text(response.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                response.points.forEach { point ->
                    Text(
                        "${point.horizonWeeks}주 후: 중앙값 ${strictValue(point.estimate)} · 80% 구간 ${strictValue(point.low80)}~${strictValue(point.high80)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                "posterior 중앙값과 80% 구간입니다. 구간이 0을 넓게 가로지르면 방향을 단정하지 마세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.simplificationDiagnostics.isNotEmpty()) {
                Text("현재 기록에서 계산 가능한 더 단순한 모형을 사용했습니다.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "단순화 내용 접기" else "단순화 내용")
                }
                if (showDetails) {
                    result.simplificationDiagnostics.forEach { diagnostic ->
                        Text(diagnostic, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StrictFailureCard(
    state: StrictBayesianLabUiState.Failed,
    onRetry: () -> Unit,
    onRelaxedRetry: () -> Unit
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("분석을 완료하지 못했습니다", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.message, color = MaterialTheme.colorScheme.error)
            Text(strictFailureNextStep(state.code), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRetry) { Text("다시 시도") }
            if (state.code.allowsRelaxedRetry) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRelaxedRetry) {
                    Text("완화해서 결과 보기")
                }
            }
            if (state.diagnostics.isNotEmpty()) {
                TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "자세히 접기" else "자세히") }
                if (showDetails) {
                    Text("실패 로그", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    state.diagnosticId?.let { Text("진단 코드: $it", style = MaterialTheme.typography.labelSmall) }
                    state.diagnostics.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun StrictFeatureMultiSelectDialog(
    title: String,
    options: List<StrictLabFeatureOption>,
    selectedIds: Set<String>,
    requireSelection: Boolean,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var draft by remember(selectedIds) { mutableStateOf(selectedIds.toList()) }
    val filtered = options.filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = !requireSelection || draft.isNotEmpty(), onClick = { onApply(draft) }) { Text("적용") }
        },
        dismissButton = {
            TextButton(onClick = { draft = emptyList() }) { Text("초기화") }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("검색") }
                )
                Text(strictFeatureSummary(options.filter { it.key.value in draft }), style = MaterialTheme.typography.labelMedium)
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filtered.forEach { option ->
                        val checked = option.key.value in draft
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = option.enabled) {
                                draft = if (checked) draft - option.key.value else draft + option.key.value
                            },
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                enabled = option.enabled,
                                onCheckedChange = { selected ->
                                    draft = if (selected) (draft + option.key.value).distinct() else draft - option.key.value
                                }
                            )
                            Column {
                                Text(option.displayName)
                                Text(
                                    option.disabledReason ?: "사용 가능 ${option.availableWeeks}주",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun strictFeatureSummary(options: List<StrictLabFeatureOption>): String =
    options.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.displayName } ?: "선택 없음"

private fun strictBlockerMessage(blocker: StrictLabBlocker): String = when (blocker.code) {
    StrictLabBlockerCode.INVALID_HORIZON -> "반응 확인 기간은 1~8주에서 선택해야 합니다."
    StrictLabBlockerCode.RESPONSE_REQUIRED -> "응답 변수 Y를 하나 이상 선택해 주세요."
    StrictLabBlockerCode.FEATURE_UNAVAILABLE -> "선택한 지표의 완료된 주간 기록이 없습니다."
    StrictLabBlockerCode.INSUFFICIENT_VARIATION -> "선택한 지표의 주간 값 변화가 아직 없습니다."
    StrictLabBlockerCode.SNAPSHOT_NOT_READY -> "전체 주간 기록 스냅샷을 준비하지 못했습니다."
    StrictLabBlockerCode.PHASE_A_INELIGIBLE -> "현재 기록으로 승인된 엄격 모형 입력을 만들 수 없습니다."
}

private fun strictStageLabel(stage: StrictLabExecutionStage): String = when (stage) {
    StrictLabExecutionStage.PREPARING_STRICT_INPUT -> "엄격 분석 입력을 준비하는 중입니다."
    StrictLabExecutionStage.STABILIZING_CHAINS -> "Bayesian chain을 안정화하는 중입니다."
    StrictLabExecutionStage.SAMPLING_POSTERIOR -> "posterior를 표본추출하는 중입니다."
    StrictLabExecutionStage.CHECKING_PRECISION -> "수치 신뢰도를 확인하는 중입니다."
    StrictLabExecutionStage.EXTENDING_SAMPLING -> "필요한 정밀도를 위해 표본추출을 연장하는 중입니다."
    StrictLabExecutionStage.SUMMARIZING_POSTERIOR -> "posterior 결과를 정리하는 중입니다."
}

private fun strictFailureNextStep(code: StrictLabFailureCode): String = when (code) {
    StrictLabFailureCode.DATA_NOT_READY,
    StrictLabFailureCode.STALE_RESULT_REJECTED -> "기록 준비가 끝난 뒤 다시 시도해 주세요."
    StrictLabFailureCode.PREFLIGHT_INELIGIBLE -> "지표를 줄이거나 기록이 있는 다른 지표를 선택해 주세요."
    StrictLabFailureCode.FOCAL_FEATURE_UNAVAILABLE -> "선택한 충격 지표의 기록 또는 표현 정책을 확인해 주세요."
    StrictLabFailureCode.NO_TARGET_VARIATION -> "응답 지표의 주간 변화가 더 쌓인 뒤 다시 확인해 주세요."
    StrictLabFailureCode.NO_FOCAL_VARIATION -> "충격 지표의 주간 변화가 더 쌓인 뒤 다시 확인해 주세요."
    StrictLabFailureCode.NO_FEASIBLE_COMMON_LAG_PLAN -> "통제 변수를 줄이거나 반응 확인 기간을 줄여 주세요."
    StrictLabFailureCode.METADATA_INCOMPLETE -> "분석에 필요한 canonical metadata가 준비된 뒤 다시 시도해 주세요."
    StrictLabFailureCode.REPRESENTATION_POLICY_UNAVAILABLE,
    StrictLabFailureCode.REPRESENTATION_DIAGNOSTIC_CONFLICT -> "다른 지표 조합을 선택해 주세요."
    StrictLabFailureCode.MCMC_CONVERGENCE_FAILED,
    StrictLabFailureCode.LAG_POSTERIOR_MIXING_FAILED,
    StrictLabFailureCode.MONTE_CARLO_PRECISION_NOT_REACHED -> "같은 조건으로 다시 시도하거나 더 많은 주간 기록이 쌓인 뒤 확인해 주세요."
    StrictLabFailureCode.NUMERICAL_SPD_FAILURE,
    StrictLabFailureCode.NONFINITE_STATE -> "지표 조합을 줄여 다시 시도해 주세요."
    StrictLabFailureCode.CANCELLED -> "분석이 취소되었습니다."
    StrictLabFailureCode.INTERNAL_ERROR -> "자세히에서 진단 코드를 확인한 뒤 다시 시도해 주세요."
}

private fun strictValue(value: Double): String = String.format(Locale.KOREA, "%.3f", value)
