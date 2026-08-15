package com.training.trackplanner.data

import android.content.Context

internal data class LegacyExerciseImportMapping(
    val oldStableKey: String,
    val oldName: String,
    val canonicalStableKey: String,
    val canonicalName: String,
    val importRule: String
)

internal sealed interface LegacyExerciseResolution {
    data class Resolved(
        val canonicalStableKey: String,
        val method: String,
        val canonicalName: String = ""
    ) : LegacyExerciseResolution

    data class Dropped(
        val diagnostic: DataTransferDiagnostic
    ) : LegacyExerciseResolution

    data class Rejected(
        val diagnostic: DataTransferDiagnostic
    ) : LegacyExerciseResolution
}

internal class LegacyExerciseImportMapper private constructor(
    mappings: List<LegacyExerciseImportMapping>
) {
    private val byStableKey = mappings.associateBy(LegacyExerciseImportMapping::oldStableKey)

    fun resolve(
        oldStableKey: String,
        oldName: String,
        equipment: String,
        canonicalStableKeys: Set<String>,
        stage: String,
        entityType: String,
        entityRowId: Long? = null
    ): LegacyExerciseResolution {
        val sourceKey = oldStableKey.trim()
        if (sourceKey in canonicalStableKeys) {
            return LegacyExerciseResolution.Resolved(sourceKey, "CANONICAL_STABLE_KEY")
        }
        val mapping = sourceKey.takeIf(String::isNotBlank)?.let(byStableKey::get)
        if (mapping == null) {
            return rejected(
                code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                message = "이전 백업의 운동을 현재 정본 stableKey로 해석할 수 없습니다.",
                sourceKey = sourceKey,
                sourceName = oldName,
                stage = stage,
                entityType = entityType,
                entityRowId = entityRowId
            )
        }
        return when (mapping.importRule) {
            "DIRECT", "CANONICAL", "CANONICAL_RENAME" -> {
                if (mapping.canonicalStableKey in canonicalStableKeys) {
                    LegacyExerciseResolution.Resolved(
                        mapping.canonicalStableKey,
                        "LEGACY_${mapping.importRule}",
                        mapping.canonicalName
                    )
                } else {
                    rejected(
                        code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                        message = "legacy map의 정본 stableKey가 현재 운동 목록에 없습니다.",
                        sourceKey = sourceKey,
                        sourceName = oldName,
                        targetKey = mapping.canonicalStableKey,
                        stage = stage,
                        entityType = entityType,
                        entityRowId = entityRowId
                    )
                }
            }
            "DROP_PLACEHOLDER_WITH_WARNING" -> LegacyExerciseResolution.Dropped(
                DataTransferDiagnostic(
                    code = DataTransferDiagnosticCodes.LEGACY_PLACEHOLDER_EXERCISE,
                    messageKo = "이전 CSV 복원용 임시 운동을 건너뛰었습니다.",
                    stage = stage,
                    entityType = entityType,
                    entityRowId = entityRowId,
                    sourceExerciseStableKey = sourceKey,
                    sourceExerciseName = oldName,
                    resolutionMethod = mapping.importRule
                )
            )
            "DROP_DELETED_EXERCISE_WITH_WARNING" -> if (entityType == "Exercise") {
                LegacyExerciseResolution.Dropped(
                    DataTransferDiagnostic(
                        code = DataTransferDiagnosticCodes.LEGACY_DELETED_EXERCISE,
                        messageKo = "현재 정본 목록에서 삭제된 이전 운동 정의를 건너뛰었습니다.",
                        stage = stage,
                        entityType = entityType,
                        entityRowId = entityRowId,
                        sourceExerciseStableKey = sourceKey,
                        sourceExerciseName = oldName,
                        resolutionMethod = mapping.importRule
                    )
                )
            } else {
                rejected(
                    code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                    message = "현재 정본 목록에서 삭제된 이전 운동을 참조하고 있습니다.",
                    sourceKey = sourceKey,
                    sourceName = oldName,
                    stage = stage,
                    entityType = entityType,
                    entityRowId = entityRowId,
                    method = mapping.importRule
                )
            }
            else -> rejected(
                code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                message = "자동 변환이 승인되지 않은 이전 운동입니다.",
                sourceKey = sourceKey,
                sourceName = oldName,
                stage = stage,
                entityType = entityType,
                entityRowId = entityRowId,
                method = mapping.importRule
            )
        }
    }

    fun mappings(): List<LegacyExerciseImportMapping> = byStableKey.values.sortedBy { it.oldStableKey }

    private fun rejected(
        code: String,
        message: String,
        sourceKey: String,
        sourceName: String,
        stage: String,
        entityType: String,
        entityRowId: Long?,
        targetKey: String = "",
        method: String = "EXACT_LEGACY_MAP"
    ) = LegacyExerciseResolution.Rejected(
        DataTransferDiagnostic(
            code = code,
            messageKo = message,
            stage = stage,
            entityType = entityType,
            entityRowId = entityRowId,
            sourceExerciseStableKey = sourceKey,
            sourceExerciseName = sourceName,
            attemptedCanonicalStableKey = targetKey,
            resolutionMethod = method,
            candidateCount = 0
        )
    )

    companion object {
        private const val ASSET = "exercise_legacy_import_map.csv"

        fun fromAssets(context: Context): LegacyExerciseImportMapper {
            val rows = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.lineSequence()
                    .filter(String::isNotBlank)
                    .map(SeedData::parseCsvLine)
                    .toList()
            }
            val header = rows.first()
            val index = header.withIndex().associate { (position, value) -> value to position }
            fun List<String>.value(name: String) = getOrNull(index.getValue(name)).orEmpty().trim()
            return LegacyExerciseImportMapper(
                rows.drop(1).map { row ->
                    LegacyExerciseImportMapping(
                        oldStableKey = row.value("old_stable_key"),
                        oldName = row.value("old_name"),
                        canonicalStableKey = row.value("canonical_stable_key"),
                        canonicalName = row.value("canonical_name"),
                        importRule = row.value("import_rule")
                    )
                }
            )
        }

        internal fun fromMappings(
            mappings: List<LegacyExerciseImportMapping>
        ): LegacyExerciseImportMapper = LegacyExerciseImportMapper(mappings)
    }
}
