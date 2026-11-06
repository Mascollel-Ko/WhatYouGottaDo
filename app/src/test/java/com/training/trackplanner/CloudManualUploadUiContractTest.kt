package com.training.trackplanner

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudManualUploadUiContractTest {
    @Test fun onlyCurrentAndAcknowledgedShowsSuccessAndLocksDuringUpload() {
        val home = File("src/main/java/com/training/trackplanner/HomeScreen.kt").readText()
        val vm = File("src/main/java/com/training/trackplanner/TrainingViewModel.kt").readText()
        val cloud = File("src/main/java/com/training/trackplanner/data/CloudBackupClient.kt").readText()
        assertTrue(home.contains("manualCloudUploadStatus = \"uploading\""))
        assertTrue(home.contains("onDismissRequest = { if (!loading)"))
        assertTrue(home.contains("성공하였습니다!"))
        assertTrue(home.contains("manualCloudUploadStatus = if (acknowledged) \"success\" else \"failed\""))
        assertTrue(vm.contains("onCompleted(result.acknowledged)"))
        assertTrue(cloud.contains("finalized.optString(\"status\") != \"CURRENT\""))
    }
}
