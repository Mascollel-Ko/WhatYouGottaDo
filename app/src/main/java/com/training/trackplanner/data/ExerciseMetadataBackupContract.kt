package com.training.trackplanner.data

enum class ExerciseMetadataFieldPolicy {
    IDENTITY_STABLE,
    CURRENT_CANONICAL_NAME,
    BACKUP_SNAPSHOT_WINS,
    CURRENT_CANONICAL_SYSTEM_VALUE,
    DERIVED_REBUILD
}

enum class ExerciseMetadataFieldScope {
    EXERCISE,
    RUNTIME_METADATA,
    TRAINING_ROLE_RELATION,
    PROGRAM_SLOT_CAPABILITY_RELATION,
    DENORMALIZED_REFERENCE,
    DERIVED_ANALYSIS
}

enum class ExerciseMetadataValueEncoding {
    STRING,
    BOOLEAN,
    INTEGER,
    LONG,
    DOUBLE,
    TOKEN_SET
}

data class ExerciseMetadataSnapshotRow(
    val stableKey: String,
    val fieldKey: String,
    val fieldScope: ExerciseMetadataFieldScope,
    val valueEncoding: ExerciseMetadataValueEncoding,
    val value: String,
    val isExplicitEmpty: Boolean
)

internal data class ExerciseMetadataSnapshotSource(
    val exercise: Exercise,
    val runtimeMetadata: RuntimeExerciseMetadata,
    val trainingRoles: Set<String>,
    val programSlotCapabilities: Set<String>
)

internal class ExerciseMetadataRestoreTarget(
    var exercise: Exercise,
    var runtimeMetadata: RuntimeExerciseMetadata
)

internal data class ExerciseMetadataFieldDefinition(
    val fieldKey: String,
    val fieldScope: ExerciseMetadataFieldScope,
    val policy: ExerciseMetadataFieldPolicy,
    val valueEncoding: ExerciseMetadataValueEncoding,
    val read: ((ExerciseMetadataSnapshotSource) -> String)? = null,
    val write: ((ExerciseMetadataRestoreTarget, String) -> Unit)? = null
)

