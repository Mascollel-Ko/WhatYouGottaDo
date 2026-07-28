package com.training.trackplanner.data

import android.content.Context
import android.net.Uri

internal class BackupImportService(
    private val restoreImporter: suspend (RecordCsvImportData.Restore) -> RecordCsvTransferResult,
    private val dailyTimeseriesImporter: suspend (RecordCsvImportData.DailyTimeseries) -> RecordCsvTransferResult,
    private val canonicalizer: BackupRestoreCanonicalizer,
    private val canonicalStableKeys: () -> Set<String>,
    private val reportStore: DataTransferReportStore? = null
) {
    suspend fun import(
        context: Context,
        uri: Uri,
        onReportChanged: (DataTransferReport) -> Unit = {}
    ): RecordCsvTransferResult {
        val session = DataTransferReportSession(
            store = checkNotNull(reportStore) { "DataTransferReportStore is required for URI import." },
            operation = DataTransferOperation.RESTORE,
            fileDisplayName = uri.lastPathSegment.orEmpty(),
            onChanged = onReportChanged
        )
        session.begin()
        try {
            session.stage(DataTransferStages.READING)
            val text = try {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { reader -> reader.readText() }
            } catch (error: Throwable) {
                throw DataTransferFormatException(
                    DataTransferDiagnosticCodes.RESTORE_MANIFEST_INVALID,
                    "복원 파일을 읽을 수 없습니다. ${error.message.orEmpty()}".trim()
                )
            } ?: throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_MANIFEST_INVALID,
                "복원 파일을 열 수 없습니다."
            )

            session.stage(DataTransferStages.PARSING)
            val parsed = RecordCsvBackupRestore.parse(text)
            session.stage(DataTransferStages.PLANNING)
            val warnings = mutableListOf<DataTransferDiagnostic>()
            val result = when (parsed) {
                is RecordCsvImportData.Restore -> {
                    val canonicalized = canonicalizer.canonicalize(parsed, canonicalStableKeys())
                    warnings += canonicalized.warnings
                    if (canonicalized.errors.isNotEmpty()) {
                        throw DataTransferFailure(
                            session.finish(
                                warnings = warnings,
                                errors = canonicalized.errors
                            )
                        )
                    }
                    session.counts(restoreCounts(canonicalized.data))
                    session.stage(DataTransferStages.RESTORING)
                    restoreImporter(canonicalized.data)
                }
                is RecordCsvImportData.DailyTimeseries -> {
                    session.counts(mapOf("daily_timeseries" to parsed.rows.size))
                    session.stage(DataTransferStages.RESTORING)
                    dailyTimeseriesImporter(parsed)
                }
            }
            session.stage(DataTransferStages.POSTRESTORE_VALIDATION)
            session.finish(warnings = warnings)
            return result
        } catch (failure: DataTransferFailure) {
            throw failure
        } catch (error: Throwable) {
            val diagnostic = when (error) {
                is DataTransferFormatException -> DataTransferDiagnostic(
                    code = error.diagnosticCode,
                    messageKo = error.message,
                    stage = session.report.currentStage
                )
                else -> DataTransferDiagnostic(
                    code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                    messageKo = error.message ?: "복원 중 알 수 없는 오류가 발생했습니다.",
                    stage = session.report.currentStage
                )
            }
            throw DataTransferFailure(session.finish(errors = listOf(diagnostic)))
        }
    }

    internal suspend fun importText(text: String): RecordCsvTransferResult =
        when (val data = RecordCsvBackupRestore.parse(text)) {
            is RecordCsvImportData.Restore -> {
                val canonicalized = canonicalizer.canonicalize(data, canonicalStableKeys())
                if (canonicalized.errors.isNotEmpty()) {
                    throw DataTransferFormatException(
                        canonicalized.errors.first().code,
                        canonicalized.errors.first().messageKo
                    )
                }
                restoreImporter(canonicalized.data)
            }
            is RecordCsvImportData.DailyTimeseries -> dailyTimeseriesImporter(data)
        }

    private fun restoreCounts(data: RecordCsvImportData.Restore): Map<String, Int> = linkedMapOf(
        "exercise" to data.exerciseRows.size,
        "daily_metric" to data.dailyRows.size,
        "daily_check_in" to data.checkInRows.size,
        "smash_speed" to data.smashSpeedRows.size,
        "workout_entry" to data.setRows.map(RestoreSetRow::entryKey).distinct().size,
        "workout_set" to data.setRows.size,
        "program" to (data.programSnapshot?.programs?.size ?: 0),
        "program_item" to (data.programSnapshot?.items?.size ?: 0)
    )
}
