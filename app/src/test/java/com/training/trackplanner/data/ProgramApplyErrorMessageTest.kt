package com.training.trackplanner.data

import android.database.sqlite.SQLiteFullException
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProgramApplyErrorMessageTest {
    @Test fun mapsAllExplicitPreflightFailuresToActionableKorean() {
        ProgramApplyFailureReason.values().forEach { reason ->
            val message = ProgramApplyErrorMessage.from(ProgramApplyException(reason))
            assertTrue(message.contains("주세요"))
            assertTrue(!message.contains("Exception"))
        }
    }

    @Test fun mapsKnownStorageFailuresWithoutTechnicalStacktraces() {
        assertTrue(ProgramApplyErrorMessage.from(SQLiteFullException()).contains("저장 공간"))
        assertTrue(ProgramApplyErrorMessage.from(IOException("disk failed")).contains("복구본"))
        assertTrue(ProgramApplyErrorMessage.from(IllegalStateException("private internals")).contains("계획을 적용하지 못했습니다"))
    }
}
