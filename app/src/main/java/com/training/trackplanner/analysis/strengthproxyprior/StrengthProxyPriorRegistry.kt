package com.training.trackplanner.analysis.strengthproxyprior

enum class StrengthTargetKey {
    BENCH_PRESS,
    BACK_SQUAT,
    DEADLIFT,
    WEIGHTED_PULL_UP,
    MILITARY_PRESS
}

enum class StrengthProxyLoadSemantics {
    EXTERNAL_LOAD,
    IMPLEMENT_TOTAL_LOAD,
    MACHINE_STACK_LOAD,
    BODYWEIGHT_PLUS_ADDED_LOAD
}

enum class StrengthTargetSourceStatus {
    LEGACY_EXPLICIT_TARGET,
    PRODUCT_OWNER_SEMANTIC_DECISION
}

enum class StrengthTargetReviewStatus {
    REVIEWED_DIRECT_ANCHOR
}

enum class StrengthProxyEvidenceClass {
    DIRECT_ANCHOR_PRODUCT_POLICY,
    PROVISIONAL_PRODUCT_PRIOR
}

enum class StrengthProxyApprovalStatus {
    REVIEWED_DIRECT_ANCHOR,
    TEMPORARY_APPROVED
}

data class StrengthTargetRef(
    val targetKey: StrengthTargetKey,
    val displayNameKo: String,
    val displayNameEn: String,
    val anchorExerciseStableKey: String,
    val canonicalExecutionSemantics: String,
    val loadSemantics: StrengthProxyLoadSemantics,
    val enabled: Boolean,
    val configVersion: String,
    val sourceStatus: StrengthTargetSourceStatus,
    val reviewStatus: StrengthTargetReviewStatus,
    val provenanceId: String
)

data class StrengthProxyRelation(
    val exerciseStableKey: String,
    val targetKey: StrengthTargetKey,
    val priorSpecificityMean: Double,
    val priorSpecificityConcentration: Double,
    val priorTransferSlopeMean: Double,
    val priorTransferSlopeSd: Double,
    val priorResidualLogSdMean: Double,
    val priorResidualLogSdSd: Double,
    val sharedFactorLoadings: Map<String, Double>,
    val targetSpecificLoading: Double,
    val loadSemantics: StrengthProxyLoadSemantics,
    val minimumProxyObservations: Int,
    val minimumTargetObservations: Int,
    val personalizationAllowed: Boolean,
    val evidenceClass: StrengthProxyEvidenceClass,
    val approvalStatus: StrengthProxyApprovalStatus,
    val requiresPostMetadataResearchReview: Boolean,
    val configVersion: String,
    val rationale: String,
    val provenanceId: String
)