internal object ExerciseMetadataFieldPolicyRegistry {
    val fields: List<ExerciseMetadataFieldDefinition> = buildList {
        identity(
            "identity.stableKey",
            ExerciseMetadataFieldScope.EXERCISE,
            read = { it.exercise.stableKey },
            write = { target, value ->
                require(target.exercise.stableKey == value) { "Exercise stableKey cannot change during restore." }
            }
        )
        identity(
            "identity.isCustom",
            ExerciseMetadataFieldScope.EXERCISE,
            ExerciseMetadataValueEncoding.BOOLEAN,
            { it.exercise.isCustom.toString() },
            { target, value -> target.exercise = target.exercise.copy(isCustom = value.requiredBoolean("identity.isCustom")) }
        )
        canonicalName(
            "exercise.name",
            ExerciseMetadataFieldScope.EXERCISE,
            { it.exercise.name },
            { target, value -> target.exercise = target.exercise.copy(name = value) }
        )

        exerciseString("exercise.category", { it.category }) { target, value -> target.copy(category = value) }
        exerciseString("exercise.detail1", { it.detail1 }) { target, value -> target.copy(detail1 = value) }
        exerciseString("exercise.detail2", { it.detail2 }) { target, value -> target.copy(detail2 = value) }
        exerciseString("exercise.mode", { it.mode }) { target, value -> target.copy(mode = value) }
        exerciseString("exercise.description", { it.description }) { target, value -> target.copy(description = value) }
        exerciseInt("exercise.defaultRestSeconds", { it.defaultRestSeconds }) { target, value -> target.copy(defaultRestSeconds = value) }
        exerciseString("exercise.familyId", { it.familyId }) { target, value -> target.copy(familyId = value) }
        exerciseString("exercise.familyName", { it.familyName }) { target, value -> target.copy(familyName = value) }
        exerciseString("exercise.familyRole", { it.familyRole }) { target, value -> target.copy(familyRole = value) }
        exerciseDouble("exercise.familyE1rmMultiplier", { it.familyE1rmMultiplier }) { target, value -> target.copy(familyE1rmMultiplier = value) }
        exerciseString("exercise.movementPattern", { it.movementPattern }) { target, value -> target.copy(movementPattern = value) }
        exerciseString("exercise.movementCategory", { it.movementCategory }) { target, value -> target.copy(movementCategory = value) }
        exerciseTokens("exercise.primaryMuscles", { it.primaryMuscles }) { target, value -> target.copy(primaryMuscles = value) }
        exerciseTokens("exercise.secondaryMuscles", { it.secondaryMuscles }) { target, value -> target.copy(secondaryMuscles = value) }
        exerciseTokens("exercise.equipment", { it.equipment }) { target, value -> target.copy(equipment = value) }
        exerciseTokens("exercise.equipmentTags", { it.equipmentTags }) { target, value -> target.copy(equipmentTags = value) }
        exerciseString("exercise.compoundType", { it.compoundType }) { target, value -> target.copy(compoundType = value) }
        exerciseString("exercise.forceType", { it.forceType }) { target, value -> target.copy(forceType = value) }
        exerciseString("exercise.bodyRegion", { it.bodyRegion }) { target, value -> target.copy(bodyRegion = value) }
        exerciseString("exercise.plane", { it.plane }) { target, value -> target.copy(plane = value) }
        exerciseString("exercise.laterality", { it.laterality }) { target, value -> target.copy(laterality = value) }
        exerciseString("exercise.axialLoadLevel", { it.axialLoadLevel }) { target, value -> target.copy(axialLoadLevel = value) }
        exerciseTokens("exercise.stabilityRoles", { it.stabilityRoles }) { target, value -> target.copy(stabilityRoles = value) }
        exerciseTokens("exercise.sportTransferDirect", { it.sportTransferDirect }) { target, value -> target.copy(sportTransferDirect = value) }
        exerciseTokens("exercise.sportTransferSupportive", { it.sportTransferSupportive }) { target, value -> target.copy(sportTransferSupportive = value) }
        exerciseTokens("exercise.badmintonTransferRoles", { it.badmintonTransferRoles }) { target, value -> target.copy(badmintonTransferRoles = value) }
        exerciseTokens("exercise.fatigueCategories", { it.fatigueCategories }) { target, value -> target.copy(fatigueCategories = value) }
        exerciseTokens("exercise.adaptiveBaselineGroups", { it.adaptiveBaselineGroups }) { target, value -> target.copy(adaptiveBaselineGroups = value) }
        exerciseTokens("exercise.accessoryRoles", { it.accessoryRoles }) { target, value -> target.copy(accessoryRoles = value) }
        exerciseString("exercise.loadProfile", { it.loadProfile }) { target, value -> target.copy(loadProfile = value) }
        exerciseString("exercise.recoveryDecayProfile", { it.recoveryDecayProfile }) { target, value -> target.copy(recoveryDecayProfile = value) }
        exerciseDouble("exercise.systemicLoadWeight", { it.systemicLoadWeight }) { target, value -> target.copy(systemicLoadWeight = value) }
        exerciseDouble("exercise.neuralHeavyWeight", { it.neuralHeavyWeight }) { target, value -> target.copy(neuralHeavyWeight = value) }
        exerciseDouble("exercise.neuralSpeedWeight", { it.neuralSpeedWeight }) { target, value -> target.copy(neuralSpeedWeight = value) }
        exerciseDouble("exercise.localLoadWeight", { it.localLoadWeight }) { target, value -> target.copy(localLoadWeight = value) }
        exerciseDouble("exercise.decelerationWeight", { it.decelerationWeight }) { target, value -> target.copy(decelerationWeight = value) }
        exerciseDouble("exercise.elasticSscWeight", { it.elasticSscWeight }) { target, value -> target.copy(elasticSscWeight = value) }
        exerciseDouble("exercise.rotationPowerWeight", { it.rotationPowerWeight }) { target, value -> target.copy(rotationPowerWeight = value) }
        exerciseDouble("exercise.antiRotationWeight", { it.antiRotationWeight }) { target, value -> target.copy(antiRotationWeight = value) }
        exerciseDouble("exercise.overheadSwingWeight", { it.overheadSwingWeight }) { target, value -> target.copy(overheadSwingWeight = value) }
        exerciseDouble("exercise.gripLoadWeight", { it.gripLoadWeight }) { target, value -> target.copy(gripLoadWeight = value) }
        exerciseString("exercise.progressMetricType", { it.progressMetricType }) { target, value -> target.copy(progressMetricType = value) }
        exerciseString("exercise.strengthProgressionGroup", { it.strengthProgressionGroup }) { target, value -> target.copy(strengthProgressionGroup = value) }
        exerciseString("exercise.hypertrophyVolumeGroup", { it.hypertrophyVolumeGroup }) { target, value -> target.copy(hypertrophyVolumeGroup = value) }
        exerciseString("exercise.mainLiftGroup", { it.mainLiftGroup }) { target, value -> target.copy(mainLiftGroup = value) }
        exerciseString("exercise.accessoryContributionGroup", { it.accessoryContributionGroup }) { target, value -> target.copy(accessoryContributionGroup = value) }
        exerciseBoolean("exercise.estimated1RmEligible", { it.estimated1RmEligible }) { target, value -> target.copy(estimated1RmEligible = value) }
        exerciseBoolean("exercise.volumeLoadEligible", { it.volumeLoadEligible }) { target, value -> target.copy(volumeLoadEligible = value) }
        exerciseString("exercise.badmintonTransferStrength", { it.badmintonTransferStrength }) { target, value -> target.copy(badmintonTransferStrength = value) }
        exerciseTokens("exercise.courtMovementTypes", { it.courtMovementTypes }) { target, value -> target.copy(courtMovementTypes = value) }
        exerciseTokens("exercise.badmintonSkillTargets", { it.badmintonSkillTargets }) { target, value -> target.copy(badmintonSkillTargets = value) }
        exerciseTokens("exercise.jointStressTags", { it.jointStressTags }) { target, value -> target.copy(jointStressTags = value) }
        exerciseString("exercise.stabilityDemandLevel", { it.stabilityDemandLevel }) { target, value -> target.copy(stabilityDemandLevel = value) }
        exerciseString("exercise.mobilityDemandLevel", { it.mobilityDemandLevel }) { target, value -> target.copy(mobilityDemandLevel = value) }
        exerciseTokens("exercise.balanceContributionTags", { it.balanceContributionTags }) { target, value -> target.copy(balanceContributionTags = value) }
        exerciseTokens("exercise.analysisEligibility", { it.analysisEligibility }) { target, value -> target.copy(analysisEligibility = value) }
        exerciseString("exercise.activityKind", { it.activityKind }) { target, value -> target.copy(activityKind = value) }
        exerciseString("exercise.planningEligibility", { it.planningEligibility }) { target, value -> target.copy(planningEligibility = value) }
        exerciseString("exercise.metadataConfidence", { it.metadataConfidence }) { target, value -> target.copy(metadataConfidence = value) }
        exerciseString("exercise.imageAssetName", { it.imageAssetName }) { target, value -> target.copy(imageAssetName = value) }
        exerciseBoolean("exercise.isActive", { it.isActive }) { target, value -> target.copy(isActive = value) }
        exerciseLong("exercise.archivedAt", { it.archivedAt }) { target, value -> target.copy(archivedAt = value) }
        exerciseBoolean("exercise.needsReview", { it.needsReview }) { target, value -> target.copy(needsReview = value) }

        canonicalName(
            "runtime.exerciseName",
            ExerciseMetadataFieldScope.RUNTIME_METADATA,
            { it.runtimeMetadata.exerciseName },
            { target, value -> target.runtimeMetadata = target.runtimeMetadata.copy(exerciseName = value) }
        )
        runtimeString("runtime.activityKind", { it.activityKind }) { target, value -> target.copy(activityKind = value) }
        runtimeString("runtime.planningEligibility", { it.planningEligibility }) { target, value -> target.copy(planningEligibility = value) }
        runtimeString("runtime.movementFamily", { it.movementFamily }) { target, value -> target.copy(movementFamily = value) }
        runtimeString("runtime.movementSubtype", { it.movementSubtype }) { target, value -> target.copy(movementSubtype = value) }
        runtimeString("runtime.programSlot", { it.programSlot }) { target, value -> target.copy(programSlot = value) }
        runtimeString("runtime.redundancyGroup", { it.redundancyGroup }) { target, value -> target.copy(redundancyGroup = value) }
        runtimeString("runtime.progressMetricType", { it.progressMetricType }) { target, value -> target.copy(progressMetricType = value) }
        runtimeString("runtime.strengthProgressionGroup", { it.strengthProgressionGroup }) { target, value -> target.copy(strengthProgressionGroup = value) }
        runtimeTokens("runtime.analysisEligibility", { it.analysisEligibility }) { target, value -> target.copy(analysisEligibility = value) }
        runtimeString("runtime.primaryStressProfile", { it.primaryStressProfile }) { target, value -> target.copy(primaryStressProfile = value) }
        runtimeTokens("runtime.secondaryStressTags", { it.secondaryStressTags }) { target, value -> target.copy(secondaryStressTags = value) }
        runtimeTokens("runtime.tendonStressTags", { it.tendonStressTags }) { target, value -> target.copy(tendonStressTags = value) }
        runtimeTokens("runtime.ligamentJointStabilityStressTags", { it.ligamentJointStabilityStressTags }) { target, value -> target.copy(ligamentJointStabilityStressTags = value) }
        runtimeTokens("runtime.jointImpactStressTags", { it.jointImpactStressTags }) { target, value -> target.copy(jointImpactStressTags = value) }
        runtimeTokens("runtime.cognitiveStressTags", { it.cognitiveStressTags }) { target, value -> target.copy(cognitiveStressTags = value) }
        runtimeTokens("runtime.sportContextTags", { it.sportContextTags }) { target, value -> target.copy(sportContextTags = value) }
        systemRuntimeString("runtime.recoveryDecayProfile", { it.recoveryDecayProfile }) { target, value -> target.copy(recoveryDecayProfile = value) }
        runtimeString("runtime.stressMagnitudeHint", { it.stressMagnitudeHint }) { target, value -> target.copy(stressMagnitudeHint = value) }
        runtimeString("runtime.badmintonTransferLevel", { it.badmintonTransferLevel }) { target, value -> target.copy(badmintonTransferLevel = value) }
        runtimeTokens("runtime.badmintonTransferType", { it.badmintonTransferType }) { target, value -> target.copy(badmintonTransferType = value) }
        runtimeTokens("runtime.badmintonSkillTargets", { it.badmintonSkillTargets }) { target, value -> target.copy(badmintonSkillTargets = value) }
        runtimeTokens("runtime.badmintonPhysicalQualities", { it.badmintonPhysicalQualities }) { target, value -> target.copy(badmintonPhysicalQualities = value) }
        runtimeString("runtime.transferConfidence", { it.transferConfidence }) { target, value -> target.copy(transferConfidence = value) }
        runtimeString("runtime.sourceConfidenceLevel", { it.sourceConfidenceLevel }) { target, value -> target.copy(sourceConfidenceLevel = value) }
        runtimeString("runtime.finalSourceStatus", { it.finalSourceStatus }) { target, value -> target.copy(finalSourceStatus = value) }
        runtimeString("runtime.neuromuscularStressLevel", { it.neuromuscularStressLevel }) { target, value -> target.copy(neuromuscularStressLevel = value) }
        runtimeString("runtime.systemicMuscularStressLevel", { it.systemicMuscularStressLevel }) { target, value -> target.copy(systemicMuscularStressLevel = value) }
        runtimeString("runtime.localMuscularStressLevel", { it.localMuscularStressLevel }) { target, value -> target.copy(localMuscularStressLevel = value) }
        runtimeString("runtime.jointTendonImpactStressLevel", { it.jointTendonImpactStressLevel }) { target, value -> target.copy(jointTendonImpactStressLevel = value) }
        runtimeString("runtime.movementFocusDemandLevel", { it.movementFocusDemandLevel }) { target, value -> target.copy(movementFocusDemandLevel = value) }
        runtimeString("runtime.recoveryDurationClass", { it.recoveryDurationClass }) { target, value -> target.copy(recoveryDurationClass = value) }

        backupRelation(
            "relation.trainingRoles",
            ExerciseMetadataFieldScope.TRAINING_ROLE_RELATION,
            ExerciseMetadataValueEncoding.TOKEN_SET
        ) { it.trainingRoles.joinToString("|") }
        backupRelation(
            "relation.programSlotCapabilities",
            ExerciseMetadataFieldScope.PROGRAM_SLOT_CAPABILITY_RELATION,
            ExerciseMetadataValueEncoding.TOKEN_SET
        ) { it.programSlotCapabilities.joinToString("|") }

        system("runtime.safeForSeedMutation", ExerciseMetadataFieldScope.RUNTIME_METADATA)
        system("runtime.appCueProfile", ExerciseMetadataFieldScope.RUNTIME_METADATA)
        system("identity.currentHistoryOnlyRestriction", ExerciseMetadataFieldScope.EXERCISE)
        canonicalName("workout.exerciseName", ExerciseMetadataFieldScope.DENORMALIZED_REFERENCE)
        canonicalName("programItem.exerciseName", ExerciseMetadataFieldScope.DENORMALIZED_REFERENCE)
        derived("derived.strengthPosterior")
        derived("derived.fatigueAnalysis")
        derived("derived.performanceTrend")
        derived("derived.connectiveTissueState")
    }

