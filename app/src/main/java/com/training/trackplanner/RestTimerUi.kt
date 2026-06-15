package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp

@Composable
internal fun RestTimerMiniBar(
    state: RestTimerState,
    onStop: () -> Unit
) {
    if (!state.isActive) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isFinished) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isFinished) "휴식 종료" else formatSeconds(state.remainingSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.nextHint.ifBlank { state.exerciseName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            OutlinedButton(onClick = onStop) {
                Text("중지")
            }
        }
    }
}

@Composable
internal fun RestTimerPermissionHint(
    notificationPermissionNeeded: Boolean,
    overlayPermissionGranted: Boolean,
    onRequestNotification: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    if (!notificationPermissionNeeded && overlayPermissionGranted) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (notificationPermissionNeeded) {
                Text(
                    text = "앱 밖에서도 휴식 종료를 보려면 알림 권한이 필요합니다.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestNotification
                ) {
                    Text("알림 허용")
                }
            }
            if (!overlayPermissionGranted) {
                Text(
                    text = "작은 오버레이는 선택 기능입니다. 권한이 없어도 타이머는 동작합니다.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenOverlaySettings
                ) {
                    Text("오버레이 설정")
                }
            }
        }
    }
}
