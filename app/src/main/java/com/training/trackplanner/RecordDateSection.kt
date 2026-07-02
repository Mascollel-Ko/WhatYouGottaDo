package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate


@Composable
internal fun RecordDateSwitcher(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        val dateButtonModifier = Modifier.defaultMinSize(minWidth = 0.dp)
        val dateButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    modifier = dateButtonModifier.weight(1f),
                    contentPadding = dateButtonPadding
                ) {
                    Text("이전날")
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = date.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = onNext,
                    modifier = dateButtonModifier.weight(1f),
                    contentPadding = dateButtonPadding
                ) {
                    Text("다음날")
                }
            }
            OutlinedButton(
                onClick = onOpenCalendar,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = dateButtonPadding
            ) {
                Text("달력")
            }
        }
    }
}

@Composable
internal fun EmptyRecordState(
    onAddExercise: () -> Unit,
    onOpenPlan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "이 날짜에는 아직 운동이 없습니다.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddExercise
            ) {
                Text("운동 추가")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenPlan
            ) {
                Text("프로그램에서 가져오기")
            }
        }
    }
}

