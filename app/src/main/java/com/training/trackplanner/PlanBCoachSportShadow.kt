package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.v22.BCoachSportShadowReport
import java.util.Locale

private fun sportShadowNumber(value: Double): String =
    String.format(Locale.KOREA, "%.1f", value)

private fun sportShadowName(id: String): String = when (id) {
    "ACCELERATION" -> "첫 스텝·가속"
    "DECELERATION" -> "감속"
    "FOOTWORK" -> "풋워크"
    "JUMP_LANDING" -> "점프·착지"
    "LUNGE_REACH" -> "런지·도달"
    "REACTION" -> "반응"
    "CONDITIONING" -> "반복 이동·컨디셔닝"
    "ROTATION_GENERATION" -> "회전력"
    "ANTI_ROTATION" -> "항회전"
    else -> id.replace('_', ' ')
}

private fun sportShadowChanges(
    production: GeneratedProgramSkeleton?,
    alternative: GeneratedProgramSkeleton?,
    week: Int
): List<String> {
    if (production == null || alternative == null) return emptyList()
    val before = production.items.filter { it.weekNumber == week }
        .associateBy { it.dayOfWeek to it.exerciseStableKey }
    val after = alternative.items.filter { it.weekNumber == week }
        .associateBy { it.dayOfWeek to it.exerciseStableKey }
    return (before.keys + after.keys).distinct()
        .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second })
        .mapNotNull { slot ->
            val old = before[slot]
            val now = after[slot]
            when {
                old == null && now != null ->
                    slot.first.toString() + "요일 · " + now.exerciseName + " 추가 (" +
                        now.setCount + "세트)"
                now == null && old != null ->
                    slot.first.toString() + "요일 · " + old.exerciseName + " 제외 (" +
                        old.setCount + "세트)"
                now != null && old != null &&
                    ProgramSetPrescriptionResolver.resolve(now) != ProgramSetPrescriptionResolver.resolve(old) ->
                    slot.first.toString() + "요일 · " + now.exerciseName + ": " +
                        old.setCount + "세트 → " + now.setCount + "세트 (반복·중량 차이 포함)"
                else -> null
            }
        }
}

