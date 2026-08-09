package com.training.trackplanner.data

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

enum class DataTransferOperation {
    BACKUP,
    RESTORE
}

enum class DataTransferStatus {
    RUNNING,
    SUCCESS,
    WARNING,
    FAILURE
}

data class DataTransferStage(
    val name: String,
    val startedAt: Long,
    val completedAt: Long? = null
)

data class DataTransferDiagnostic(
    val code: String,
    val messageKo: String,
    val stage: String,
    val entityType: String = "",
    val entityRowId: Long? = null,
    val programStableKey: String = "",
    val programName: String = "",
    val programItemRowId: Long? = null,
    val week: Int? = null,
    val day: Int? = null,
    val order: Int? = null,
    val workoutDate: String = "",
    val sourceExerciseStableKey: String = "",
    val sourceExerciseName: String = "",
    val attemptedCanonicalStableKey: String = "",
    val resolutionMethod: String = "",
    val candidateCount: Int? = null
)

data class DataTransferReport(
    val operationId: String,
    val operation: DataTransferOperation,
    val status: DataTransferStatus,
    val startedAt: Long,
    val completedAt: Long? = null,
    val fileDisplayName: String = "",
    val currentStage: String,
    val stages: List<DataTransferStage> = emptyList(),
    val entityCounts: Map<String, Int> = emptyMap(),
    val warnings: List<DataTransferDiagnostic> = emptyList(),
    val errors: List<DataTransferDiagnostic> = emptyList()
) {
    fun detailText(): String = buildString {
        appendLine("작업: ${if (operation == DataTransferOperation.BACKUP) "기록 백업" else "기록 복원"}")
        appendLine("상태: ${status.name}")
        appendLine("작업 ID: $operationId")
        appendLine("파일: ${fileDisplayName.ifBlank { "(알 수 없음)" }}")
        appendLine("시작: $startedAt")
        appendLine("완료: ${completedAt ?: "-"}")
        appendLine("현재 단계: $currentStage")
        if (stages.isNotEmpty()) {
            appendLine()
            appendLine("[단계]")
            stages.forEach { stage ->
                appendLine("- ${stage.name}: ${stage.startedAt} ~ ${stage.completedAt ?: "진행 중"}")
            }
        }
        if (entityCounts.isNotEmpty()) {
            appendLine()
            appendLine("[개수]")
            entityCounts.toSortedMap().forEach { (key, value) -> appendLine("- $key: $value") }
        }
        if (warnings.isNotEmpty()) {
            appendLine()
            appendLine("[경고]")
            warnings.forEach { appendLine("- ${it.code}: ${it.messageKo}${it.contextText()}") }
        }
        if (errors.isNotEmpty()) {
            appendLine()
            appendLine("[오류]")
            errors.forEach { appendLine("- ${it.code}: ${it.messageKo}${it.contextText()}") }
        }
    }.trimEnd()

    private fun DataTransferDiagnostic.contextText(): String {
        val values = listOfNotNull(
            entityType.takeIf(String::isNotBlank)?.let { "entity=$it" },
            entityRowId?.let { "row=$it" },
            programName.takeIf(String::isNotBlank)?.let { "program=$it" },
            programStableKey.takeIf(String::isNotBlank)?.let { "programKey=$it" },
            programItemRowId?.let { "item=$it" },
            week?.let { "week=$it" },
            day?.let { "day=$it" },
            order?.let { "order=$it" },
            workoutDate.takeIf(String::isNotBlank)?.let { "date=$it" },
            sourceExerciseStableKey.takeIf(String::isNotBlank)?.let { "sourceKey=$it" },
            sourceExerciseName.takeIf(String::isNotBlank)?.let { "sourceName=$it" },
            attemptedCanonicalStableKey.takeIf(String::isNotBlank)?.let { "targetKey=$it" },
            resolutionMethod.takeIf(String::isNotBlank)?.let { "resolution=$it" },
            candidateCount?.let { "candidates=$it" }
        )
        return if (values.isEmpty()) "" else " (${values.joinToString(", ")})"
    }

    companion object {
        fun running(operation: DataTransferOperation, fileDisplayName: String): DataTransferReport {
            val now = System.currentTimeMillis()
            return DataTransferReport(
                operationId = UUID.randomUUID().toString(),
                operation = operation,
                status = DataTransferStatus.RUNNING,
                startedAt = now,
                fileDisplayName = fileDisplayName,
                currentStage = DataTransferStages.STARTED,
                stages = listOf(DataTransferStage(DataTransferStages.STARTED, now))
            )
        }
    }
}

