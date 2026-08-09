package com.training.trackplanner.data

internal data class RestorePreflightPlan(
    val currentCanonicalResolved: Set<String> = emptySet(),
    val currentHistoryResolved: Set<String> = emptySet(),
    val customResolved: Set<String> = emptySet(),
    val backupHistoricalCreated: Set<String> = emptySet(),
    val legacyExplicitlyMapped: Map<String, String> = emptyMap(),
    val minimalHistoricalStubCreated: Set<String> = emptySet()
)

internal data class RestoreCanonicalizationResult(
    val data: RecordCsvImportData.Restore,
    val warnings: List<DataTransferDiagnostic>,
    val errors: List<DataTransferDiagnostic>,
    val plan: RestorePreflightPlan = RestorePreflightPlan()
)

internal class BackupRestoreCanonicalizer(
    private val legacyMapper: LegacyExerciseImportMapper
) {
    fun canonicalize(
        data: RecordCsvImportData.Restore,
        canonicalExercises: Map<String, Exercise>
    ): RestoreCanonicalizationResult {
        val warnings = mutableListOf<DataTransferDiagnostic>()
        val errors = mutableListOf<DataTransferDiagnostic>()
        val currentCanonical = mutableSetOf<String>()
        val currentHistory = mutableSetOf<String>()
        val custom = mutableSetOf<String>()
        val backupHistorical = mutableSetOf<String>()
        val legacyMapped = mutableMapOf<String, String>()
        val minimalStubs = mutableSetOf<String>()
        val isLegacy = (data.manifest?.formatVersion ?: 0) < 11
        val canonicalByKey = canonicalExercises.entries.associate { (key, exercise) -> key.trim() to exercise }
        val referencedKeys = buildSet {
            data.setRows.mapTo(this, RestoreSetRow::stableKey)
            data.programSnapshot?.items.orEmpty().mapTo(this, ProgramBackupItem::exerciseStableKey)
        }.filterTo(mutableSetOf(), String::isNotBlank)

        data.exerciseRows
            .filter { it.stableKey.isNotBlank() }
            .groupBy(RestoreExerciseRow::stableKey)
            .filterValues { rows -> rows.distinct().size > 1 }
            .keys
            .forEach { stableKey ->
                errors += diagnostic(
                    code = DataTransferDiagnosticCodes.RESTORE_IDENTITY_CONTRADICTION,
                    message = "Backup contains contradictory exercise definitions for stableKey $stableKey.",
                    stableKey = stableKey
                )
            }
        data.exerciseRows
            .filter { row -> row.isCustom && row.stableKey.isNotBlank() && row.stableKey in canonicalByKey }
            .forEach { row ->
                errors += diagnostic(
                    code = DataTransferDiagnosticCodes.RESTORE_IDENTITY_CONTRADICTION,
                    message = "Custom exercise collides with current built-in stableKey ${row.stableKey}.",
                    stableKey = row.stableKey
                )
            }

        validateProgramGraph(data.programSnapshot, errors)
        if (errors.isNotEmpty()) return RestoreCanonicalizationResult(data, warnings, errors)

        val sourceRowsByKey = data.exerciseRows
            .filter { it.stableKey.isNotBlank() }
            .associateBy(RestoreExerciseRow::stableKey)
        val customNames = data.exerciseRows
            .filter(RestoreExerciseRow::isCustom)
            .groupBy(RestoreExerciseRow::name)
            .filterValues { it.size == 1 }
            .mapValues { (_, rows) ->
                rows.single().stableKey.takeIf(UserExerciseStableKeyGenerator::isUserExerciseKey)
                    ?: UserExerciseStableKeyGenerator.generate()
            }
        val customKeys = data.exerciseRows
            .filter { row -> row.isCustom && row.stableKey.isNotBlank() }
            .associate { row -> row.stableKey to row.stableKey }
        val resolutionCache = mutableMapOf<Pair<String, String>, LegacyExerciseResolution>()

        fun resolve(stableKey: String, name: String, equipment: String, entityType: String): LegacyExerciseResolution {
            val cacheKey = stableKey to name
            return resolutionCache.getOrPut(cacheKey) {
                customKeys[stableKey]?.let { key ->
                    custom += key
                    return@getOrPut LegacyExerciseResolution.Resolved(key, "BACKUP_CUSTOM_STABLE_KEY", name)
                }
                if (stableKey.isBlank()) {
                    customNames[name]?.let { key ->
                        custom += key
                        return@getOrPut LegacyExerciseResolution.Resolved(key, "LEGACY_CUSTOM_EXACT_NAME", name)
                    }
                    return@getOrPut legacyMapper.resolve(
                        oldStableKey = stableKey,
                        oldName = name,
                        equipment = equipment,
                        canonicalStableKeys = canonicalByKey.keys,
                        stage = DataTransferStages.PLANNING,
                        entityType = entityType
                    )
                }
                canonicalByKey[stableKey]?.let { current ->
                    if (current.planningEligibility == "HISTORY_ONLY") currentHistory += stableKey
                    else currentCanonical += stableKey
                    return@getOrPut LegacyExerciseResolution.Resolved(
                        canonicalStableKey = stableKey,
                        method = "CURRENT_CANONICAL_STABLE_KEY",
                        canonicalName = current.name
                    )
                }
                if (isLegacy) {
                    when (val mapped = legacyMapper.resolve(
                        oldStableKey = stableKey,
                        oldName = name,
                        equipment = equipment,
                        canonicalStableKeys = canonicalByKey.keys,
                        stage = DataTransferStages.PLANNING,
                        entityType = entityType
                    )) {
                        is LegacyExerciseResolution.Resolved -> {
                            legacyMapped[stableKey] = mapped.canonicalStableKey
                            return@getOrPut mapped.copy(
                                canonicalName = canonicalByKey[mapped.canonicalStableKey]?.name
                                    ?: mapped.canonicalName
                            )
                        }
                        is LegacyExerciseResolution.Dropped -> if (stableKey !in referencedKeys) {
                            return@getOrPut mapped
                        } else warnings += mapped.diagnostic.copy(
                            messageKo = "Deleted legacy identity was retained as history because user data references it."
                        )
                        is LegacyExerciseResolution.Rejected -> warnings += mapped.diagnostic.copy(
                            messageKo = "Unmapped legacy identity was retained as inactive history."
                        )
                    }
                }
                backupHistorical += stableKey
                LegacyExerciseResolution.Resolved(stableKey, "BACKUP_HISTORICAL_STABLE_KEY", name)
            }
        }

        val resolvedRows = data.exerciseRows.mapNotNull { row ->
            when (val resolution = resolve(row.stableKey, row.name, row.equipment, "Exercise")) {
                is LegacyExerciseResolution.Resolved -> {
                    val current = canonicalByKey[resolution.canonicalStableKey]
                    row.copy(
                        stableKey = resolution.canonicalStableKey,
                        name = current?.name ?: resolution.canonicalName.ifBlank { row.name },
                        isActive = if (current != null || row.isCustom) row.isActive else false,
                        needsReview = row.needsReview || (!row.isCustom && current == null)
                    )
                }
                is LegacyExerciseResolution.Dropped -> {
                    warnings += resolution.diagnostic
                    null
                }
                is LegacyExerciseResolution.Rejected -> {
                    errors += resolution.diagnostic
                    null
                }
            }
        }.distinctBy(RestoreExerciseRow::stableKey).toMutableList()

        val referenceDetails = linkedMapOf<String, Pair<String, String>>()
        data.setRows.forEach { row -> referenceDetails.putIfAbsent(row.stableKey, row.exerciseName to row.category) }
        data.programSnapshot?.items.orEmpty().forEach { item ->
            referenceDetails.putIfAbsent(item.exerciseStableKey, item.exerciseName to item.category)
        }
        data.runtimeMetadataRows.forEach { row ->
            referenceDetails.putIfAbsent(row.stableKey, row.exerciseName to "Historical")
        }
        referenceDetails.forEach { (sourceKey, details) ->
            val source = sourceRowsByKey[sourceKey]
            val resolution = resolve(sourceKey, details.first, source?.equipment.orEmpty(), "ExerciseReference")
            if (resolution is LegacyExerciseResolution.Resolved &&
                resolvedRows.none { it.stableKey == resolution.canonicalStableKey }
            ) {
                val current = canonicalByKey[resolution.canonicalStableKey]
                resolvedRows += minimalStub(
                    stableKey = resolution.canonicalStableKey,
                    name = current?.name ?: resolution.canonicalName.ifBlank { details.first },
                    category = current?.category ?: details.second,
                    current = current
                )
                if (current == null) {
                    minimalStubs += resolution.canonicalStableKey
                    warnings += diagnostic(
                        code = DataTransferDiagnosticCodes.RESTORE_HISTORICAL_STUB_CREATED,
                        message = "Created an inactive historical exercise stub for ${resolution.canonicalStableKey}.",
                        stableKey = resolution.canonicalStableKey
                    )
                }
            }
        }

        fun resolved(stableKey: String, name: String, entityType: String): LegacyExerciseResolution.Resolved? {
            val source = sourceRowsByKey[stableKey]
            return when (val result = resolve(stableKey, name, source?.equipment.orEmpty(), entityType)) {
                is LegacyExerciseResolution.Resolved -> result
                is LegacyExerciseResolution.Dropped -> {
                    warnings += result.diagnostic
                    null
                }
                is LegacyExerciseResolution.Rejected -> {
                    errors += result.diagnostic
                    null
                }
            }
        }

        val runtimeRows = data.runtimeMetadataRows.mapNotNull { row ->
            resolved(row.stableKey, row.exerciseName, "RuntimeExerciseMetadata")?.let { resolution ->
                row.copy(
                    stableKey = resolution.canonicalStableKey,
                    exerciseName = canonicalByKey[resolution.canonicalStableKey]?.name
                        ?: resolution.canonicalName.ifBlank { row.exerciseName }
                )
            }
        }.distinctBy(RuntimeExerciseMetadata::stableKey)
        val snapshotRows = data.metadataSnapshotRows.mapNotNull { row ->
            resolved(row.stableKey, sourceRowsByKey[row.stableKey]?.name.orEmpty(), "ExerciseMetadataSnapshot")
                ?.let { row.copy(stableKey = it.canonicalStableKey) }
        }
        val overrideRows = data.metadataUserOverrideRows.mapNotNull { row ->
            resolved(row.stableKey, sourceRowsByKey[row.stableKey]?.name.orEmpty(), "ExerciseMetadataUserOverride")
                ?.let { row.copy(stableKey = it.canonicalStableKey).validated() }
        }
        require(
            overrideRows.distinctBy { Triple(it.stableKey, it.fieldScope, it.fieldKey) }.size == overrideRows.size
        ) { "Backup contains duplicate explicit metadata override fields." }
        val setRows = data.setRows.mapNotNull { row ->
            resolved(row.stableKey, row.exerciseName, "WorkoutEntry")?.let { resolution ->
                row.copy(
                    stableKey = resolution.canonicalStableKey,
                    exerciseName = canonicalByKey[resolution.canonicalStableKey]?.name
                        ?: resolution.canonicalName.ifBlank { row.exerciseName }
                )
            }
        }
        val snapshot = data.programSnapshot?.let { current ->
            current.copy(
                items = current.items.mapNotNull { item ->
                    resolved(item.exerciseStableKey, item.exerciseName, "TrainingProgramItem")?.let { resolution ->
                        item.copy(
                            exerciseStableKey = resolution.canonicalStableKey,
                            exerciseName = canonicalByKey[resolution.canonicalStableKey]?.name
                                ?: resolution.canonicalName.ifBlank { item.exerciseName }
                        )
                    }
                }
            )
        }

        if (isLegacy && setRows.any { it.entrySourceId == null }) {
            warnings += diagnostic(
                code = DataTransferDiagnosticCodes.RESTORE_LEGACY_ENTRY_IDENTITY_FALLBACK,
                message = "Legacy workout entries lack durable source identities; conservative content matching was used."
            )
        }
        val distinctWarnings = warnings.distinct()
        val distinctErrors = errors.distinct()
        return RestoreCanonicalizationResult(
            data = data.copy(
                exerciseRows = resolvedRows,
                setRows = setRows,
                runtimeMetadataRows = runtimeRows,
                metadataSnapshotRows = snapshotRows,
                metadataUserOverrideRows = overrideRows,
                programSnapshot = snapshot,
                warningCount = data.warningCount + distinctWarnings.size
            ),
            warnings = distinctWarnings,
            errors = distinctErrors,
            plan = RestorePreflightPlan(
                currentCanonicalResolved = currentCanonical,
                currentHistoryResolved = currentHistory,
                customResolved = custom,
                backupHistoricalCreated = backupHistorical,
                legacyExplicitlyMapped = legacyMapped,
                minimalHistoricalStubCreated = minimalStubs
            )
        )
    }

    private fun validateProgramGraph(
        snapshot: RestoreProgramSnapshot?,
        errors: MutableList<DataTransferDiagnostic>
    ) {
        snapshot ?: return
        val programKeys = snapshot.programs.map(TrainingProgram::stableKey)
        if (programKeys.size != programKeys.distinct().size) {
            errors += diagnostic(DataTransferDiagnosticCodes.RESTORE_IDENTITY_CONTRADICTION, "Duplicate program identity in backup.")
        }
        val positions = snapshot.items.map { item ->
            "${item.programStableKey}|${item.weekNumber}|${item.dayOfWeek}|${item.orderIndex}"
        }
        if (positions.size != positions.distinct().size) {
            errors += diagnostic(DataTransferDiagnosticCodes.RESTORE_IDENTITY_CONTRADICTION, "Duplicate program item position in backup.")
        }
        snapshot.items.filter { it.programStableKey !in programKeys }.forEach { item ->
            errors += diagnostic(
                DataTransferDiagnosticCodes.RESTORE_PROGRAM_PARENT_MISSING,
                "Program item parent is missing: ${item.programStableKey}."
            )
        }
        snapshot.sets.filter { set ->
            "${set.programStableKey}|${set.weekNumber}|${set.dayOfWeek}|${set.orderIndex}" !in positions
        }.forEach { set ->
            errors += diagnostic(
                DataTransferDiagnosticCodes.RESTORE_PROGRAM_PARENT_MISSING,
                "Program item set parent is missing: ${set.programStableKey}."
            )
        }
    }

    private fun minimalStub(
        stableKey: String,
        name: String,
        category: String,
        current: Exercise?
    ): RestoreExerciseRow = RestoreExerciseRow(
        name = current?.name ?: name.ifBlank { stableKey },
        stableKey = stableKey,
        category = current?.category ?: category.ifBlank { "Historical" },
        detail1 = "",
        detail2 = "",
        mode = "",
        description = "",
        defaultRestSeconds = current?.defaultRestSeconds ?: 60,
        imageAssetName = current?.imageAssetName.orEmpty(),
        primaryMuscles = "",
        secondaryMuscles = "",
        equipment = "",
        movementPattern = "",
        movementCategory = "",
        forceType = "",
        bodyRegion = "",
        laterality = "",
        plane = "",
        legacyTrainingRole = "",
        trainingRoleCodes = emptySet(),
        programSlotCapabilityCodes = emptySet(),
        sportTransferDirect = "",
        sportTransferSupportive = "",
        loadProfile = "",
        metadataConfidence = MetadataConfidence.LOW.name,
        isActive = current?.isActive ?: false,
        isCustom = current?.isCustom ?: UserExerciseStableKeyGenerator.isUserExerciseKey(stableKey),
        needsReview = current?.needsReview ?: true
    )

    private fun diagnostic(
        code: String,
        message: String,
        stableKey: String? = null
    ) = DataTransferDiagnostic(
        code = code,
        messageKo = message,
        stage = DataTransferStages.PLANNING,
        sourceExerciseStableKey = stableKey.orEmpty()
    )
}
