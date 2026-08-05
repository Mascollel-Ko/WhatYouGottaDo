package com.training.trackplanner.data

import android.content.Context
import com.training.trackplanner.analysis.tissue.TissueRcvAssetRepository
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

data class CanonicalExerciseIdentity(
    val stableKey: String,
    val exerciseName: String,
    val identityStatus: String,
    val sourceStableKey: String,
    val selectable: Boolean,
    val historyTreatment: String,
    val movementPattern: String,
    val stabilityDemandLevel: String,
    val mobilityDemandLevel: String,
    val equipmentRequirementModel: String,
    val equipmentCodes: String,
    val mappingConfidence: String,
    val identityDecisionStatus: String,
    val reviewStatus: String
) {
    val historyOnly: Boolean
        get() = identityStatus == "HISTORY_ONLY_GENERIC"
}

data class ExerciseProgramTimingProfile(
    val stableKey: String,
    val defaultRestSeconds: Int,
    val provenanceId: String,
    val derivationMode: String,
    val reviewStatus: String,
    val sourceStableKey: String,
    val sourceReference: String
)

data class CanonicalEquipmentRequirement(
    val exerciseStableKey: String,
    val groupId: String,
    val requirementModel: String,
    val memberOrder: Int,
    val equipmentCode: String,
    val simultaneousRequired: Boolean,
    val sourceStableKey: String
)

enum class CanonicalRelationDomain {
    MOVEMENT,
    MUSCLE,
    OFI,
    BADMINTON,
    PROGRESSION
}

data class CanonicalMetadataRelation(
    val domain: CanonicalRelationDomain,
    val relationKey: String,
    val exerciseStableKey: String,
    val relationType: String,
    val relationValue: String,
    val coefficient: Double?,
    val sourceStableKey: String,
    val status: String,
    val provenance: String
)

data class CanonicalRecoveryProfile(
    val exerciseStableKey: String,
    val recoveryDecayProfile: String,
    val recoveryDurationClass: String,
    val stressMagnitudeHint: String,
    val sourceStableKey: String,
    val provenance: String
)

data class CanonicalStrengthProxyRelation(
    val relationId: String,
    val targetKey: String,
    val targetAnchorStableKey: String,
    val exerciseStableKey: String,
    val relationRole: String,
    val observationPath: String,
    val relationStatus: String,
    val provenance: String
)

class CanonicalExerciseMetadataRepository(private val context: Context) {
    private val manifest = loadManifest()
    private val identitiesByStableKey: Map<String, CanonicalExerciseIdentity> =
        parseVerifiedCsv("identity_master.csv")
            .map(::identityFrom)
            .also { rows -> require(rows.size == EXPECTED_IDENTITY_ROWS) }
            .associateByUnique(CanonicalExerciseIdentity::stableKey)
    private val timingByStableKey: Map<String, ExerciseProgramTimingProfile> =
        parseVerifiedCsv("program_timing.csv")
            .map(::timingFrom)
            .also { rows -> require(rows.size == EXPECTED_SELECTABLE_ROWS) }
            .associateByUnique(ExerciseProgramTimingProfile::stableKey)
    private val runtimeMetadata: List<RuntimeExerciseMetadata> =
        ExerciseMetadataAdapter.fromCsv(verifiedAssetText("runtime_metadata.csv"))
            .also { rows ->
                require(rows.size == EXPECTED_IDENTITY_ROWS)
                require(rows.distinctBy { it.stableKey.lowercase(Locale.ROOT) }.size == rows.size)
            }
    private val bootstrapByStableKey: Map<String, Exercise> =
        parseVerifiedCsv("exercise_bootstrap.csv")
            .map(::exerciseFrom)
            .also { rows -> require(rows.size == EXPECTED_IDENTITY_ROWS) }
            .associateByUnique(Exercise::stableKey)
    private val equipmentByStableKey: Map<String, List<CanonicalEquipmentRequirement>> =
        parseVerifiedCsv("equipment_relations.csv")
            .map { fields ->
                CanonicalEquipmentRequirement(
                    exerciseStableKey = fields.required("exerciseStableKey").normalizedCanonicalKey(),
                    groupId = fields["groupId"].orEmpty(),
                    requirementModel = fields.required("requirementModel"),
                    memberOrder = fields["memberOrder"].orEmpty().toIntOrNull() ?: 0,
                    equipmentCode = fields["equipmentCode"].orEmpty(),
                    simultaneousRequired = fields.required("simultaneousRequired").toYesNoBoolean(),
                    sourceStableKey = fields.required("sourceStableKey").normalizedCanonicalKey()
                )
            }
            .groupBy(CanonicalEquipmentRequirement::exerciseStableKey)

