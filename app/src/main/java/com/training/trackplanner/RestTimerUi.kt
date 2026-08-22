package com.training.trackplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object RestTimerForegroundBarPolicy {
    fun presentationIdentity(state: RestTimerState): String =
        listOf(
            state.endAtEpochMillis,
            state.targetRecordDate,
            state.targetEntryId,
            state.targetSetId
        ).joinToString(separator = "|")

    fun visible(state: RestTimerState, dismissedIdentity: String?): Boolean =
        state.isActive && dismissedIdentity != presentationIdentity(state)

    fun progress(state: RestTimerState): Float =
        if (state.totalSeconds <= 0) 0f
        else (state.remainingSeconds.toFloat() / state.totalSeconds).coerceIn(0f, 1f)

    fun emphasized(state: RestTimerState): Boolean = state.isRunning && state.remainingSeconds in 1..5

    fun target(state: RestTimerState, requestId: Long = 0): RestTimerTarget = RestTimerTarget(
        recordDate = state.targetRecordDate,
        entryId = state.targetEntryId,
        setId = state.targetSetId,
        navigationRequestId = requestId
    )
}

@Composable
internal fun RestTimerForegroundBar(
    state: RestTimerState,
    onOpenTarget: () -> Unit,
    onDismiss: () -> Unit
) {
    val emphasized = RestTimerForegroundBarPolicy.emphasized(state)
    val containerColor = when {
        emphasized -> MaterialTheme.colorScheme.errorContainer
        state.isFinished -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        emphasized -> MaterialTheme.colorScheme.onErrorContainer
        state.isFinished -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenTarget)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.isFinished) {
                            "휴식 종료"
                        } else {
                            "휴식 ${formatRestTimerClock(state.remainingSeconds)}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "다음: ${state.nextHint.ifBlank { state.exerciseName }}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = onDismiss
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "휴식 타이머 바 숨기기"
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(RestTimerForegroundBarPolicy.progress(state))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                        .background(if (emphasized) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

internal fun formatRestTimerClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

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
