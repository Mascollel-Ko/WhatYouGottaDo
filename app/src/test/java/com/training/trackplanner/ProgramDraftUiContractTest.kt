package com.training.trackplanner

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgramDraftUiContractTest {
    @Test fun programListHasDraftResumeAndManualSaveAndTwoMinuteAutosave() {
        val screen = File("src/main/java/com/training/trackplanner/PlanScreen.kt").readText()
        assertTrue(screen.contains("임시저장한 곳에서 계속하기"))
        assertTrue(screen.contains("마지막 수정:"))
        assertTrue(screen.contains("Text(\"임시저장\")"))
        assertTrue(screen.contains("delay(ProgramDraftIdentity.AUTO_SAVE_INTERVAL_MILLIS)"))
        assertTrue(screen.contains("deleteProgramDraftAfterFinalSave(draftId)"))
    }
}