    init {
        val selectableKeys = selectableIdentities().mapTo(mutableSetOf(), CanonicalExerciseIdentity::stableKey)
        require(timingByStableKey.keys == selectableKeys) {
            "Canonical timing profiles must exactly cover selectable identities."
        }
        require(identitiesByStableKey.values.count(CanonicalExerciseIdentity::historyOnly) == EXPECTED_HISTORY_ROWS)
        require(identitiesByStableKey.values.none { it.mappingConfidence in IDENTITY_DECISION_TOKENS })
        require(runtimeMetadata.all { metadata ->
            val identity = identitiesByStableKey.getValue(metadata.stableKey)
            if (identity.historyOnly) metadata.planningEligibility == "HISTORY_ONLY"
            else metadata.planningEligibility != "HISTORY_ONLY"
        })
        require(equipmentByStableKey.keys == selectableKeys) {
            "Canonical equipment requirements must exactly cover selectable identities."
        }
        require(bootstrapByStableKey.keys == identitiesByStableKey.keys) {
            "Canonical bootstrap rows must exactly cover all identities."
        }
        require(identitiesByStableKey.values.filter(CanonicalExerciseIdentity::historyOnly).all { identity ->
            bootstrapByStableKey.getValue(identity.stableKey).let { exercise ->
                !exercise.isActive && exercise.planningEligibility == "HISTORY_ONLY"
            }
        }) { "History-only bootstrap rows must remain inactive and non-selectable." }
        require(selectableKeys.all { key ->
            bootstrapByStableKey.getValue(key).defaultRestSeconds == timingByStableKey.getValue(key).defaultRestSeconds
        }) { "Canonical bootstrap timing differs from program timing authority." }
    }

    fun identities(): List<CanonicalExerciseIdentity> = identitiesByStableKey.values.sortedBy { it.stableKey }

    fun selectableIdentities(): List<CanonicalExerciseIdentity> =
        identitiesByStableKey.values.filter(CanonicalExerciseIdentity::selectable).sortedBy { it.stableKey }

    fun identity(stableKey: String): CanonicalExerciseIdentity? =
        identitiesByStableKey[stableKey.normalizedCanonicalKey()]

    fun timing(stableKey: String): ExerciseProgramTimingProfile? =
        timingByStableKey[stableKey.normalizedCanonicalKey()]

    fun equipmentRequirements(stableKey: String): List<CanonicalEquipmentRequirement> =
        equipmentByStableKey[stableKey.normalizedCanonicalKey()].orEmpty().sortedBy { it.memberOrder }

    fun equipmentCodes(stableKey: String): String =
        equipmentRequirements(stableKey)
            .map(CanonicalEquipmentRequirement::equipmentCode)
            .filter(String::isNotBlank)
            .joinToString("|")

    fun movementRelations(): List<CanonicalMetadataRelation> = canonicalRelations(
        assetName = "movement_relations.csv",
        domain = CanonicalRelationDomain.MOVEMENT,
        keyField = "relationId",
        typeField = "relationType",
        valueField = "relationValue"
    )

    fun muscleRelations(): List<CanonicalMetadataRelation> = canonicalRelations(
        assetName = "muscle_relations.csv",
        domain = CanonicalRelationDomain.MUSCLE,
        keyField = "relationKey",
        typeField = "muscleRelationType",
        valueField = "muscleCode",
        coefficientField = "coefficient"
    )

