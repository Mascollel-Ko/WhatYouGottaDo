package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.training.trackplanner.data.ExerciseRuntimeMetadataEditorData
import com.training.trackplanner.data.ExerciseTaxonomy
import com.training.trackplanner.data.FatigueForceType
import com.training.trackplanner.data.FatigueTrainingRole
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.MovementCategory
import com.training.trackplanner.data.MovementPattern

@Composable
internal fun RuntimeMetadataExerciseEditorDialog(
    initial: ExerciseRuntimeMetadataEditorData,
    onDismiss: () -> Unit,
    onSave: (ExerciseRuntimeMetadataEditorData) -> Unit
) {
    var exercise by remember(initial) { mutableStateOf(initial.exercise) }
    var metadata by remember(initial) { mutableStateOf(initial.metadata) }
    var restText by remember(initial) { mutableStateOf(initial.exercise.defaultRestSeconds.toString()) }
    var error by remember(initial) { mutableStateOf<String?>(null) }
    val options = initial.options

    fun token(values: List<String>): MetadataTokenField =
        MetadataTokenField(raw = values.joinToString("|"), values = values)
    fun splitExerciseTokens(raw: String): List<String> =
        raw.split(',', '|', '/', ';')
            .map(String::trim)
            .filter(String::isNotBlank)
    fun joinExerciseTokens(values: List<String>): String = values.joinToString(",")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (exercise.id == 0L) "사용자 운동 추가" else "운동 메타데이터 수정",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        MetadataEditorSection("1. 기본 운동 정보", initiallyExpanded = true) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = exercise.name,
                                onValueChange = { exercise = exercise.copy(name = it) },
                                label = { Text("운동 이름") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = exercise.category,
                                onValueChange = { exercise = exercise.copy(category = it) },
                                label = { Text("기본 분류") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = exercise.description,
                                onValueChange = { exercise = exercise.copy(description = it) },
                                label = { Text("설명/메모") }
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = restText,
                                onValueChange = { restText = it.filter(Char::isDigit) },
                                label = { Text("기본 휴식시간(초)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                    item {
                        MetadataEditorSection("2. 프로그램/분석") {
                            MetadataSingleSelectField("활동 종류", metadata.activityKind, options.values("activityKind", metadata.activityKind)) { metadata = metadata.copy(activityKind = it) }
                            MetadataSingleSelectField("프로그램 사용 여부", metadata.planningEligibility, options.values("planningEligibility", metadata.planningEligibility)) { metadata = metadata.copy(planningEligibility = it) }
                            MetadataSingleSelectField("프로그램 역할", metadata.programSlot, options.values("programSlot", metadata.programSlot)) { metadata = metadata.copy(programSlot = it) }
                            MetadataSingleSelectField("중복 억제 그룹", metadata.redundancyGroup, options.values("redundancyGroup", metadata.redundancyGroup)) { metadata = metadata.copy(redundancyGroup = it) }
                            MetadataSingleSelectField("진행 지표", metadata.progressMetricType, options.values("progressMetricType", metadata.progressMetricType)) { metadata = metadata.copy(progressMetricType = it) }
                            MetadataSingleSelectField("근력 진행 그룹", metadata.strengthProgressionGroup, options.values("strengthProgressionGroup", metadata.strengthProgressionGroup)) { metadata = metadata.copy(strengthProgressionGroup = it) }
                            MetadataMultiSelectField("분석 대상", metadata.analysisEligibility.values, options.values("analysisEligibility")) { metadata = metadata.copy(analysisEligibility = token(it)) }
                        }
                    }
                    item {
                        MetadataEditorSection("3. 동작 분류") {
                            MetadataMultiSelectField("주동근", splitExerciseTokens(exercise.primaryMuscles), ExerciseTaxonomy.muscles.sorted()) {
                                exercise = exercise.copy(primaryMuscles = joinExerciseTokens(it))
                            }
                            MetadataMultiSelectField("보조근", splitExerciseTokens(exercise.secondaryMuscles), ExerciseTaxonomy.muscles.sorted()) {
                                exercise = exercise.copy(secondaryMuscles = joinExerciseTokens(it))
                            }
                            MetadataMultiSelectField("장비", splitExerciseTokens(exercise.equipment.ifBlank { exercise.equipmentTags }), ExerciseTaxonomy.equipment.sorted()) {
                                exercise = exercise.copy(equipment = joinExerciseTokens(it), equipmentTags = joinExerciseTokens(it))
                            }
                            MetadataSingleSelectField("원본 동작 패턴", exercise.movementPattern, MovementPattern.entries.map { it.name }) {
                                exercise = exercise.copy(movementPattern = it)
                            }
                            MetadataSingleSelectField("원본 동작 분류", exercise.movementCategory, MovementCategory.entries.map { it.name }) {
                                exercise = exercise.copy(movementCategory = it)
                            }
                            MetadataSingleSelectField("힘/부하 유형", exercise.forceType, FatigueForceType.entries.map { it.name }) {
                                exercise = exercise.copy(forceType = it)
                            }
                            MetadataSingleSelectField("신체 부위", exercise.bodyRegion, ExerciseTaxonomy.bodyRegions.sorted()) {
                                exercise = exercise.copy(bodyRegion = it)
                            }
                            MetadataSingleSelectField("운동 역할", exercise.trainingRole, FatigueTrainingRole.entries.map { it.name }) {
                                exercise = exercise.copy(trainingRole = it)
                            }
                            MetadataSingleSelectField("동작 계열", metadata.movementFamily, options.values("movementFamily", metadata.movementFamily)) { metadata = metadata.copy(movementFamily = it) }
                            MetadataSingleSelectField("동작 세부형", metadata.movementSubtype, options.values("movementSubtype", metadata.movementSubtype)) { metadata = metadata.copy(movementSubtype = it) }
                        }
                    }
                    item {
                        MetadataEditorSection("4. 스트레스/피로") {
                            MetadataSingleSelectField("주 스트레스", metadata.primaryStressProfile, options.values("primaryStressProfile", metadata.primaryStressProfile)) { metadata = metadata.copy(primaryStressProfile = it) }
                            MetadataMultiSelectField("보조 스트레스", metadata.secondaryStressTags.values, options.values("secondaryStressTags")) { metadata = metadata.copy(secondaryStressTags = token(it)) }
                            MetadataMultiSelectField("건 스트레스", metadata.tendonStressTags.values, options.values("tendonStressTags")) { metadata = metadata.copy(tendonStressTags = token(it)) }
                            MetadataMultiSelectField("관절 안정성", metadata.ligamentJointStabilityStressTags.values, options.values("ligamentJointStabilityStressTags")) { metadata = metadata.copy(ligamentJointStabilityStressTags = token(it)) }
                            MetadataMultiSelectField("충격 스트레스", metadata.jointImpactStressTags.values, options.values("jointImpactStressTags")) { metadata = metadata.copy(jointImpactStressTags = token(it)) }
                            MetadataMultiSelectField("인지 스트레스", metadata.cognitiveStressTags.values, options.values("cognitiveStressTags")) { metadata = metadata.copy(cognitiveStressTags = token(it)) }
                            MetadataMultiSelectField("스포츠 맥락", metadata.sportContextTags.values, options.values("sportContextTags")) { metadata = metadata.copy(sportContextTags = token(it)) }
                            MetadataSingleSelectField("회복 감쇠", metadata.recoveryDecayProfile, options.values("recoveryDecayProfile", metadata.recoveryDecayProfile)) { metadata = metadata.copy(recoveryDecayProfile = it) }
                            MetadataSingleSelectField("전체 스트레스 크기", metadata.stressMagnitudeHint, options.values("stressMagnitudeHint", metadata.stressMagnitudeHint)) { metadata = metadata.copy(stressMagnitudeHint = it) }
                        }
                    }
                    item {
                        MetadataEditorSection("5. 배드민턴 전이") {
                            MetadataSingleSelectField("전이 수준", metadata.badmintonTransferLevel, options.values("badmintonTransferLevel", metadata.badmintonTransferLevel)) { metadata = metadata.copy(badmintonTransferLevel = it) }
                            MetadataMultiSelectField("전이 종류", metadata.badmintonTransferType.values, options.values("badmintonTransferType")) { metadata = metadata.copy(badmintonTransferType = token(it)) }
                            MetadataMultiSelectField("기술 목표", metadata.badmintonSkillTargets.values, options.values("badmintonSkillTargets")) { metadata = metadata.copy(badmintonSkillTargets = token(it)) }
                            MetadataMultiSelectField("신체 능력", metadata.badmintonPhysicalQualities.values, options.values("badmintonPhysicalQualities")) { metadata = metadata.copy(badmintonPhysicalQualities = token(it)) }
                            MetadataSingleSelectField("전이 신뢰도", metadata.transferConfidence, options.values("transferConfidence", metadata.transferConfidence)) { metadata = metadata.copy(transferConfidence = it) }
                        }
                    }
                    item {
                        MetadataEditorSection("6. 근거/상태") {
                            MetadataSingleSelectField("근거 신뢰도", metadata.sourceConfidenceLevel, options.values("sourceConfidenceLevel", metadata.sourceConfidenceLevel)) { metadata = metadata.copy(sourceConfidenceLevel = it) }
                            MetadataSingleSelectField("근거 상태", metadata.finalSourceStatus, options.values("finalSourceStatus", metadata.finalSourceStatus)) { metadata = metadata.copy(finalSourceStatus = it) }
                        }
                    }
                    item {
                        MetadataEditorSection("7. 고급 런타임 필드") {
                            Text("stableKey: ${exercise.stableKey.ifBlank { "저장 시 자동 생성" }}", style = MaterialTheme.typography.bodySmall)
                            MetadataSingleSelectField("근신경계", metadata.neuromuscularStressLevel, options.values("neuromuscularStressLevel", metadata.neuromuscularStressLevel)) { metadata = metadata.copy(neuromuscularStressLevel = it) }
                            MetadataSingleSelectField("전신 근육", metadata.systemicMuscularStressLevel, options.values("systemicMuscularStressLevel", metadata.systemicMuscularStressLevel)) { metadata = metadata.copy(systemicMuscularStressLevel = it) }
                            MetadataSingleSelectField("국소 근육", metadata.localMuscularStressLevel, options.values("localMuscularStressLevel", metadata.localMuscularStressLevel)) { metadata = metadata.copy(localMuscularStressLevel = it) }
                            MetadataSingleSelectField("관절/건/충격", metadata.jointTendonImpactStressLevel, options.values("jointTendonImpactStressLevel", metadata.jointTendonImpactStressLevel)) { metadata = metadata.copy(jointTendonImpactStressLevel = it) }
                            MetadataSingleSelectField("동작 집중", metadata.movementFocusDemandLevel, options.values("movementFocusDemandLevel", metadata.movementFocusDemandLevel)) { metadata = metadata.copy(movementFocusDemandLevel = it) }
                            MetadataSingleSelectField("회복 기간", metadata.recoveryDurationClass, options.values("recoveryDurationClass", metadata.recoveryDurationClass)) { metadata = metadata.copy(recoveryDurationClass = it) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Checkbox(checked = false, onCheckedChange = null, enabled = false)
                                Text("seed 자동 변경 허용 안 함")
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val rest = restText.toIntOrNull()
                        error = when {
                            exercise.name.isBlank() -> "운동 이름을 입력하세요."
                            exercise.category.isBlank() -> "기본 분류를 입력하세요."
                            rest == null || rest !in 0..3600 -> "휴식시간은 0~3600초로 입력하세요."
                            else -> null
                        }
                        if (error == null) {
                            onSave(
                                initial.copy(
                                    exercise = exercise.copy(defaultRestSeconds = rest!!),
                                    metadata = metadata.copy(
                                        exerciseName = exercise.name,
                                        safeForSeedMutation = false
                                    )
                                )
                            )
                        }
                    }
                ) {
                    Text("저장")
                }
            }
        }
    }
}
