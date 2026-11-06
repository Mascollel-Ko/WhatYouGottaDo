package com.training.trackplanner.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalBackupTimestampTest {
    @Test fun renderedReportTimesAreLocalWallClockWithSeconds() {
        val saved = Instant.parse("2026-11-06T02:15:00Z").toEpochMilli()
        assertEquals(
            "2026-11-06 11:15:00",
            formatTransferTimestamp(saved, Locale.KOREAN, ZoneId.of("Asia/Seoul"))
        )
    }

    @Test fun diagnosticFileUsesReadableTimeRatherThanRawEpoch() {
        val instant = Instant.parse("2026-11-06T02:15:00Z").toEpochMilli()
        val report = DataTransferReport(
            operationId = "sample",
            operation = DataTransferOperation.BACKUP,
            status = DataTransferStatus.SUCCESS,
            startedAt = instant,
            completedAt = instant + 1000L,
            fileDisplayName = "sample.csv",
            currentStage = DataTransferStages.COMPLETED,
            stages = listOf(DataTransferStage(DataTransferStages.WRITING, instant, instant + 1000L))
        )
        val detail = report.detailText()
        assertTrue(Regex("""시작: \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").containsMatchIn(detail))
        assertFalse(detail.contains("시작: $instant"))
        assertTrue(detail.contains("파일: sample.csv"))
    }
}
