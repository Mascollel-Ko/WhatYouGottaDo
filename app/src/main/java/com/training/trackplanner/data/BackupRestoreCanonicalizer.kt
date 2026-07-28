package com.training.trackplanner.data

internal data class RestoreCanonicalizationResult(
    val data: RecordCsvImportData.Restore,
    val warnings: List<DataTransferDiagnostic>,
    val errors: List<DataTransferDiagnostic>
)

internal class BackupRestoreCanonicalizer(
    private val legacyMapper: LegacyExerciseImportMapper
) {
    fun canonicalize(
        data: RecordCsvImportData.Restore,
        canonicalStableKeys: Set<String>
    ): RestoreCanonicalizationResult {
        val warnings = mutableListOf<DataTransferDiagnostic>()
        val errors = mutableListOf<DataTransferDiagnostic>()
        val isLegacy = data.manifest == null
        val exerciseRowsByKey = data.exerciseRows
            .filter { it.stableKey.isNotBlank() }
            .associateBy(RestoreExerciseRow::stableKey)
        val customKeyMap = mutableMapOf<String, String>()
        val customNameMap = data.exerciseRows
            .filter(RestoreExerciseRow::isCustom)
            .groupBy(RestoreExerciseRow::name)
            .filterValues { it.size == 1 }
            .mapValues { (_, rows) ->
                rows.single().stableKey
                    .takeIf(UserExerciseStableKeyGenerator::isUserExerciseKey)
                    ?: UserExerciseStableKeyGenerator.generate()
            }
        data.exerciseRows.filter(RestoreExerciseRow::isCustom).forEach { row ->
            if (row.stableKey.isNotBlank()) {
                customKeyMap[row.stableKey] = row.stableKey
                    .takeIf(UserExerciseStableKeyGenerator::isUserExerciseKey)
                    ?: customNameMap.getValue(row.name)
            }
        }

        fun resolve(
            stableKey: String,
            name: String,
            equipment: String,
            entityType: String,
            entityRowId: Long? = null
        ): LegacyExerciseResolution {
            customKeyMap[stableKey]?.let {
                return LegacyExerciseResolution.Resolved(it, "LEGACY_CUSTOM_EXERCISE")
            }
            if (stableKey.isBlank()) {
                customNameMap[name]?.let {
                    return LegacyExerciseResolution.Resolved(it, "LEGACY_CUSTOM_EXACT_NAME")
                }
            }
            if (!isLegacy) {
                return if (stableKey in canonicalStableKeys ||
                    (UserExerciseStableKeyGenerator.isUserExerciseKey(stableKey) &&
                        data.exerciseRows.any { it.isCustom && it.stableKey == stableKey })
                ) {
                    LegacyExerciseResolution.Resolved(stableKey, "NEW_BACKUP_EXACT_STABLE_KEY")
                } else {
                    LegacyExerciseResolution.Rejected(
                        DataTransferDiagnostic(
                            code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                            messageKo = "새 백업의 운동 stableKey를 현재 정본 목록에서 찾을 수 없습니다.",
                            stage = DataTransferStages.PLANNING,
                            entityType = entityType,
                            entityRowId = entityRowId,
                            sourceExerciseStableKey = stableKey,
                            sourceExerciseName = name,
                            attemptedCanonicalStableKey = stableKey,
                            resolutionMethod = "NEW_BACKUP_EXACT_STABLE_KEY",
                            candidateCount = 0
                        )
                    )
                }
            }
            return legacyMapper.resolve(
                oldStableKey = stableKey,
                oldName = name,
                equipment = equipment,
                canonicalStableKeys = canonicalStableKeys,
                stage = DataTransferStages.PLANNING,
                entityType = entityType,
                entityRowId = entityRowId
            )
        }

        val exerciseRows = data.exerciseRows.mapNotNull { row ->
            when (val resolution = resolve(
                stableKey = row.stableKey,
                name = row.name,
                equipment = row.equipment,
                entityType = "Exercise"
            )) {
                is LegacyExerciseResolution.Resolved -> row.copy(stableKey = resolution.canonicalStableKey)
                is LegacyExerciseResolution.Dropped -> {
                    warnings += resolution.diagnostic
                    null
                }
                is LegacyExerciseResolution.Rejected -> {
                    errors += resolution.diagnostic
                    null
                }
            }
        }.distinctBy(RestoreExerciseRow::stableKey)

        val runtimeMetadataRows = data.runtimeMetadataRows.mapNotNull { row ->
            val sourceExercise = exerciseRowsByKey[row.stableKey]
            when (val resolution = resolve(
                stableKey = row.stableKey,
                name = row.exerciseName,
                equipment = sourceExercise?.equipment.orEmpty(),
                entityType = "RuntimeExerciseMetadata"
            )) {
                is LegacyExerciseResolution.Resolved -> row.copy(stableKey = resolution.canonicalStableKey)
                is LegacyExerciseResolution.Dropped -> {
                    warnings += resolution.diagnostic
                    null
                }
                is LegacyExerciseResolution.Rejected -> {
                    errors += resolution.diagnostic
                    null
                }
            }
        }.distinctBy(RuntimeExerciseMetadata::stableKey)

        val droppedEntryKeys = mutableSetOf<String>()
        val setRows = data.setRows.mapNotNull { row ->
            val sourceExercise = exerciseRowsByKey[row.stableKey]
            when (val resolution = resolve(
                stableKey = row.stableKey,
                name = row.exerciseName,
                equipment = sourceExercise?.equipment.orEmpty(),
                entityType = "WorkoutEntry"
            )) {
                is LegacyExerciseResolution.Resolved -> row.copy(stableKey = resolution.canonicalStableKey)
                is LegacyExerciseResolution.Dropped -> {
                    warnings += resolution.diagnostic.copy(
                        workoutDate = row.date,
                        sourceExerciseName = row.exerciseName
                    )
                    droppedEntryKeys += row.entryKey
                    null
                }
                is LegacyExerciseResolution.Rejected -> {
                    errors += resolution.diagnostic.copy(
                        workoutDate = row.date,
                        sourceExerciseName = row.exerciseName
                    )
                    null
                }
            }
        }.filter { it.entryKey !in droppedEntryKeys }

        val droppedProgramKeys = mutableSetOf<String>()
        val programItems = data.programSnapshot?.items.orEmpty().mapNotNull { item ->
            val sourceExercise = exerciseRowsByKey[item.exerciseStableKey]
            when (val resolution = resolve(
                stableKey = item.exerciseStableKey,
                name = item.exerciseName,
                equipment = sourceExercise?.equipment.orEmpty(),
                entityType = "TrainingProgramItem"
            )) {
                is LegacyExerciseResolution.Resolved -> item.copy(
                    exerciseStableKey = resolution.canonicalStableKey
                )
                is LegacyExerciseResolution.Dropped -> {
                    warnings += resolution.diagnostic.copy(
                        programStableKey = item.programStableKey,
                        programItemRowId = null,
                        week = item.weekNumber,
                        day = item.dayOfWeek,
                        order = item.orderIndex
                    )
                    droppedProgramKeys += item.programStableKey
                    null
                }
                is LegacyExerciseResolution.Rejected -> {
                    errors += resolution.diagnostic.copy(
                        programStableKey = item.programStableKey,
                        week = item.weekNumber,
                        day = item.dayOfWeek,
                        order = item.orderIndex
                    )
                    null
                }
            }
        }.filter { it.programStableKey !in droppedProgramKeys }
        val snapshot = data.programSnapshot?.let { current ->
            current.copy(
                programs = current.programs.filter { it.stableKey !in droppedProgramKeys },
                items = programItems
            )
        }

        return RestoreCanonicalizationResult(
            data = data.copy(
                exerciseRows = exerciseRows,
                setRows = setRows,
                runtimeMetadataRows = runtimeMetadataRows,
                programSnapshot = snapshot,
                warningCount = data.warningCount + warnings.size
            ),
            warnings = warnings.distinct(),
            errors = errors.distinct()
        )
    }
}