/** Shadow is a separate read-only surface. It never invokes proposal.accept(). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BCoachSportShadowDialog(
    report: BCoachSportShadowReport,
    candidateForReview: (String) -> GeneratedProgramSkeleton?,
    onClose: () -> Unit
) {
    var selectedId by remember(report) {
        mutableStateOf(report.shadowLeaderId ?: report.productionCandidateId)
    }
    val selected = report.candidates.firstOrNull { it.candidateId == selectedId }
    val production = report.candidates.firstOrNull {
        it.candidateId == report.productionCandidateId
    }
    val chosenPlan = selected?.candidateId?.let(candidateForReview)
    val productionPlan = candidateForReview(report.productionCandidateId)
    val weeks = chosenPlan?.items.orEmpty().map(ProgramSkeletonItem::weekNumber).distinct().sorted()
    var selectedWeek by remember(selectedId, weeks) { mutableStateOf(weeks.firstOrNull()) }
    val days = chosenPlan?.items.orEmpty().filter { it.weekNumber == selectedWeek }
        .map(ProgramSkeletonItem::dayOfWeek).distinct().sorted()
    var selectedDay by remember(selectedId, selectedWeek, days) {
        mutableStateOf(days.firstOrNull())
    }
    val visibleCandidates = (report.candidates.take(8) +
        report.candidates.filter { it.productionRecommended })
        .distinctBy { it.candidateId }

    val windowBounds = bCoachReviewWindowBounds()
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Surface(windowBounds.fillMaxSize().testTag("b_coach_sport_shadow_dialog")) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("후보별 스포츠 훈련량", style = MaterialTheme.typography.titleLarge)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .testTag("b_coach_sport_shadow_content"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("비교 범위", fontWeight = FontWeight.SemiBold)
                                Text("훈련 시간과 장비 등 제약을 통과한 후보 " + report.evaluatedCandidateCount +
                                    "개의 목표별 훈련량을 비교합니다.")
                                if (report.error != null) Text("평가 오류: " + report.error)
                                else if (report.shadowLeaderId == report.productionCandidateId)
                                    Text("전체 목표 추천안이 스포츠 기준에서도 상위입니다.")
                                else if (report.shadowLeaderId != null)
                                    Text("스포츠 기준 상위 후보는 전체 목표 추천안과 다릅니다.")
                                else Text("스포츠 목표의 계산 가능한 하한이 없습니다.")
                            }
                        }
                    }
                    item {
                        Text("비교할 후보", style = MaterialTheme.typography.titleMedium)
                        visibleCandidates.forEachIndexed { index, row ->
                            val flags = listOfNotNull(
                                "후보 " + (index + 1),
                                "전체 목표 추천" .takeIf { row.productionRecommended },
                                "스포츠 기준 상위".takeIf {
                                    row.candidateId == report.shadowLeaderId
                                }
                            ).joinToString(" · ")
                            OutlinedButton(
                                onClick = { selectedId = row.candidateId },
                                modifier = Modifier.fillMaxWidth().testTag(
                                    "b_coach_sport_shadow_candidate_" + row.candidateId
                                )
                            ) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(flags, fontWeight = if (selectedId == row.candidateId)
                                        FontWeight.Bold else FontWeight.Normal)
                                    Text("스포츠 부족·초과: " +
                                        (row.shadowSportLoss?.let(::sportShadowNumber) ?: "평가 불가") +
                                        " · 목표 충족 " + row.objectiveCoverage + "/" + row.objectiveCount,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (selected != null) {
                        item {
                            Text("전체 목표 추천안과 점수 비교", style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("전체 목표 추천안", style = MaterialTheme.typography.labelMedium)
                                    Text("스포츠 " + (production?.shadowSportLoss?.let(::sportShadowNumber)
                                        ?: "평가 불가"))
                                    Text("주평균 " + (production?.meanWeeklySets?.let(::sportShadowNumber)
                                        ?: "-") + "세트")
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("비교 후보", style = MaterialTheme.typography.labelMedium)
                                    Text("스포츠 " +
                                        (selected.shadowSportLoss?.let(::sportShadowNumber) ?: "평가 불가"))
                                    Text("주평균 " + sportShadowNumber(selected.meanWeeklySets) + "세트")
                                }
                            }
                            Text("최장 세션 예상 약 " +
                                sportShadowNumber(selected.estimatedMaxDayMinutes) + "분" +
                                " · 전체 목표 손실 " + sportShadowNumber(selected.productionBaseLoss))
                            Text("스포츠 지표는 낮을수록 목표 부족·초과가 적다는 뜻입니다. " +
                                "전체 훈련의 우수성이나 적응 효과를 의미하지 않습니다.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        item {
                            Text("세부 스포츠 목표", style = MaterialTheme.typography.titleMedium)
                            if (selected.objectives.isEmpty()) Text("평가 가능한 스포츠 목표가 없습니다.")
                            selected.objectives.forEach { objective ->
                                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Column(Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(sportShadowName(objective.objective),
                                            fontWeight = FontWeight.SemiBold)
                                        Text("예상 노출 " + sportShadowNumber(objective.meanDose) +
                                            " · 목표 " + sportShadowNumber(objective.meanLower) +
                                            " ~ " + sportShadowNumber(objective.meanUpper))
                                        Text("부족률 " +
                                            sportShadowNumber(objective.shortfallFraction * 100.0) +
                                            "% · " + objective.weeks + "개 주차 평균",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        item {
                            Text("스포츠 관련 운동 구성", style = MaterialTheme.typography.titleMedium)
                            if (selected.sportExercises.isEmpty()) Text("선정된 직접·보조 전이 운동 없음")
                            selected.sportExercises.forEach { exercise ->
                                Text("• " + exercise.name + " · 주평균 " +
                                    sportShadowNumber(exercise.weeklySets) + "세트")
                                Text(exercise.objectives.joinToString(" · ",
                                    transform = ::sportShadowName),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (chosenPlan != null) {
                            item {
                                Text("주차별 운동·세트·중량", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    weeks.forEach { week ->
                                        OutlinedButton(
                                            onClick = { selectedWeek = week },
                                            modifier = Modifier.testTag("b_coach_shadow_week_" + week)
                                        ) {
                                            Text(if (selectedWeek == week) "● " + week + "주"
                                                else week.toString() + "주")
                                        }
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    days.forEach { day ->
                                        TextButton(onClick = { selectedDay = day }) {
                                            Text(if (selectedDay == day) "● " + day + "요일"
                                                else day.toString() + "요일")
                                        }
                                    }
                                }
                                Text("전체 목표 추천안과 차이 · " + (selectedWeek ?: "-") + "주",
                                    fontWeight = FontWeight.SemiBold)
                                val changes = selectedWeek?.let {
                                    sportShadowChanges(productionPlan, chosenPlan, it)
                                }.orEmpty()
                                if (changes.isEmpty()) Text("운동·세트·반복·중량 차이 없음")
                                else changes.forEach { Text("• " + it) }
                            }
                            items(chosenPlan.items.filter {
                                it.weekNumber == selectedWeek && it.dayOfWeek == selectedDay
                            }.sortedBy(ProgramSkeletonItem::orderIndex),
                                key = ProgramSkeletonItem::localId) { exercise ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(exercise.exerciseName, fontWeight = FontWeight.SemiBold)
                                        ProgramSetPrescriptionResolver.resolve(exercise).forEach { set ->
                                            val load = when {
                                                set.loadState == ProgramLoadState.NOT_APPLICABLE ->
                                                    "중량 비적용"
                                                set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED &&
                                                    set.estimatedLoadKg != null ->
                                                    "약 " + sportShadowNumber(set.estimatedLoadKg) +
                                                        "kg (확인 필요)"
                                                set.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED ->
                                                    "중량 보정 필요"
                                                else -> sportShadowNumber(set.weightKg) + "kg"
                                            }
                                            val reps = if (set.seconds > 0)
                                                set.seconds.toString() + "초"
                                            else set.reps.toString() + "회"
                                            val effort = set.expectedRir?.let {
                                                "예상 RIR " + sportShadowNumber(it)
                                            } ?: "RIR 미확정"
                                            Text(set.setIndex.toString() + "세트 · " + reps +
                                                " · " + load + " · " + effort +
                                                " · 휴식 " + exercise.restSeconds + "초",
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("후보에 오르지 않은 운동", style = MaterialTheme.typography.titleMedium)
                        Text("이번 평가 후보에 없는 운동입니다. 불선정 원인이 장비·시간·회복·점수 " +
                            "중 무엇인지는 추가 탐색 기록 없이는 확정할 수 없습니다.",
                            style = MaterialTheme.typography.bodySmall)
                        if (report.notInEvaluatedPool.isEmpty()) Text("해당 운동 없음")
                        report.notInEvaluatedPool.take(12).forEach {
                            Text("• " + it)
                        }
                        if (report.notInEvaluatedPool.size > 12) Text(
                            "이외 " + (report.notInEvaluatedPool.size - 12) + "개 · 원인 미검증"
                        )
                    }
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().testTag("b_coach_sport_shadow_close")
                ) { Text("닫기 · 전체 목표 추천안으로 돌아가기") }
            }
        }
    }
}
