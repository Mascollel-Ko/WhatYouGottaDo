package com.training.trackplanner

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudManualUploadUiContractTest {
    @Test fun onlyCurrentAndAcknowledgedShowsSuccessAndLocksDuringUpload() {
        val home = File("src/main/java/com/training/trackplanner/HomeScreen.kt").readText()
        val vm = File("src/main/java/com/training/trackplanner/TrainingViewModel.kt").readText()
        val cloud = File("src/main/java/com/training/trackplanner/data/CloudBackupClient.kt").readText()
        val stringsKo = File("src/main/res/values/strings.xml").readText()
        val stringsEn = File("src/main/res/values-en/strings.xml").readText()

        // Manual upload must acknowledge CURRENT and local revision before showing success.
        assertTrue(home.contains("manualCloudUploadStatus = \"uploading\""))
        assertTrue(home.contains("acknowledged -> \"success\""))
        assertTrue(home.contains("code == \"LINEAGE_CONFLICT\" -> \"conflict\""))
        assertTrue(vm.contains("onCompleted(result.acknowledged, if (result.acknowledged) null else"))
        assertTrue(cloud.contains("finalized.optString(\"status\") != \"CURRENT\""))

        // An in-progress operation must not be dismissed or mistaken for success.
        assertTrue(home.contains("onDismissRequest = { if (!loading) manualCloudUploadStatus = \"idle\" }"))
        assertTrue(home.contains("if (!loading) {"))
        assertTrue(home.contains("\"success\" -> stringResource(R.string.cloud_upload_success)"))
        assertTrue(home.contains("Text(stringResource(R.string.cloud_upload_completed))"))
        assertTrue(stringsKo.contains("<string name=\"cloud_upload_completed\">성공하였습니다!"))
        assertTrue(stringsEn.contains("<string name=\"cloud_upload_completed\">Success!"))

        // Conflict resolution is distinct from an ordinary successful upload.
        assertTrue(home.contains("manualCloudUploadStatus = if (success) \"conflictSuccess\" else \"conflictFailed\""))
        assertTrue(home.contains("\"conflictSuccess\" -> stringResource(R.string.cloud_conflict_success)"))
        assertTrue(home.contains("Text(stringResource(R.string.cloud_conflict_completed))"))
        assertTrue(home.contains("\"failed\" -> stringResource(R.string.cloud_upload_failed)"))
        assertTrue(home.contains("\"conflictFailed\" -> stringResource(R.string.cloud_conflict_failed)"))
    }
}
