package com.training.trackplanner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.training.trackplanner.data.ProgramBuildProgressState

@Composable
internal fun PersonalizedGenerationProgressDialog(progress: ProgramBuildProgressState.Running) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("프로그램을 구성하는 중입니다") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text("최적의 프로그램이 아닐 수 있습니다.")
                    Text("생성 결과를 바탕으로 자신에게 맞게 프로그램을 조정해 보세요.")
                }
                Text("${progress.progressPercent}%", modifier = Modifier.testTag("personalized-progress-percent"))
                LinearProgressIndicator(progress = { progress.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().testTag("personalized-progress-bar"))
                Text(progress.message)
            }
        },
        confirmButton = {}
    )
}