    fun ofiRelations(): List<CanonicalMetadataRelation> = canonicalRelations(
        assetName = "ofi_relations.csv",
        domain = CanonicalRelationDomain.OFI,
        keyField = "relationKey",
        typeField = "relationType",
        valueField = "relationId",
        coefficientField = "coefficient"
    )

    fun badmintonRelations(): List<CanonicalMetadataRelation> = canonicalRelations(
        assetName = "badminton_relations.csv",
        domain = CanonicalRelationDomain.BADMINTON,
        keyField = "relationKey",
        typeField = "relationType",
        valueField = "relationValue"
    )

    fun progressionRelations(): List<CanonicalMetadataRelation> = canonicalRelations(
        assetName = "progression_relations.csv",
        domain = CanonicalRelationDomain.PROGRESSION,
        keyField = "relationKey",
        typeField = "progressMetricType",
        valueField = "progressionGroup"
    )

    fun recoveryProfiles(): List<CanonicalRecoveryProfile> =
        parseVerifiedCsv("recovery_relations.csv").map { fields ->
            CanonicalRecoveryProfile(
                exerciseStableKey = fields.requiredSelectableStableKey(),
                recoveryDecayProfile = fields.required("recoveryDecayProfile"),
                recoveryDurationClass = fields.required("recoveryDurationClass"),
                stressMagnitudeHint = fields.required("stressMagnitudeHint"),
                sourceStableKey = fields.required("sourceStableKey").normalizedCanonicalKey(),
                provenance = fields.required("provenance")
            )
        }

    fun strengthProxyRelations(): List<CanonicalStrengthProxyRelation> =
        parseVerifiedCsv("strength_proxy_relations.csv").map { fields ->
            CanonicalStrengthProxyRelation(
                relationId = fields.required("relationId"),
                targetKey = fields.required("targetKey"),
                targetAnchorStableKey = fields.required("targetAnchorStableKey").also(::requireSelectableKey),
                exerciseStableKey = fields.required("exerciseStableKey").also(::requireSelectableKey),
                relationRole = fields.required("relationRole"),
                observationPath = fields.required("observationPath"),
                relationStatus = fields.required("relationStatus"),
                provenance = fields.required("provenance")
            )
        }

    fun tissueRepository(): TissueRcvAssetRepository = TissueRcvAssetRepository.fromAssets(context)

    fun runtimeMetadataCatalog(): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataCatalog.of(runtimeMetadata)

    fun exercises(includeHistory: Boolean = false): List<Exercise> =
        identitiesByStableKey.values
            .asSequence()
            .filter { identity -> includeHistory || identity.selectable }
            .map { identity -> bootstrapByStableKey.getValue(identity.stableKey) }
            .sortedBy(Exercise::stableKey)
            .toList()

    fun trainingRoleRelations(): List<ExerciseTrainingRoleRelation> =
        parseVerifiedCsv("training_roles.csv").map { fields ->
            require(fields.getValue("relationScope") == "PRODUCTION_ACTIVE")
            ExerciseTrainingRoleRelation(
                exerciseStableKey = fields.getValue("exerciseStableKey"),
                trainingRoleCode = TrainingRole.valueOf(fields.getValue("trainingRoleCode")).name,
                provenance = fields.getValue("provenance"),
                reviewStatus = fields.getValue("reviewStatus"),
                notes = fields.getValue("notes")
            )
        }.also(::requireSelectableRelations)

    fun programSlotCapabilityRelations(): List<ExerciseProgramSlotCapabilityRelation> =
        parseVerifiedCsv("program_slot_capabilities.csv").map { fields ->
            require(fields.getValue("relationScope") == "PRODUCTION_ACTIVE")
            ExerciseProgramSlotCapabilityRelation(
                exerciseStableKey = fields.getValue("exerciseStableKey"),
                capabilityCode = ProgramSlotCapability.valueOf(fields.getValue("capabilityCode")).name,
                provenance = fields.getValue("provenance"),
                reviewStatus = fields.getValue("reviewStatus"),
                notes = fields.getValue("notes")
            )
        }.also(::requireSelectableRelations)

    internal fun verifiedRows(assetName: String): List<Map<String, String>> =
        parseVerifiedCsv(assetName)

