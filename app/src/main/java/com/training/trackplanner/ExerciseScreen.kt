package com.training.trackplanner

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ExerciseScreen(viewModel: TrainingViewModel) {
    val exercises by viewModel.exercises.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("전체") }
    var selectedExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }

    val categories = remember(exercises) {
        listOf("전체") + exercises.map { it.category }.distinct()
    }
    val filtered = remember(exercises, query, selectedCategory) {
        exercises.filter { exercise ->
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "운동",
                body = "운동의 분류와 기본 기록 방식을 확인하고 계획이나 오늘 기록에 사용할 항목을 고릅니다."
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
        }
    }
}
