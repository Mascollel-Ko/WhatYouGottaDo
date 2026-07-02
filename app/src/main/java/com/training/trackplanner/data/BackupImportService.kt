package com.training.trackplanner.data

import android.content.Context
import android.net.Uri

internal class BackupImportService(
    private val restoreImporter: suspend (RecordCsvImportData.Restore) -> RecordCsvTransferResult,
    private val dailyTimeseriesImporter: suspend (RecordCsvImportData.DailyTimeseries) -> RecordCsvTransferResult
) {
    suspend fun import(context: Context, uri: Uri): RecordCsvTransferResult {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            reader.readText()
        } ?: error("복원 파일을 열 수 없습니다.")
        return importText(text)
    }

    suspend fun importText(text: String): RecordCsvTransferResult =
        when (val data = RecordCsvBackupRestore.parse(text)) {
            is RecordCsvImportData.Restore -> restoreImporter(data)
            is RecordCsvImportData.DailyTimeseries -> dailyTimeseriesImporter(data)
        }
}
