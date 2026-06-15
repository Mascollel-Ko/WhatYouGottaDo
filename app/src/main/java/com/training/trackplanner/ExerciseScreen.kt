package com.training.trackplanner

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.Exercise

@Composable
internal fun ExerciseScreen(viewModel: TrainingViewModel) {
    val exercises by viewModel.exercises.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("전체") }
    var selectedExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var manageMode by rememberSaveable { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Exercise?>(null) }
    var managementMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val visibleExercises = remember(exercises, showHidden) {
        exercises.filter { exercise -> showHidden || exercise.isActive }
    }
    val categories = remember(visibleExercises) {
        listOf("전체") + visibleExercises.map { it.category }.distinct()
    }
    val filtered = remember(visibleExercises, query, selectedCategory) {
        visibleExercises.filter { exercise ->
            val categoryMatches = selectedCategory == "전체" || exercise.category == selectedCategory
            val queryMatches = query.isBlank() ||
                exercise.name.contains(query, ignoreCase = true) ||
                exercise.detail1.contains(query, ignoreCase = true) ||
                exercise.detail2.contains(query, ignoreCase = true)
            categoryMatches && queryMatches
        }
    }
    val selectedExercise = remember(exercises, selectedExerciseId, filtered) {
        exercises.firstOrNull { it.id == selectedExerciseId } ?: filtered.firstOrNull()
    }

    deleteCandidate?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("운동 삭제") },
            text = { Text("기존 기록이나 프로그램에서 쓰인 운동은 삭제되지 않습니다. 그 경우 숨김을 사용하세요.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExerciseIfUnused(exercise.id) { deleted ->
                            managementMessage = if (deleted) {
                                "운동을 삭제했습니다."
                            } else {
                                "사용 중인 운동이라 삭제하지 않았습니다."
                            }
                        }
                        deleteCandidate = null
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("취소")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "운동",
                body = "운동 목록과 기본 기록 방식을 확인합니다."
            )
        }
        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                label = { Text("검색") },
                singleLine = true
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { manageMode = !manageMode }) {
                        Text(if (manageMode) "관리 종료" else "운동 목록 관리")
                    }
                    if (manageMode) {
                        FilterChip(
                            selected = showHidden,
                            onClick = { showHidden = !showHidden },
                            label = { Text("숨김 보기") }
                        )
                    }
                }
                managementMessage?.let { message -> Text(message) }
            }
        }
        selectedExercise?.let { exercise ->
            item {
                ExerciseDetailCard(exercise)
            }
        }
        items(filtered, key = { it.id }) { exercise ->
            ExerciseListItem(
                exercise = exercise,
                selected = exercise.id == selectedExercise?.id,
                onClick = { selectedExerciseId = exercise.id }
            )
            if (manageMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.setExerciseActive(exercise.id, !exercise.isActive)
                            managementMessage = if (exercise.isActive) {
                                "운동을 숨겼습니다."
                            } else {
                                "운동 숨김을 해제했습니다."
                            }
                        }
                    ) {
                        Text(if (exercise.isActive) "숨김" else "숨김 해제")
                    }
                    OutlinedButton(onClick = { deleteCandidate = exercise }) {
                        Text("삭제")
                    }
                }
            }
        }
    }
}
