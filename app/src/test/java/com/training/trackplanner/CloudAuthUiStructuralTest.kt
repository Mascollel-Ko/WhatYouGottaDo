package com.training.trackplanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudAuthUiStructuralTest {
    @Test fun homeKeepsOnboardingActionAndAddsAccountBeforeLanguageChips() {
        val source = source("src/main/java/com/training/trackplanner/HomeScreen.kt")
        assertTrue(source.contains("프로그램으로 시작하기"))
        assertTrue(source.contains("onProgramTargetPositioned"))
        assertTrue(source.contains("cloud_account_control_description"))
        assertTrue(source.indexOf("cloud_account_control_description") < source.indexOf("AppLanguageSelector()"))
    }

    @Test fun accountActionsLiveInDialogBodyAndCloseIsTheOnlyAlertAction() {
        val source = source("src/main/java/com/training/trackplanner/HomeScreen.kt")
        val start = source.indexOf("private fun CloudAccountDialog")
        val end = source.indexOf("@Composable", start + 1)
        val dialog = source.substring(start, end)
        assertFalse(dialog.contains("dismissButton"))
        assertTrue(dialog.indexOf("cloud_upload_now") < dialog.indexOf("confirmButton"))
        assertTrue(dialog.indexOf("cloud_restore_now") < dialog.indexOf("confirmButton"))
        assertTrue(dialog.indexOf("cloud_logout") < dialog.indexOf("confirmButton"))
        assertTrue(dialog.contains("cloud_close"))
    }

    @Test fun authStringsHaveKoreanAndEnglishResources() {
        val ko = source("src/main/res/values/strings.xml")
        val en = source("src/main/res/values-en/strings.xml")
        listOf("cloud_first_launch_body", "cloud_guest_warning_body", "cloud_upload_now", "cloud_restore_now", "cloud_current_discovery_failed")
            .forEach { key ->
                assertTrue(ko.contains("name=\"$key\""))
                assertTrue(en.contains("name=\"$key\""))
            }
    }

    @Test fun communityUsesTheCanonicalApplicationSession() {
        val source = source("src/main/java/com/training/trackplanner/CommunityViewModel.kt")
        assertTrue(source.contains("val session: StateFlow<CloudAuthSession?> = auth.session"))
        assertTrue(source.contains("val current = auth.refreshIfNeeded()"))
        assertFalse(source.contains("MutableStateFlow(auth.currentSession())"))
    }

    @Test fun trainingUsesTheSharedSessionManager() {
        val source = source("src/main/java/com/training/trackplanner/TrainingViewModel.kt")
        assertTrue(source.contains("CloudAuthSessionManager.forApplication(application)"))
        assertTrue(source.contains("authSessionManager.session.collect"))
    }

    private fun source(relativePath: String): String = sequenceOf(
        File(relativePath), File("app", relativePath), File("..", relativePath)
    ).first(File::isFile).readText()
}