object DataTransferDiagnosticCodes {
    const val PROGRAM_EXERCISE_STABLE_KEY_MISSING = "PROGRAM_EXERCISE_STABLE_KEY_MISSING"
    const val PROGRAM_EXERCISE_STABLE_KEY_UNRESOLVED = "PROGRAM_EXERCISE_STABLE_KEY_UNRESOLVED"
    const val WORKOUT_EXERCISE_STABLE_KEY_MISSING = "WORKOUT_EXERCISE_STABLE_KEY_MISSING"
    const val WORKOUT_EXERCISE_STABLE_KEY_UNRESOLVED = "WORKOUT_EXERCISE_STABLE_KEY_UNRESOLVED"
    const val AMBIGUOUS_LEGACY_EXERCISE_SPLIT = "AMBIGUOUS_LEGACY_EXERCISE_SPLIT"
    const val LEGACY_PLACEHOLDER_EXERCISE = "LEGACY_PLACEHOLDER_EXERCISE"
    const val LEGACY_DELETED_EXERCISE = "LEGACY_DELETED_EXERCISE"
    const val ORPHAN_PROGRAM_ITEM = "ORPHAN_PROGRAM_ITEM"
    const val ORPHAN_WORKOUT_SET = "ORPHAN_WORKOUT_SET"
    const val WORKOUT_ENTRY_WITHOUT_SET = "WORKOUT_ENTRY_WITHOUT_SET"
    const val BACKUP_FILE_OPEN_FAILED = "BACKUP_FILE_OPEN_FAILED"
    const val BACKUP_SERIALIZATION_FAILED = "BACKUP_SERIALIZATION_FAILED"
    const val BACKUP_FILE_WRITE_FAILED = "BACKUP_FILE_WRITE_FAILED"
    const val BACKUP_POSTWRITE_VALIDATION_FAILED = "BACKUP_POSTWRITE_VALIDATION_FAILED"
    const val RESTORE_SCHEMA_UNSUPPORTED = "RESTORE_SCHEMA_UNSUPPORTED"
    const val RESTORE_MANIFEST_INVALID = "RESTORE_MANIFEST_INVALID"
    const val RESTORE_HASH_MISMATCH = "RESTORE_HASH_MISMATCH"
    const val RESTORE_COUNT_MISMATCH = "RESTORE_COUNT_MISMATCH"
    const val RESTORE_CANONICAL_KEY_UNRESOLVED = "RESTORE_CANONICAL_KEY_UNRESOLVED"
    const val RESTORE_IDENTITY_CONTRADICTION = "RESTORE_IDENTITY_CONTRADICTION"
    const val RESTORE_PREFLIGHT_STALE = "RESTORE_PREFLIGHT_STALE"
    const val RESTORE_HISTORICAL_STUB_CREATED = "RESTORE_HISTORICAL_STUB_CREATED"
    const val RESTORE_LEGACY_ENTRY_IDENTITY_FALLBACK = "RESTORE_LEGACY_ENTRY_IDENTITY_FALLBACK"
    const val RESTORE_PROGRAM_PARENT_MISSING = "RESTORE_PROGRAM_PARENT_MISSING"
}

object DataTransferStages {
    const val STARTED = "작업 시작"
    const val LOADING = "데이터 불러오기"
    const val PREFLIGHT = "무결성 사전검사"
    const val SERIALIZING = "백업 직렬화"
    const val WRITING = "파일 쓰기"
    const val POSTWRITE_VALIDATION = "작성 결과 재검증"
    const val READING = "파일 읽기"
    const val PARSING = "형식 및 무결성 검사"
    const val PLANNING = "복원 계획 생성"
    const val RESTORING = "트랜잭션 복원"
    const val POSTRESTORE_VALIDATION = "복원 결과 검증"
    const val COMPLETED = "완료"
}

class DataTransferFailure(
    val report: DataTransferReport
) : IllegalStateException(report.detailText())

class DataTransferFormatException(
    val diagnosticCode: String,
    override val message: String
) : IllegalArgumentException(message)

internal class DataTransferReportStore(
    private val appMetaDao: AppMetaDao
) {
    suspend fun save(report: DataTransferReport): DataTransferReport {
        appMetaDao.upsert(
            AppMeta(
                key = "$KEY_PREFIX${report.operationId}",
                value = DataTransferReportCodec.encode(report),
                updatedAt = report.completedAt ?: report.startedAt
            )
        )
        appMetaDao.trimLatest(KEY_PREFIX_SQL, RETENTION_COUNT)
        return report
    }

    suspend fun latest(): DataTransferReport? =
        appMetaDao.latestByPrefix(KEY_PREFIX_SQL)?.value?.let(DataTransferReportCodec::decode)

    suspend fun recent(): List<DataTransferReport> =
        appMetaDao.latestByPrefix(KEY_PREFIX_SQL, RETENTION_COUNT)
            .mapNotNull { DataTransferReportCodec.decode(it.value) }

    companion object {
        const val RETENTION_COUNT = 20
        private const val KEY_PREFIX = "data_transfer_report_"
        private const val KEY_PREFIX_SQL = "data_transfer_report_%"
    }
}