    private val byKey = fields.associateBy(ExerciseMetadataFieldDefinition::fieldKey)

    init {
        require(byKey.size == fields.size) { "Duplicate exercise metadata field-policy key." }
        require(fields.filter { it.read != null }.all { it.write != null || it.fieldScope.name.endsWith("RELATION") }) {
            "Snapshot field is missing a restore handler."
        }
    }

    fun definition(fieldKey: String): ExerciseMetadataFieldDefinition? = byKey[fieldKey]

    fun policyCounts(): Map<ExerciseMetadataFieldPolicy, Int> =
        fields.groupingBy(ExerciseMetadataFieldDefinition::policy).eachCount()

    fun snapshot(source: ExerciseMetadataSnapshotSource): List<ExerciseMetadataSnapshotRow> =
        fields.mapNotNull { definition ->
            val read = definition.read ?: return@mapNotNull null
            val value = normalize(read(source), definition.valueEncoding)
            ExerciseMetadataSnapshotRow(
                stableKey = source.exercise.stableKey,
                fieldKey = definition.fieldKey,
                fieldScope = definition.fieldScope,
                valueEncoding = definition.valueEncoding,
                value = value,
                isExplicitEmpty = value.isEmpty()
            )
        }

    fun restore(
        exercise: Exercise,
        runtimeMetadata: RuntimeExerciseMetadata,
        rows: List<ExerciseMetadataSnapshotRow>
    ): ExerciseMetadataRestoreTarget {
        val target = ExerciseMetadataRestoreTarget(exercise, runtimeMetadata)
        rows.sortedBy(ExerciseMetadataSnapshotRow::fieldKey).forEach { row ->
            val definition = requireNotNull(byKey[row.fieldKey]) { "Unknown exercise metadata field: ${row.fieldKey}" }
            require(definition.fieldScope == row.fieldScope) { "Exercise metadata field scope mismatch: ${row.fieldKey}" }
            require(definition.valueEncoding == row.valueEncoding) { "Exercise metadata encoding mismatch: ${row.fieldKey}" }
            require(row.isExplicitEmpty == row.value.isEmpty()) { "Exercise metadata empty marker mismatch: ${row.fieldKey}" }
            definition.write?.invoke(target, normalize(row.value, row.valueEncoding))
        }
        return target
    }

