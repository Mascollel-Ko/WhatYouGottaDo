package com.training.trackplanner.analysis.strengthperformance

import android.content.Context
import com.training.trackplanner.data.Exercise

@JvmInline
value class StrengthPerformanceTargetKey(val value: String)

@JvmInline
value class StrengthFactorKey(val value: String)

enum class StrengthLoadSemantics {
    EXTERNAL_LOAD,
    BODYWEIGHT_PLUS_ADDED_LOAD,
    BODYWEIGHT_MINUS_ASSISTANCE,
    BODYWEIGHT_FRACTION_PLUS_ADDED_LOAD,
    MACHINE_STACK_LOAD,
    IMPLEMENT_TOTAL_LOAD
}

enum class DirectObservationPolicy {
    RPE10_ONLY
}

enum class StrengthProxyMode {
    DIRECT_ABSOLUTE,
    LOCAL_INNOVATION_SHARED_ONLY,
    DISABLED,
    CALIBRATED_ABSOLUTE_PROXY
}

data class StrengthPerformanceTargetSpec(
    val targetKey: StrengthPerformanceTargetKey,
    val displayNameKo: String,
    val anchorStableKeys: Set<String>,
    val closeVariationStableKeys: Set<String>,
    val loadSemantics: StrengthLoadSemantics,
    val curveSelectionPolicyKey: String,
    val sharedFactorLoadings: Map<StrengthFactorKey, Double>,
    val targetSpecificFactorKey: StrengthFactorKey,
    val supportedRepRange: IntRange,
    val directObservationPolicy: DirectObservationPolicy,
    val enabled: Boolean,
    val configVersion: String
)

data class StrengthProxyLoadingSpec(
    val exerciseStableKey: String,
    val targetKey: StrengthPerformanceTargetKey,
    val proxyMode: StrengthProxyMode,
    val transferCoefficient: Double,
    val transferLogVariance: Double,
    val factorLoadings: Map<StrengthFactorKey, Double>,
    val loadSemantics: StrengthLoadSemantics,
    val minimumLocalHistoryCount: Int,
    val configVersion: String,
    val rationale: String,
    val sourceClass: String,
    val reviewedStatus: String
) {
    val isDirectAnchor: Boolean get() = proxyMode == StrengthProxyMode.DIRECT_ABSOLUTE
    val loadingWeight: Double get() = transferCoefficient
    val relationship: String get() = proxyMode.name
}

