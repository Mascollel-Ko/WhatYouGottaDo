package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.training.trackplanner.data.CanonicalMuscleRoleEntry
import com.training.trackplanner.data.ExerciseRuntimeMetadataEditorData
import com.training.trackplanner.data.UserMuscleRoleCodec
import com.training.trackplanner.localization.localizedExerciseName

/**
 * One exercise at a time. This never commits guessed legacy roles automatically:
 * the user must press Confirm. No exercise or workout row is edited here.
 */
@Composable
internal fun CustomMuscleRoleReviewDialog(
    initial: ExerciseRuntimeMetadataEditorData,
    remaining: Int,
    onLater: () -> Unit,
    onConfirm: (List<CanonicalMuscleRoleEntry>, (Result<Unit>) -> Unit) -> Unit
) {
    val exercise = initial.exercise
    val stableKey = exercise.stableKey
    val context = LocalContext.current
    val catalogue = remember(context) { MetadataDisplayCatalogue.from(context) }
    var roles by remember(stableKey) { mutableStateOf(initial.muscleRoles) }
    var showCopy by remember(stableKey) { mutableStateOf(false) }
    var confirmEmpty by remember(stableKey) { mutableStateOf(false) }
    var saving by remember(stableKey) { mutableStateOf(false) }
    var error by remember(stableKey) { mutableStateOf<String?>(null) }

    fun submit() {
        if (saving) return
        saving = true
        error = null
        onConfirm(roles) { result ->
            saving = false
            result.onFailure { failure -> error = failure.message ?: "저장하지 못했습니다." }
        }
    }

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MaterialText(
                    "이전 운동 근육 정보 확인",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                MaterialText(
                    "남은 운동 ${remaining}개 · ${exercise.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                MaterialText(
                    "예전에는 근육을 주동근·보조근으로만 저장했습니다. " +
                        "새 방식에서는 안정화 근육 등도 구분합니다. " +
                        "아래 내용을 확인한 뒤 저장해야 분석에 사용됩니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showCopy = true },
                    enabled = !saving
                ) {
                    MaterialText("기본 운동 검색해서 근육 정보 복사하기")
                }
                MaterialText(
                    "비슷한 기본 운동을 검색해 근육 정보를 가져온 뒤, 이 운동에 맞게 고칠 수 있습니다. " +
                        "복사해도 원래 운동의 기록이나 다른 설정은 바뀌지 않습니다.",
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (initial.legacyReviewPrimary.isNotBlank() || initial.legacyReviewSecondary.isNotBlank()) {
                        item {
                            MaterialText("이전에 저장한 근육 정보 (참고용)", fontWeight = FontWeight.SemiBold)
                            MaterialText(
                                "주동근: ${initial.legacyReviewPrimary.ifBlank { "없음" }} / " +
                                    "보조근: ${initial.legacyReviewSecondary.ifBlank { "없음" }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            MaterialText(
                                "아래 역할은 확인 전에는 자동으로 확정되지 않습니다.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    item { MaterialText("근육별 역할", fontWeight = FontWeight.SemiBold) }
                    UserMuscleRoleCodec.MUSCLE_CODES.forEach { code ->
                        item(key = code) {
                            val prior = roles.firstOrNull { it.muscleCode == code }
                            MuscleRoleChoiceRow(
                                label = catalogue.option(MetadataDisplayField.MUSCLE_GROUP, code).label,
                                selected = prior?.role,
                                onChange = { selected ->
                                    roles = roles.filterNot { it.muscleCode == code } +
                                        listOfNotNull(selected?.let { role ->
                                            UserMuscleRoleCodec.entry(stableKey, code, role).copy(
                                                coefficient = if (prior?.role == role) prior.coefficient
                                                    else UserMuscleRoleCodec.coefficient(role)
                                            )
                                        })
                                }
                            )
                        }
                    }
                }
                error?.let { MaterialText(it, color = MaterialTheme.colorScheme.error) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onLater,
                        enabled = !saving
                    ) { MaterialText("나중에") }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !saving,
                        onClick = { if (roles.isEmpty()) confirmEmpty = true else submit() }
                    ) { MaterialText(if (saving) "저장 중" else "확인하고 다음") }
                }
            }
        }
    }
    if (showCopy) {
        ExerciseMetadataCopyDialog(
            sources = initial.copySources.filter {
                !it.exercise.isCustom && it.muscleRoles.isNotEmpty() &&
                    it.exercise.planningEligibility != "HISTORY_ONLY"
            },
            onDismiss = { showCopy = false },
            onSelect = { source ->
                roles = source.muscleRoles.map { row ->
                    UserMuscleRoleCodec.entry(stableKey, row.muscleCode, row.role)
                        .copy(coefficient = row.coefficient)
                }
                showCopy = false
            }
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { MaterialText("근육 정보를 비워 둘까요?") },
            text = { MaterialText("참여 근육을 하나도 지정하지 않으면 근육별 운동량과 부위별 피로 계산에 이 운동이 반영되지 않습니다.") },
            confirmButton = {
                TextButton(onClick = { confirmEmpty = false; submit() }) {
                    MaterialText("비운 채 확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { MaterialText("취소") }
            }
        )
    }
}