    private fun identityFrom(fields: Map<String, String>): CanonicalExerciseIdentity =
        CanonicalExerciseIdentity(
            stableKey = fields.required("stableKey").normalizedCanonicalKey(),
            exerciseName = fields.required("exerciseName"),
            identityStatus = fields.required("identityStatus"),
            sourceStableKey = fields.required("sourceStableKey").normalizedCanonicalKey(),
            selectable = fields.required("selectable").toYesNoBoolean(),
            historyTreatment = fields.required("historyTreatment"),
            movementPattern = fields["movementPattern"].orEmpty(),
            stabilityDemandLevel = fields["stabilityDemandLevel"].orEmpty(),
            mobilityDemandLevel = fields["mobilityDemandLevel"].orEmpty(),
            equipmentRequirementModel = fields.required("equipmentRequirementModel"),
            equipmentCodes = fields["equipmentCodes"].orEmpty(),
            mappingConfidence = fields["mappingConfidence"].orEmpty(),
            identityDecisionStatus = fields["identityDecisionStatus"].orEmpty(),
            reviewStatus = fields.required("reviewStatus")
        )

    private fun timingFrom(fields: Map<String, String>): ExerciseProgramTimingProfile {
        val rest = fields.required("defaultRestSeconds").toInt()
        require(rest in 0..3600)
        return ExerciseProgramTimingProfile(
            stableKey = fields.required("stableKey").normalizedCanonicalKey(),
            defaultRestSeconds = rest,
            provenanceId = fields.required("provenanceId"),
            derivationMode = fields.required("derivationMode"),
            reviewStatus = fields.required("reviewStatus"),
            sourceStableKey = fields.required("sourceStableKey").normalizedCanonicalKey(),
            sourceReference = fields.required("sourceReference")
        )
    }