class StrengthPerformanceRegistry private constructor(
    private val targetsByKey: Map<StrengthPerformanceTargetKey, StrengthPerformanceTargetSpec>,
    private val proxyRowsByExercise: Map<String, List<StrengthProxyLoadingSpec>>
) {
    fun targets(): List<StrengthPerformanceTargetSpec> = targetsByKey.values
        .filter(StrengthPerformanceTargetSpec::enabled)
        .sortedBy { target -> target.targetKey.value }

    fun target(key: StrengthPerformanceTargetKey): StrengthPerformanceTargetSpec? = targetsByKey[key]

    fun directTarget(exerciseStableKey: String): StrengthPerformanceTargetSpec? =
        targets().firstOrNull { target -> exerciseStableKey in target.anchorStableKeys }

    fun proxyLoadings(exerciseStableKey: String): List<StrengthProxyLoadingSpec> =
        proxyRowsByExercise[exerciseStableKey].orEmpty()

    /**
     * Reviewed stable-key rows remain authoritative. Metadata only broadens the
     * relevant-movement set when an eligible strength exercise has no row yet.
     */
    fun proxyLoadings(exercise: Exercise): List<StrengthProxyLoadingSpec> =
        proxyLoadings(exercise.stableKey).ifEmpty { metadataProxyLoadings(exercise) }

    fun loading(exerciseStableKey: String, targetKey: StrengthPerformanceTargetKey): StrengthProxyLoadingSpec? =
        proxyLoadings(exerciseStableKey).firstOrNull { loading -> loading.targetKey == targetKey }

    fun orderedFactorSchema(): List<StrengthFactorKey> = targets().flatMap { target ->
        target.sharedFactorLoadings.keys + target.targetSpecificFactorKey
    }.distinct().sortedBy(StrengthFactorKey::value)

    private fun metadataProxyLoadings(exercise: Exercise): List<StrengthProxyLoadingSpec> {
        if (!exercise.estimated1RmEligible || exercise.needsReview) return emptyList()
        val text = listOf(
            exercise.familyId,
            exercise.familyRole,
            exercise.movementPattern,
            exercise.movementCategory,
            exercise.strengthProgressionGroup,
            exercise.mainLiftGroup,
            exercise.analysisEligibility,
            exercise.equipment,
            exercise.equipmentTags
        ).joinToString("|").uppercase()
        val machine = "MACHINE" in text || "LEG_PRESS" in text || "HACK_SQUAT" in text
        fun row(
            targetKey: StrengthPerformanceTargetKey,
            weight: Double,
            semantics: StrengthLoadSemantics,
            vararg factors: Pair<String, Double>
        ) = StrengthProxyLoadingSpec(
            exerciseStableKey = exercise.stableKey,
            targetKey = targetKey,
            proxyMode = StrengthProxyMode.LOCAL_INNOVATION_SHARED_ONLY,
            transferCoefficient = weight,
            transferLogVariance = METADATA_PROXY_TRANSFER_VARIANCE,
            factorLoadings = factors.associate { (key, value) -> StrengthFactorKey(key) to value },
            loadSemantics = semantics,
            minimumLocalHistoryCount = 2,
            configVersion = METADATA_PROXY_CONFIG_VERSION,
            rationale = "Reviewed metadata-family local innovation fallback",
            sourceClass = "PRODUCT_POLICY",
            reviewedStatus = "REVIEWED"
        )
        return when {
            text.containsAny("SQUAT", "KNEE_DOMINANT", "LEG_PRESS", "LUNGE", "SPLIT_SQUAT", "STEP_UP") -> {
                val isLunge = text.containsAny("LUNGE", "SPLIT_SQUAT", "STEP_UP")
                listOf(row(
                    BACK_SQUAT,
                    if (isLunge) 0.28 else if (machine) 0.42 else 0.52,
                    if (machine) StrengthLoadSemantics.MACHINE_STACK_LOAD else StrengthLoadSemantics.EXTERNAL_LOAD,
                    "strength.factor.knee_extension" to if (isLunge) 0.80 else 0.92,
                    "strength.factor.hip_extension_posterior_chain" to if (isLunge) 0.48 else 0.62,
                    "strength.factor.trunk_bracing" to if (isLunge) 0.30 else 0.55
                ))
            }
            text.containsAny("DEADLIFT", "HINGE", "ROMANIAN", "_RDL", "HIP_THRUST", "GLUTE_BRIDGE") -> listOf(row(
                CONVENTIONAL_DEADLIFT,
                if (text.containsAny("HIP_THRUST", "GLUTE_BRIDGE")) 0.30 else 0.50,
                StrengthLoadSemantics.EXTERNAL_LOAD,
                "strength.factor.knee_extension" to 0.18,
                "strength.factor.hip_extension_posterior_chain" to 0.88,
                "strength.factor.trunk_bracing" to 0.62
            ))
            text.containsAny("BENCH", "HORIZONTAL_PUSH", "CHEST_PRESS", "DUMBBELL_PRESS", "DIP") -> listOf(row(
                BENCH_PRESS,
                if (machine) 0.36 else 0.46,
                if (machine) StrengthLoadSemantics.MACHINE_STACK_LOAD else StrengthLoadSemantics.EXTERNAL_LOAD,
                "strength.factor.press_shared" to 0.78,
                "strength.factor.horizontal_press" to 0.72,
                "strength.factor.elbow_extension" to 0.42
            ))
            text.containsAny("VERTICAL_PULL", "PULL_UP", "CHIN_UP", "LAT_PULLDOWN") -> listOf(row(
                WEIGHTED_PULL_UP,
                if (machine) 0.40 else 0.50,
                if (machine) StrengthLoadSemantics.MACHINE_STACK_LOAD else StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD,
                "strength.factor.vertical_pull_shared" to 0.82,
                "strength.factor.shoulder_adduction_extension" to 0.70,
                "strength.factor.elbow_flexion" to 0.55,
                "strength.factor.scapular_depression_control" to 0.48
            ))
            else -> emptyList()
        }
    }

    private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)

    companion object {
        const val TARGET_CONFIG_VERSION = "strength-target-registry-1.1.0"
        const val FACTOR_SCHEMA_VERSION = "strength-factor-schema-2.0.0"
        const val PROXY_CONFIG_VERSION = "strength-proxy-registry-2.0.0"
        const val METADATA_PROXY_CONFIG_VERSION = "strength-proxy-metadata-2.0.0"

        val BENCH_PRESS = StrengthPerformanceTargetKey("strength.bench_press")
        val BACK_SQUAT = StrengthPerformanceTargetKey("strength.back_squat")
        val CONVENTIONAL_DEADLIFT = StrengthPerformanceTargetKey("strength.conventional_deadlift")
        val WEIGHTED_PULL_UP = StrengthPerformanceTargetKey("strength.weighted_pull_up")

        fun fromContext(context: Context): StrengthPerformanceRegistry {
            fun text(name: String): String = context.assets.open("strength_performance/$name")
                .bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            return fromCsv(text(TARGET_FILE), text(PROXY_FILE))
        }

        fun fromCsv(targetCsv: String, proxyCsv: String): StrengthPerformanceRegistry {
            val targets = rows(targetCsv).map { row ->
                StrengthPerformanceTargetSpec(
                    targetKey = StrengthPerformanceTargetKey(row.required("targetKey")),
                    displayNameKo = row.required("displayNameKo"),
                    anchorStableKeys = row.tokens("anchorStableKeys").toSet(),
                    closeVariationStableKeys = row.tokens("closeVariationStableKeys").toSet(),
                    loadSemantics = enumValueOf(row.required("loadSemantics")),
                    curveSelectionPolicyKey = row.required("curveSelectionPolicyKey"),
                    sharedFactorLoadings = row.factorMap("sharedFactorLoadings"),
                    targetSpecificFactorKey = StrengthFactorKey(row.required("targetSpecificFactorKey")),
                    supportedRepRange = row.required("supportedRepMin").toInt()..row.required("supportedRepMax").toInt(),
                    directObservationPolicy = enumValueOf(row.required("directObservationPolicy")),
                    enabled = row.required("enabled").toBooleanStrict(),
                    configVersion = row.required("configVersion")
                ).also { target ->
                    require(target.anchorStableKeys.isNotEmpty())
                    require(target.sharedFactorLoadings.values.all { value -> value in 0.0..1.0 })
                }
            }.associateBy(StrengthPerformanceTargetSpec::targetKey)
            val proxyRows = rows(proxyCsv).map { row ->
                StrengthProxyLoadingSpec(
                    exerciseStableKey = row.required("exerciseStableKey"),
                    targetKey = StrengthPerformanceTargetKey(row.required("targetKey")),
                    proxyMode = enumValueOf(row.required("proxyMode")),
                    transferCoefficient = row.required("transferCoefficient").toDouble(),
                    transferLogVariance = row.required("transferLogVariance").toDouble(),
                    factorLoadings = row.factorMap("sharedFactorLoadings"),
                    loadSemantics = enumValueOf(row.required("loadSemantics")),
                    minimumLocalHistoryCount = row.required("minimumLocalHistoryCount").toInt(),
                    configVersion = row.required("configVersion"),
                    rationale = row.required("rationale"),
                    sourceClass = row.required("sourceClass"),
                    reviewedStatus = row.required("reviewedStatus")
                ).also { loading ->
                    require(loading.targetKey in targets)
                    require(loading.transferCoefficient in 0.0..1.0)
                    require(loading.transferLogVariance.isFinite() && loading.transferLogVariance > 0.0)
                    require(loading.minimumLocalHistoryCount >= if (loading.isDirectAnchor) 0 else 2)
                    require(loading.factorLoadings.values.all { value -> value in 0.0..1.0 })
                    require(
                        loading.isDirectAnchor ||
                            loading.factorLoadings.keys.none { key -> key.value.startsWith("strength.factor.target.") }
                    ) { "Non-direct proxy contains a target-specific factor." }
                    require(loading.proxyMode != StrengthProxyMode.CALIBRATED_ABSOLUTE_PROXY) {
                        "Calibrated absolute proxies are not enabled in this release."
                    }
                    require(loading.reviewedStatus == "REVIEWED")
                }
            }.groupBy(StrengthProxyLoadingSpec::exerciseStableKey)
            return StrengthPerformanceRegistry(targets, proxyRows)
        }

        private fun rows(csv: String): List<Map<String, String>> {
            val lines = csv.lineSequence().filter(String::isNotBlank).toList()
            require(lines.isNotEmpty())
            val header = lines.first().removePrefix("\uFEFF").split(',')
            return lines.drop(1).map { line ->
                val values = line.split(',')
                require(values.size == header.size) { "Malformed strength registry CSV row." }
                header.zip(values).toMap()
            }
        }

        private fun Map<String, String>.required(name: String): String =
            get(name).orEmpty().trim().also { value -> require(value.isNotEmpty()) { "Missing $name" } }

        private fun Map<String, String>.tokens(name: String): List<String> =
            get(name).orEmpty().split('|').map(String::trim).filter(String::isNotEmpty)

        private fun Map<String, String>.factorMap(name: String): Map<StrengthFactorKey, Double> =
            tokens(name).associate { token ->
                val parts = token.split(':', limit = 2)
                require(parts.size == 2)
                StrengthFactorKey(parts[0]) to parts[1].toDouble()
            }

        private const val TARGET_FILE = "strength_target_registry_v1.csv"
        private const val PROXY_FILE = "strength_proxy_loadings_v1.csv"
        private const val METADATA_PROXY_TRANSFER_VARIANCE = 0.12
    }
}