internal class DataTransferReportSession(
    private val store: DataTransferReportStore,
    operation: DataTransferOperation,
    fileDisplayName: String,
    private val onChanged: (DataTransferReport) -> Unit
) {
    var report: DataTransferReport = DataTransferReport.running(operation, fileDisplayName)
        private set

    suspend fun begin() {
        publish(report)
    }

    suspend fun stage(name: String) {
        val now = System.currentTimeMillis()
        report = report.copy(
            currentStage = name,
            stages = report.stages.map { stage ->
                if (stage.completedAt == null) stage.copy(completedAt = now) else stage
            } + DataTransferStage(name, now)
        )
        publish(report)
    }

    suspend fun counts(values: Map<String, Int>) {
        report = report.copy(entityCounts = values)
        publish(report)
    }

    suspend fun finish(
        warnings: List<DataTransferDiagnostic> = report.warnings,
        errors: List<DataTransferDiagnostic> = report.errors
    ): DataTransferReport {
        val now = System.currentTimeMillis()
        val status = when {
            errors.isNotEmpty() -> DataTransferStatus.FAILURE
            warnings.isNotEmpty() -> DataTransferStatus.WARNING
            else -> DataTransferStatus.SUCCESS
        }
        report = report.copy(
            status = status,
            completedAt = now,
            currentStage = DataTransferStages.COMPLETED,
            stages = report.stages.map { stage ->
                if (stage.completedAt == null) stage.copy(completedAt = now) else stage
            } + DataTransferStage(DataTransferStages.COMPLETED, now, now),
            warnings = warnings,
            errors = errors
        )
        publish(report)
        return report
    }

    private suspend fun publish(value: DataTransferReport) {
        report = store.save(value)
        onChanged(report)
    }
}

private object DataTransferReportCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(report: DataTransferReport): String = buildString {
        appendLine(
            fields(
                "R",
                report.operationId,
                report.operation.name,
                report.status.name,
                report.startedAt.toString(),
                report.completedAt?.toString().orEmpty(),
                report.fileDisplayName,
                report.currentStage
            )
        )
        report.stages.forEach { stage ->
            appendLine(fields("S", stage.name, stage.startedAt.toString(), stage.completedAt?.toString().orEmpty()))
        }
        report.entityCounts.toSortedMap().forEach { (key, count) ->
            appendLine(fields("C", key, count.toString()))
        }
        report.warnings.forEach { appendLine(encodeDiagnostic("W", it)) }
        report.errors.forEach { appendLine(encodeDiagnostic("E", it)) }
    }

    fun decode(value: String): DataTransferReport? = runCatching {
        val rows = value.lineSequence().filter(String::isNotBlank).map(::decodeFields).toList()
        val root = rows.first { it.firstOrNull() == "R" }
        val stages = rows.filter { it.firstOrNull() == "S" }.map { fields ->
            DataTransferStage(fields[1], fields[2].toLong(), fields[3].toLongOrNull())
        }
        val counts = rows.filter { it.firstOrNull() == "C" }.associate { fields ->
            fields[1] to fields[2].toInt()
        }
        DataTransferReport(
            operationId = root[1],
            operation = enumValueOf(root[2]),
            status = enumValueOf(root[3]),
            startedAt = root[4].toLong(),
            completedAt = root[5].toLongOrNull(),
            fileDisplayName = root[6],
            currentStage = root[7],
            stages = stages,
            entityCounts = counts,
            warnings = rows.filter { it.firstOrNull() == "W" }.map(::decodeDiagnostic),
            errors = rows.filter { it.firstOrNull() == "E" }.map(::decodeDiagnostic)
        )
    }.getOrNull()

    private fun encodeDiagnostic(type: String, value: DataTransferDiagnostic): String =
        fields(
            type,
            value.code,
            value.messageKo,
            value.stage,
            value.entityType,
            value.entityRowId?.toString().orEmpty(),
            value.programStableKey,
            value.programName,
            value.programItemRowId?.toString().orEmpty(),
            value.week?.toString().orEmpty(),
            value.day?.toString().orEmpty(),
            value.order?.toString().orEmpty(),
            value.workoutDate,
            value.sourceExerciseStableKey,
            value.sourceExerciseName,
            value.attemptedCanonicalStableKey,
            value.resolutionMethod,
            value.candidateCount?.toString().orEmpty()
        )

    private fun decodeDiagnostic(fields: List<String>) = DataTransferDiagnostic(
        code = fields[1],
        messageKo = fields[2],
        stage = fields[3],
        entityType = fields[4],
        entityRowId = fields[5].toLongOrNull(),
        programStableKey = fields[6],
        programName = fields[7],
        programItemRowId = fields[8].toLongOrNull(),
        week = fields[9].toIntOrNull(),
        day = fields[10].toIntOrNull(),
        order = fields[11].toIntOrNull(),
        workoutDate = fields[12],
        sourceExerciseStableKey = fields[13],
        sourceExerciseName = fields[14],
        attemptedCanonicalStableKey = fields[15],
        resolutionMethod = fields[16],
        candidateCount = fields[17].toIntOrNull()
    )

    private fun fields(vararg values: String): String =
        values.joinToString("\t") { encoder.encodeToString(it.toByteArray(StandardCharsets.UTF_8)) }

    private fun decodeFields(line: String): List<String> =
        line.split('\t').map { String(decoder.decode(it), StandardCharsets.UTF_8) }
}