    private fun exerciseFrom(fields: Map<String, String>): Exercise = Exercise(
        stableKey = fields.required("stableKey").normalizedCanonicalKey(),
        name = fields.required("name"),
        category = fields.required("category"),
        detail1 = fields["detail1"].orEmpty(),
        detail2 = fields["detail2"].orEmpty(),
        mode = fields["mode"].orEmpty(),
        description = fields["description"].orEmpty(),
        defaultRestSeconds = fields.required("defaultRestSeconds").toInt(),
        familyId = fields["familyId"].orEmpty(),
        familyName = fields["familyName"].orEmpty(),
        familyRole = fields["familyRole"].orEmpty(),
        familyE1rmMultiplier = fields.required("familyE1rmMultiplier").toDouble(),
        movementPattern = fields["movementPattern"].orEmpty(),
        movementCategory = fields["movementCategory"].orEmpty(),
        primaryMuscles = fields["primaryMuscles"].orEmpty(),
        secondaryMuscles = fields["secondaryMuscles"].orEmpty(),
        equipment = fields["equipment"].orEmpty(),
        equipmentTags = fields["equipmentTags"].orEmpty(),
        compoundType = fields["compoundType"].orEmpty(),
        forceType = fields["forceType"].orEmpty(),
        bodyRegion = fields["bodyRegion"].orEmpty(),
        plane = fields["plane"].orEmpty(),
        laterality = fields["laterality"].orEmpty(),
        axialLoadLevel = fields["axialLoadLevel"].orEmpty(),
        stabilityRoles = fields["stabilityRoles"].orEmpty(),
        sportTransferDirect = fields["sportTransferDirect"].orEmpty(),
        sportTransferSupportive = fields["sportTransferSupportive"].orEmpty(),
        badmintonTransferRoles = fields["badmintonTransferRoles"].orEmpty(),
        fatigueCategories = fields["fatigueCategories"].orEmpty(),
        adaptiveBaselineGroups = fields["adaptiveBaselineGroups"].orEmpty(),
        accessoryRoles = fields["accessoryRoles"].orEmpty(),
        loadProfile = fields["loadProfile"].orEmpty(),
        recoveryDecayProfile = fields["recoveryDecayProfile"].orEmpty(),
        systemicLoadWeight = fields.required("systemicLoadWeight").toDouble(),
        neuralHeavyWeight = fields.required("neuralHeavyWeight").toDouble(),
        neuralSpeedWeight = fields.required("neuralSpeedWeight").toDouble(),
        localLoadWeight = fields.required("localLoadWeight").toDouble(),
        decelerationWeight = fields.required("decelerationWeight").toDouble(),
        elasticSscWeight = fields.required("elasticSscWeight").toDouble(),
        rotationPowerWeight = fields.required("rotationPowerWeight").toDouble(),
        antiRotationWeight = fields.required("antiRotationWeight").toDouble(),
        overheadSwingWeight = fields.required("overheadSwingWeight").toDouble(),
        gripLoadWeight = fields.required("gripLoadWeight").toDouble(),
        progressMetricType = fields["progressMetricType"].orEmpty(),
        strengthProgressionGroup = fields["strengthProgressionGroup"].orEmpty(),
        hypertrophyVolumeGroup = fields["hypertrophyVolumeGroup"].orEmpty(),
        mainLiftGroup = fields["mainLiftGroup"].orEmpty(),
        accessoryContributionGroup = fields["accessoryContributionGroup"].orEmpty(),
        estimated1RmEligible = fields.required("estimated1RmEligible").toYesNoBoolean(),
        volumeLoadEligible = fields.required("volumeLoadEligible").toYesNoBoolean(),
        badmintonTransferStrength = fields["badmintonTransferStrength"].orEmpty(),
        courtMovementTypes = fields["courtMovementTypes"].orEmpty(),
        badmintonSkillTargets = fields["badmintonSkillTargets"].orEmpty(),
        jointStressTags = fields["jointStressTags"].orEmpty(),
        stabilityDemandLevel = fields["stabilityDemandLevel"].orEmpty(),
        mobilityDemandLevel = fields["mobilityDemandLevel"].orEmpty(),
        balanceContributionTags = fields["balanceContributionTags"].orEmpty(),
        analysisEligibility = fields["analysisEligibility"].orEmpty(),
        activityKind = fields["activityKind"].orEmpty(),
        planningEligibility = fields["planningEligibility"].orEmpty(),
        metadataConfidence = fields["metadataConfidence"].orEmpty(),
        imageAssetName = fields["imageAssetName"].orEmpty(),
        isActive = fields.required("isActive").toYesNoBoolean(),
        archivedAt = fields["archivedAt"].orEmpty().toLongOrNull(),
        isCustom = fields.required("isCustom").toYesNoBoolean(),
        needsReview = fields.required("needsReview").toYesNoBoolean()
    )

    private fun requireSelectableRelations(rows: Collection<Any>) {
        val keys = when (rows.firstOrNull()) {
            is ExerciseTrainingRoleRelation -> rows.map { (it as ExerciseTrainingRoleRelation).exerciseStableKey }
            is ExerciseProgramSlotCapabilityRelation -> rows.map { (it as ExerciseProgramSlotCapabilityRelation).exerciseStableKey }
            null -> emptyList()
            else -> error("Unsupported canonical relation type")
        }
        require(keys.all { key -> identitiesByStableKey[key]?.selectable == true }) {
            "Production relation references a non-selectable or missing identity."
        }
    }

    private fun canonicalRelations(
        assetName: String,
        domain: CanonicalRelationDomain,
        keyField: String,
        typeField: String,
        valueField: String,
        coefficientField: String? = null
    ): List<CanonicalMetadataRelation> = parseVerifiedCsv(assetName).map { fields ->
        CanonicalMetadataRelation(
            domain = domain,
            relationKey = fields.required(keyField),
            exerciseStableKey = fields.requiredSelectableStableKey(),
            relationType = fields.required(typeField),
            relationValue = fields.required(valueField),
            coefficient = coefficientField?.let { field -> fields[field].orEmpty().toDoubleOrNull() },
            sourceStableKey = fields["sourceStableKey"].orEmpty().normalizedCanonicalKey(),
            status = fields["status"].orEmpty().ifBlank { fields["sourceStatus"].orEmpty() },
            provenance = fields.required("provenance")
        )
    }

    private fun Map<String, String>.requiredSelectableStableKey(): String =
        required("exerciseStableKey").normalizedCanonicalKey().also(::requireSelectableKey)