class StrengthProxyPriorRegistry private constructor(
    val targets: List<StrengthTargetRef>,
    val relations: List<StrengthProxyRelation>
) {
    val targetsByKey: Map<StrengthTargetKey, StrengthTargetRef> = targets.associateBy(StrengthTargetRef::targetKey)
    val relationsByExercise: Map<String, List<StrengthProxyRelation>> = relations.groupBy(StrengthProxyRelation::exerciseStableKey)

    companion object {
        fun fromRows(
            targetRows: List<Map<String, String>>,
            relationRows: List<Map<String, String>>
        ): StrengthProxyPriorRegistry {
            val targets = targetRows.map { row ->
                StrengthTargetRef(
                    targetKey = row.enum("targetKey"),
                    displayNameKo = row.required("displayNameKo"),
                    displayNameEn = row.required("displayNameEn"),
                    anchorExerciseStableKey = row.required("anchorExerciseStableKey"),
                    canonicalExecutionSemantics = row.required("canonicalExecutionSemantics"),
                    loadSemantics = row.enum("loadSemantics"),
                    enabled = row.boolean("enabled"),
                    configVersion = row.required("configVersion"),
                    sourceStatus = row.enum("sourceStatus"),
                    reviewStatus = row.enum("reviewStatus"),
                    provenanceId = row.required("provenanceId")
                )
            }
            val relations = relationRows.map { row ->
                StrengthProxyRelation(
                    exerciseStableKey = row.required("exerciseStableKey"),
                    targetKey = row.enum("targetKey"),
                    priorSpecificityMean = row.double("priorSpecificityMean"),
                    priorSpecificityConcentration = row.double("priorSpecificityConcentration"),
                    priorTransferSlopeMean = row.double("priorTransferSlopeMean"),
                    priorTransferSlopeSd = row.double("priorTransferSlopeSd"),
                    priorResidualLogSdMean = row.double("priorResidualLogSdMean"),
                    priorResidualLogSdSd = row.double("priorResidualLogSdSd"),
                    sharedFactorLoadings = row.required("sharedFactorLoadings").split('|').associate { token ->
                        val (factor, loading) = token.split(':', limit = 2)
                        factor to loading.toDouble()
                    },
                    targetSpecificLoading = row.double("targetSpecificLoading"),
                    loadSemantics = row.enum("loadSemantics"),
                    minimumProxyObservations = row.int("minimumProxyObservations"),
                    minimumTargetObservations = row.int("minimumTargetObservations"),
                    personalizationAllowed = row.boolean("personalizationAllowed"),
                    evidenceClass = row.enum("evidenceClass"),
                    approvalStatus = row.enum("approvalStatus"),
                    requiresPostMetadataResearchReview = row.boolean("requiresPostMetadataResearchReview"),
                    configVersion = row.required("configVersion"),
                    rationale = row.required("rationale"),
                    provenanceId = row.required("provenanceId")
                )
            }
            return StrengthProxyPriorRegistry(targets, relations).also { it.validate() }
        }
    }

    private fun validate() {
        require(targets.map(StrengthTargetRef::targetKey).toSet() == StrengthTargetKey.entries.toSet())
        require(targets.size == StrengthTargetKey.entries.size)
        require(targets.all(StrengthTargetRef::enabled))
        require(relations.map { it.exerciseStableKey to it.targetKey }.distinct().size == relations.size)
        require(relations.map(StrengthProxyRelation::targetKey).toSet() == targetsByKey.keys)
        relations.forEach { relation ->
            require(relation.exerciseStableKey.isNotBlank())
            require(relation.priorSpecificityMean in 0.0..1.0)
            require(relation.priorSpecificityConcentration > 0.0)
            require(relation.priorTransferSlopeMean.isFinite())
            require(relation.priorTransferSlopeSd > 0.0)
            require(relation.priorResidualLogSdMean.isFinite())
            require(relation.priorResidualLogSdSd > 0.0)
            require(relation.sharedFactorLoadings.isNotEmpty())
            require(relation.sharedFactorLoadings.values.all { it in 0.0..1.0 })
            require(relation.targetSpecificLoading in 0.0..1.0)
            require(relation.minimumProxyObservations >= 0)
            require(relation.minimumTargetObservations >= 0)

            val direct = relation.exerciseStableKey == targetsByKey.getValue(relation.targetKey).anchorExerciseStableKey
            if (direct) {
                require(relation.evidenceClass == StrengthProxyEvidenceClass.DIRECT_ANCHOR_PRODUCT_POLICY)
                require(relation.approvalStatus == StrengthProxyApprovalStatus.REVIEWED_DIRECT_ANCHOR)
                require(!relation.requiresPostMetadataResearchReview)
            } else {
                require(relation.evidenceClass == StrengthProxyEvidenceClass.PROVISIONAL_PRODUCT_PRIOR)
                require(relation.approvalStatus == StrengthProxyApprovalStatus.TEMPORARY_APPROVED)
                require(relation.requiresPostMetadataResearchReview)
                require(relation.priorSpecificityConcentration <= 4.0)
                require(relation.priorTransferSlopeSd >= 0.5)
                require(relation.priorResidualLogSdSd >= 0.5)
            }
        }
    }
}

private fun Map<String, String>.required(key: String): String =
    get(key)?.trim().orEmpty().also { require(it.isNotBlank()) { "Missing strength proxy field: $key" } }

private inline fun <reified T : Enum<T>> Map<String, String>.enum(key: String): T =
    enumValueOf(required(key))

private fun Map<String, String>.double(key: String): Double = required(key).toDouble()
private fun Map<String, String>.int(key: String): Int = required(key).toInt()
private fun Map<String, String>.boolean(key: String): Boolean = required(key).toBooleanStrict()
