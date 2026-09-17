package com.training.trackplanner

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

    @Test fun authStringsHaveKoreanAndEnglishResources() {
        val ko = source("src/main/res/values/strings.xml")
        val en = source("src/main/res/values-en/strings.xml")
        listOf("cloud_first_launch_body", "cloud_guest_warning_body", "cloud_upload_now", "cloud_restore_now")
            .forEach { key ->
                assertTrue(ko.contains("name=\"$key\""))
                assertTrue(en.contains("name=\"$key\""))
            }
    }

    private fun source(relativePath: String): String = sequenceOf(
        File(relativePath), File("app", relativePath), File("..", relativePath)
    ).first(File::isFile).readText()
}