    fun relationValues(
        rows: List<ExerciseMetadataSnapshotRow>,
        fieldKey: String
    ): Set<String>? {
        val row = rows.singleOrNull { it.fieldKey == fieldKey } ?: return null
        return normalize(row.value, ExerciseMetadataValueEncoding.TOKEN_SET)
            .split('|')
            .filter(String::isNotBlank)
            .toSet()
    }

    fun validate(rows: List<ExerciseMetadataSnapshotRow>) {
        val duplicate = rows.groupBy { it.stableKey to it.fieldKey }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) {
            throw DataTransferFormatException(
                DataTransferDiagnosticCodes.RESTORE_IDENTITY_CONTRADICTION,
                "Duplicate exercise metadata snapshot field: ${duplicate.key}"
            )
        }
        rows.forEach { row ->
            require(row.stableKey.isNotBlank()) { "Exercise metadata snapshot stableKey must be nonblank." }
            val definition = requireNotNull(byKey[row.fieldKey]) { "Unknown exercise metadata field: ${row.fieldKey}" }
            require(definition.fieldScope == row.fieldScope) { "Exercise metadata field scope mismatch: ${row.fieldKey}" }
            require(definition.valueEncoding == row.valueEncoding) { "Exercise metadata encoding mismatch: ${row.fieldKey}" }
            require(row.isExplicitEmpty == row.value.isEmpty()) { "Exercise metadata empty marker mismatch: ${row.fieldKey}" }
            normalize(row.value, row.valueEncoding)
        }
    }

    fun hasBackupOwnedFields(): Boolean = fields.any { it.policy == ExerciseMetadataFieldPolicy.BACKUP_SNAPSHOT_WINS }

    private fun MutableList<ExerciseMetadataFieldDefinition>.identity(
        key: String,
        scope: ExerciseMetadataFieldScope,
        encoding: ExerciseMetadataValueEncoding = ExerciseMetadataValueEncoding.STRING,
        read: (ExerciseMetadataSnapshotSource) -> String,
        write: ((ExerciseMetadataRestoreTarget, String) -> Unit)? = null
    ) = add(ExerciseMetadataFieldDefinition(key, scope, ExerciseMetadataFieldPolicy.IDENTITY_STABLE, encoding, read, write))

    private fun MutableList<ExerciseMetadataFieldDefinition>.canonicalName(
        key: String,
        scope: ExerciseMetadataFieldScope,
        read: ((ExerciseMetadataSnapshotSource) -> String)? = null,
        write: ((ExerciseMetadataRestoreTarget, String) -> Unit)? = null
    ) = add(ExerciseMetadataFieldDefinition(key, scope, ExerciseMetadataFieldPolicy.CURRENT_CANONICAL_NAME, ExerciseMetadataValueEncoding.STRING, read, write))

    private fun MutableList<ExerciseMetadataFieldDefinition>.exerciseString(
        key: String,
        read: (Exercise) -> String,
        write: (Exercise, String) -> Exercise
    ) = backup(key, ExerciseMetadataFieldScope.EXERCISE, ExerciseMetadataValueEncoding.STRING, { read(it.exercise) }) { target, value -> target.exercise = write(target.exercise, value) }

    private fun MutableList<ExerciseMetadataFieldDefinition>.exerciseTokens(
        key: String,
        read: (Exercise) -> String,
        write: (Exercise, String) -> Exercise
    ) = backup(key, ExerciseMetadataFieldScope.EXERCISE, ExerciseMetadataValueEncoding.TOKEN_SET, { read(it.exercise) }) { target, value -> target.exercise = write(target.exercise, value) }

    private fun MutableList<ExerciseMetadataFieldDefinition>.exerciseBoolean(
        key: String,
        read: (Exercise) -> Boolean,
        write: (Exercise, Boolean) -> Exercise
    ) = backup(key, ExerciseMetadataFieldScope.EXERCISE, ExerciseMetadataValueEncoding.BOOLEAN, { read(it.exercise).toString() }) { target, value -> target.exercise = write(target.exercise, value.requiredBoolean(key)) }

    private fun MutableList<ExerciseMetadataFieldDefinition>.exerciseInt(
        key: String,
        read: (Exercise) -> Int,
        write: (Exercise, Int) -> Exercise
    ) = backup(key, ExerciseMetadataFieldScope.EXERCISE, ExerciseMetadataValueEncoding.INTEGER, { read(it.exercise).toString() }) { target, value -> target.exercise = write(target.exercise, value.toIntOrNull() ?: error("Invalid integer metadata: $key")) }

    private fun MutableList<ExerciseMetadataFieldDefinition>.exerciseLong(
        key: String,
        read: (Exercise) -> Long?,
        write: (Exercise, Long?) -> Exercise
    ) = backup(key, ExerciseMetadataFieldScope.EXERCISE, ExerciseMetadataValueEncoding.LONG, { read(it.exercise)?.toString().orEmpty() }) { target, value ->
        val parsed = if (value.isEmpty()) null else value.toLongOrNull() ?: error("Invalid long metadata: $key")
        target.exercise = write(target.exercise, parsed)
    }

    private fun MutableList<ExerciseMetadataFieldDefinition>.exerciseDouble(
        key: String,
        read: (Exercise) -> Double,
        write: (Exercise, Double) -> Exercise
    ) = backup(key, ExerciseMetadataFieldScope.EXERCISE, ExerciseMetadataValueEncoding.DOUBLE, { read(it.exercise).toString() }) { target, value -> target.exercise = write(target.exercise, value.toDoubleOrNull()?.takeIf(Double::isFinite) ?: error("Invalid double metadata: $key")) }

    private fun MutableList<ExerciseMetadataFieldDefinition>.runtimeString(
        key: String,
        read: (RuntimeExerciseMetadata) -> String,
        write: (RuntimeExerciseMetadata, String) -> RuntimeExerciseMetadata
    ) = backup(key, ExerciseMetadataFieldScope.RUNTIME_METADATA, ExerciseMetadataValueEncoding.STRING, { read(it.runtimeMetadata) }) { target, value -> target.runtimeMetadata = write(target.runtimeMetadata, value) }

    private fun MutableList<ExerciseMetadataFieldDefinition>.runtimeTokens(
        key: String,
        read: (RuntimeExerciseMetadata) -> MetadataTokenField,
        write: (RuntimeExerciseMetadata, MetadataTokenField) -> RuntimeExerciseMetadata
    ) = backup(key, ExerciseMetadataFieldScope.RUNTIME_METADATA, ExerciseMetadataValueEncoding.TOKEN_SET, { read(it.runtimeMetadata).values.joinToString("|") }) { target, value -> target.runtimeMetadata = write(target.runtimeMetadata, MetadataTokenField.parse(value)) }

    private fun MutableList<ExerciseMetadataFieldDefinition>.backupRelation(
        key: String,
        scope: ExerciseMetadataFieldScope,
        encoding: ExerciseMetadataValueEncoding,
        read: (ExerciseMetadataSnapshotSource) -> String
    ) = backup(key, scope, encoding, read, null)

    private fun MutableList<ExerciseMetadataFieldDefinition>.backup(
        key: String,
        scope: ExerciseMetadataFieldScope,
        encoding: ExerciseMetadataValueEncoding,
        read: (ExerciseMetadataSnapshotSource) -> String,
        write: ((ExerciseMetadataRestoreTarget, String) -> Unit)?
    ) = add(ExerciseMetadataFieldDefinition(key, scope, ExerciseMetadataFieldPolicy.BACKUP_SNAPSHOT_WINS, encoding, read, write))

    private fun MutableList<ExerciseMetadataFieldDefinition>.system(key: String, scope: ExerciseMetadataFieldScope) =
        add(ExerciseMetadataFieldDefinition(key, scope, ExerciseMetadataFieldPolicy.CURRENT_CANONICAL_SYSTEM_VALUE, ExerciseMetadataValueEncoding.STRING))

    private fun MutableList<ExerciseMetadataFieldDefinition>.systemRuntimeString(
        key: String,
        read: (RuntimeExerciseMetadata) -> String,
        write: (RuntimeExerciseMetadata, String) -> RuntimeExerciseMetadata
    ) = add(
        ExerciseMetadataFieldDefinition(
            key,
            ExerciseMetadataFieldScope.RUNTIME_METADATA,
            ExerciseMetadataFieldPolicy.CURRENT_CANONICAL_SYSTEM_VALUE,
            ExerciseMetadataValueEncoding.STRING,
            { read(it.runtimeMetadata) },
            { target, value -> target.runtimeMetadata = write(target.runtimeMetadata, value) }
        )
    )

    private fun MutableList<ExerciseMetadataFieldDefinition>.derived(key: String) =
        add(ExerciseMetadataFieldDefinition(key, ExerciseMetadataFieldScope.DERIVED_ANALYSIS, ExerciseMetadataFieldPolicy.DERIVED_REBUILD, ExerciseMetadataValueEncoding.STRING))

    private fun normalize(value: String, encoding: ExerciseMetadataValueEncoding): String = when (encoding) {
        ExerciseMetadataValueEncoding.STRING -> value
        ExerciseMetadataValueEncoding.BOOLEAN -> value.requiredBoolean("snapshot").toString()
        ExerciseMetadataValueEncoding.INTEGER -> value.toIntOrNull()?.toString() ?: error("Invalid integer metadata value.")
        ExerciseMetadataValueEncoding.LONG -> value.takeIf(String::isNotEmpty)?.toLongOrNull()?.toString() ?: value.takeIf(String::isEmpty).orEmpty()
        ExerciseMetadataValueEncoding.DOUBLE -> value.toDoubleOrNull()?.takeIf(Double::isFinite)?.toString() ?: error("Invalid double metadata value.")
        ExerciseMetadataValueEncoding.TOKEN_SET -> value.split('|', ',', ';')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .joinToString("|")
    }
}

private fun String.requiredBoolean(field: String): Boolean = when (this) {
    "true" -> true
    "false" -> false
    else -> error("Invalid boolean metadata: $field")
}
