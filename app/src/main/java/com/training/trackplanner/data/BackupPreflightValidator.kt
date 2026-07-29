package com.training.trackplanner.data

internal data class BackupPreflightResult(
    val entityCounts: Map<String, Int>,
    val warnings: List<DataTransferDiagnostic>,
    val errors: List<DataTransferDiagnostic>
)

internal object BackupPreflightValidator {
    fun validate(
        exercises: List<Exercise>,
        workoutEntries: List<WorkoutEntry>,
        workoutSets: List<WorkoutSet>,
        programs: List<TrainingProgram>,
        programItems: List<TrainingProgramItem>,
        programItemSets: List<TrainingProgramItemSet> = emptyList(),
        runtimeMetadata: List<RuntimeExerciseMetadata>,
        migrationIssues: List<ExerciseIdentityMigrationIssue>
    ): BackupPreflightResult {
        val errors = mutableListOf<DataTransferDiagnostic>()
        val warnings = mutableListOf<DataTransferDiagnostic>()
        val exercisesByKey = exercises.associateBy(Exercise::stableKey)
        val programsById = programs.associateBy(TrainingProgram::id)
        val entryIds = workoutEntries.mapTo(mutableSetOf(), WorkoutEntry::id)
        val setEntryIds = workoutSets.mapTo(mutableSetOf(), WorkoutSet::entryId)
        val programItemIds = programItems.mapTo(mutableSetOf(), TrainingProgramItem::id)

        exercises
            .groupBy(Exercise::stableKey)
            .filter { (key, rows) -> key.isBlank() || rows.size > 1 }
            .forEach { (key, rows) ->
                errors += DataTransferDiagnostic(
                    code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                    messageKo = if (key.isBlank()) {
                        "빈 운동 stableKey가 있습니다."
                    } else {
                        "중복 운동 stableKey가 있습니다: $key"
                    },
                    stage = DataTransferStages.PREFLIGHT,
                    entityType = "Exercise",
                    sourceExerciseStableKey = key,
                    candidateCount = rows.size
                )
            }

        workoutEntries.forEach { entry ->
            when {
                entry.exerciseStableKey.isBlank() -> errors += DataTransferDiagnostic(
                    code = DataTransferDiagnosticCodes.WORKOUT_EXERCISE_STABLE_KEY_MISSING,
                    messageKo = "운동 기록에 exerciseStableKey가 없습니다.",
                    stage = DataTransferStages.PREFLIGHT,
                    entityType = "WorkoutEntry",
                    entityRowId = entry.id,
                    workoutDate = entry.date,
                    sourceExerciseName = entry.exerciseName
                )
                entry.exerciseStableKey !in exercisesByKey -> errors += DataTransferDiagnostic(
                    code = DataTransferDiagnosticCodes.WORKOUT_EXERCISE_STABLE_KEY_UNRESOLVED,
                    messageKo = "운동 기록의 stableKey를 현재 운동 목록에서 찾을 수 없습니다.",
                    stage = DataTransferStages.PREFLIGHT,
                    entityType = "WorkoutEntry",
                    entityRowId = entry.id,
                    workoutDate = entry.date,
                    sourceExerciseStableKey = entry.exerciseStableKey,
                    sourceExerciseName = entry.exerciseName,
                    attemptedCanonicalStableKey = entry.exerciseStableKey,
                    resolutionMethod = "EXACT_STABLE_KEY",
                    candidateCount = 0
                )
            }
        }

        workoutSets.filter { it.entryId !in entryIds }.forEach { set ->
            errors += DataTransferDiagnostic(
                code = DataTransferDiagnosticCodes.ORPHAN_WORKOUT_SET,
                messageKo = "상위 운동 기록이 없는 세트입니다.",
                stage = DataTransferStages.PREFLIGHT,
                entityType = "WorkoutSet",
                entityRowId = set.id
            )
        }
        workoutEntries.filter { it.id !in setEntryIds }.forEach { entry ->
            errors += DataTransferDiagnostic(
                code = DataTransferDiagnosticCodes.WORKOUT_ENTRY_WITHOUT_SET,
                messageKo = "세트가 없는 운동 기록은 현재 백업 형식으로 보존할 수 없습니다.",
                stage = DataTransferStages.PREFLIGHT,
                entityType = "WorkoutEntry",
                entityRowId = entry.id,
                workoutDate = entry.date,
                sourceExerciseStableKey = entry.exerciseStableKey,
                sourceExerciseName = entry.exerciseName
            )
        }

        programItems.forEach { item ->
            val program = programsById[item.programId]
            if (program == null) {
                errors += DataTransferDiagnostic(
                    code = DataTransferDiagnosticCodes.ORPHAN_PROGRAM_ITEM,
                    messageKo = "상위 프로그램이 없는 프로그램 항목입니다.",
                    stage = DataTransferStages.PREFLIGHT,
                    entityType = "TrainingProgramItem",
                    entityRowId = item.id,
                    programItemRowId = item.id,
                    week = item.weekNumber,
                    day = item.dayOfWeek,
                    order = item.orderIndex,
                    sourceExerciseStableKey = item.exerciseStableKey,
                    sourceExerciseName = item.exerciseName
                )
                return@forEach
            }
            when {
                item.exerciseStableKey.isBlank() -> errors += programDiagnostic(
                    code = DataTransferDiagnosticCodes.PROGRAM_EXERCISE_STABLE_KEY_MISSING,
                    message = "프로그램 항목에 exerciseStableKey가 없습니다.",
                    program = program,
                    item = item
                )
                item.exerciseStableKey !in exercisesByKey -> errors += programDiagnostic(
                    code = DataTransferDiagnosticCodes.PROGRAM_EXERCISE_STABLE_KEY_UNRESOLVED,
                    message = "프로그램 항목의 stableKey를 현재 운동 목록에서 찾을 수 없습니다.",
                    program = program,
                    item = item
                )
            }
        }
        programItemSets
            .groupBy(TrainingProgramItemSet::programItemId)
            .forEach { (programItemId, sets) ->
                if (programItemId !in programItemIds) {
                    errors += DataTransferDiagnostic(
                        code = DataTransferDiagnosticCodes.ORPHAN_PROGRAM_ITEM,
                        messageKo = "상위 프로그램 항목이 없는 세트 처방입니다.",
                        stage = DataTransferStages.PREFLIGHT,
                        entityType = "TrainingProgramItemSet",
                        entityRowId = sets.firstOrNull()?.id
                    )
                }
                val indices = sets.map(TrainingProgramItemSet::setIndex).sorted()
                if (indices != (1..indices.size).toList()) {
                    errors += DataTransferDiagnostic(
                        code = DataTransferDiagnosticCodes.ORPHAN_PROGRAM_ITEM,
                        messageKo = "프로그램 세트 순서는 1부터 중복 없이 이어져야 합니다.",
                        stage = DataTransferStages.PREFLIGHT,
                        entityType = "TrainingProgramItemSet",
                        entityRowId = sets.firstOrNull()?.id
                    )
                }
                sets.filter { set ->
                    set.reps < 0 ||
                        !set.weightKg.isFinite() ||
                        set.weightKg < 0.0 ||
                        set.seconds < 0
                }.forEach { set ->
                    errors += DataTransferDiagnostic(
                        code = DataTransferDiagnosticCodes.ORPHAN_PROGRAM_ITEM,
                        messageKo = "프로그램 세트 처방 값이 올바르지 않습니다.",
                        stage = DataTransferStages.PREFLIGHT,
                        entityType = "TrainingProgramItemSet",
                        entityRowId = set.id
                    )
                }
            }

        runtimeMetadata.filter { it.stableKey.isBlank() || it.stableKey !in exercisesByKey }.forEach { metadata ->
            errors += DataTransferDiagnostic(
                code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                messageKo = "런타임 운동 메타데이터의 stableKey를 현재 운동 목록에서 찾을 수 없습니다.",
                stage = DataTransferStages.PREFLIGHT,
                entityType = "RuntimeExerciseMetadata",
                sourceExerciseStableKey = metadata.stableKey,
                sourceExerciseName = metadata.exerciseName,
                attemptedCanonicalStableKey = metadata.stableKey,
                resolutionMethod = "EXACT_STABLE_KEY",
                candidateCount = 0
            )
        }

        migrationIssues.forEach { issue ->
            if (issue.issueCode == "RUNTIME_METADATA_OVERRIDE_COLLISION") {
                warnings += DataTransferDiagnostic(
                    code = DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED,
                    messageKo = issue.message,
                    stage = DataTransferStages.PREFLIGHT,
                    entityType = issue.entityType,
                    sourceExerciseStableKey = issue.sourceStableKey,
                    attemptedCanonicalStableKey = issue.canonicalStableKey,
                    resolutionMethod = "CANONICAL_OVERRIDE_RETAINED"
                )
                return@forEach
            }
            val code = if ("AMBIGUOUS" in issue.issueCode) {
                DataTransferDiagnosticCodes.AMBIGUOUS_LEGACY_EXERCISE_SPLIT
            } else {
                DataTransferDiagnosticCodes.RESTORE_CANONICAL_KEY_UNRESOLVED
            }
            errors += DataTransferDiagnostic(
                code = code,
                messageKo = issue.message,
                stage = DataTransferStages.PREFLIGHT,
                entityType = issue.entityType,
                entityRowId = issue.entityRowId,
                sourceExerciseStableKey = issue.sourceStableKey,
                attemptedCanonicalStableKey = issue.canonicalStableKey,
                resolutionMethod = "ROOM_MIGRATION_REVIEW_REQUIRED"
            )
        }

        exercises.filter { exercise ->
            exercise.stableKey.startsWith("imported_csv_") ||
                exercise.name.startsWith("CSV 복원 ")
        }.forEach { exercise ->
            warnings += DataTransferDiagnostic(
                code = DataTransferDiagnosticCodes.LEGACY_PLACEHOLDER_EXERCISE,
                messageKo = "이전 CSV 복원용 임시 운동은 새 백업의 운동 정본으로 사용할 수 없습니다.",
                stage = DataTransferStages.PREFLIGHT,
                entityType = "Exercise",
                sourceExerciseStableKey = exercise.stableKey,
                sourceExerciseName = exercise.name
            )
        }

        return BackupPreflightResult(
            entityCounts = linkedMapOf(
                "exercise" to exercises.size,
                "workout_entry" to workoutEntries.size,
                "workout_set" to workoutSets.size,
                "program" to programs.size,
                "program_item" to programItems.size,
                "program_item_set" to programItemSets.size,
                "runtime_metadata" to runtimeMetadata.size
            ),
            warnings = warnings,
            errors = errors
        )
    }

    private fun programDiagnostic(
        code: String,
        message: String,
        program: TrainingProgram,
        item: TrainingProgramItem
    ) = DataTransferDiagnostic(
        code = code,
        messageKo = message,
        stage = DataTransferStages.PREFLIGHT,
        entityType = "TrainingProgramItem",
        entityRowId = item.id,
        programStableKey = program.stableKey,
        programName = program.name,
        programItemRowId = item.id,
        week = item.weekNumber,
        day = item.dayOfWeek,
        order = item.orderIndex,
        sourceExerciseStableKey = item.exerciseStableKey,
        sourceExerciseName = item.exerciseName,
        attemptedCanonicalStableKey = item.exerciseStableKey,
        resolutionMethod = "EXACT_STABLE_KEY",
        candidateCount = 0
    )
}