    private fun requireSelectableKey(stableKey: String) {
        require(identitiesByStableKey[stableKey.normalizedCanonicalKey()]?.selectable == true) {
            "Production relation references non-selectable identity: $stableKey"
        }
    }

    private fun loadManifest(): Map<String, ManifestEntry> {
        val root = JSONObject(assetBytes(MANIFEST_FILE).toString(Charsets.UTF_8))
        require(root.getInt("schemaVersion") == SCHEMA_VERSION)
        require(root.getString("researchScope") == "NOT_EXPORTED_TO_RUNTIME_ASSETS")
        require(root.getString("relationshipAdjudication") == "NOT_ADJUDICATED")
        val files = root.getJSONArray("files")
        return buildMap {
            for (index in 0 until files.length()) {
                val row = files.getJSONObject(index)
                val entry = ManifestEntry(
                    path = row.getString("path"),
                    rowCount = row.getInt("rowCount"),
                    sha256 = row.getString("sha256"),
                    scope = row.getString("scope")
                )
                require(put(entry.path, entry) == null) { "Duplicate canonical manifest path: ${entry.path}" }
            }
        }
    }

    private fun parseVerifiedCsv(assetName: String): List<Map<String, String>> {
        val rows = verifiedAssetText(assetName).lineSequence()
            .filter(String::isNotBlank)
            .map(SeedData::parseCsvLine)
            .toList()
        require(rows.isNotEmpty()) { "Empty canonical asset: $assetName" }
        val header = rows.first().map { it.trim().trimStart('\uFEFF') }
        val parsed = rows.drop(1).map { values ->
            require(values.size == header.size) { "Malformed canonical row in $assetName" }
            header.mapIndexed { index, name -> name to values[index].trim() }.toMap()
        }
        require(parsed.size == manifest.getValue(assetName).rowCount) {
            "Canonical row-count mismatch for $assetName"
        }
        return parsed
    }

    private fun verifiedAssetText(assetName: String): String {
        val bytes = assetBytes(assetName)
        require(bytes.sha256() == manifest.getValue(assetName).sha256) {
            "Canonical asset hash mismatch: $assetName"
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun assetBytes(assetName: String): ByteArray =
        context.assets.open("$ASSET_DIRECTORY/$assetName").use { it.readBytes() }

    private data class ManifestEntry(
        val path: String,
        val rowCount: Int,
        val sha256: String,
        val scope: String
    )

    companion object {
        const val ASSET_DIRECTORY = "metadata/canonical_v1"
        const val MANIFEST_FILE = "manifest.json"
        const val EXPECTED_IDENTITY_ROWS = 257
        const val EXPECTED_SELECTABLE_ROWS = 241
        const val EXPECTED_HISTORY_ROWS = 16
        private const val SCHEMA_VERSION = 1
        private val IDENTITY_DECISION_TOKENS = setOf("KEEP_CANONICAL", "PROPOSED_USER_APPROVED")
    }
}

object CanonicalExerciseMetadataRepositoryProvider {
    @Volatile
    private var cached: CanonicalExerciseMetadataRepository? = null

    fun get(context: Context): CanonicalExerciseMetadataRepository =
        cached ?: synchronized(this) {
            cached ?: CanonicalExerciseMetadataRepository(context.applicationContext).also { cached = it }
        }

    internal fun clearForTest() {
        cached = null
    }
}

private fun <T> Iterable<T>.associateByUnique(selector: (T) -> String): Map<String, T> {
    val rows = associateBy { selector(it).normalizedCanonicalKey() }
    require(rows.size == count()) { "Canonical asset contains duplicate stable keys." }
    return rows
}

private fun Map<String, String>.required(field: String): String =
    get(field).orEmpty().also { require(it.isNotBlank()) { "Missing canonical field: $field" } }

private fun String.toYesNoBoolean(): Boolean = when (this) {
    "YES" -> true
    "NO" -> false
    else -> error("Expected YES or NO, found '$this'")
}

private fun String.normalizedCanonicalKey(): String = trim().lowercase(Locale.ROOT)

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
